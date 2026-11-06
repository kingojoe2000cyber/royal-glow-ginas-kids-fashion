-- Auth profile bootstrap trigger and authenticated, atomic customer checkout.
create or replace function private.handle_new_auth_user()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  insert into public.profiles(id, full_name, email, phone)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'full_name', ''), new.email, new.phone)
  on conflict (id) do update set email = excluded.email, phone = excluded.phone, updated_at = now();
  return new;
end;
$$;
revoke all on function private.handle_new_auth_user() from public, anon, authenticated;
drop trigger if exists on_auth_user_created_profile on auth.users;
create trigger on_auth_user_created_profile after insert on auth.users
for each row execute function private.handle_new_auth_user();

create or replace function public.place_customer_order(
  p_brand_id uuid,
  p_customer_name text,
  p_contact_phone text,
  p_delivery_address text,
  p_delivery_city text,
  p_items jsonb,
  p_payment_method text,
  p_delivery_area text default null,
  p_delivery_region text default null,
  p_digital_address text default null,
  p_delivery_instructions text default null,
  p_customer_notes text default null
)
returns jsonb language plpgsql security definer set search_path = ''
as $$
declare
  v_customer_id uuid := auth.uid();
  v_order_id uuid;
  v_order_number text;
  v_item jsonb;
  v_product_id uuid;
  v_quantity integer;
  v_product_name text;
  v_sku text;
  v_unit_price numeric(12,2);
  v_on_hand integer;
  v_reserved integer;
  v_line_total numeric(12,2);
  v_subtotal numeric(12,2) := 0;
  v_total numeric(12,2);
  v_phone_digits text;
begin
  if v_customer_id is null then
    raise exception 'Sign-in is required to place an order' using errcode = '28000';
  end if;
  if p_brand_id is null then raise exception 'Please select a shop brand' using errcode = '22023'; end if;
  if p_customer_name is null or length(trim(p_customer_name)) = 0 then raise exception 'Customer name is required' using errcode = '22023'; end if;
  v_phone_digits := regexp_replace(coalesce(p_contact_phone, ''), '[^0-9]', '', 'g');
  if length(v_phone_digits) < 9 or length(v_phone_digits) > 15 then raise exception 'A valid contact phone number is required' using errcode = '22023'; end if;
  if p_delivery_address is null or length(trim(p_delivery_address)) < 5 then raise exception 'A full delivery address is required' using errcode = '22023'; end if;
  if p_delivery_city is null or length(trim(p_delivery_city)) = 0 then raise exception 'Delivery town or city is required' using errcode = '22023'; end if;
  if p_payment_method is null or p_payment_method not in ('mtn_momo','telecel_cash','at_money','card','cash','bank_transfer','cash_on_delivery','other') then raise exception 'Unsupported payment method' using errcode = '22023'; end if;
  if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 or jsonb_array_length(p_items) > 50 then raise exception 'Your basket must contain between 1 and 50 product lines' using errcode = '22023'; end if;
  if not exists (select 1 from public.brands b where b.id = p_brand_id and b.is_active) then raise exception 'The selected shop is unavailable' using errcode = '22023'; end if;

  insert into public.orders(
    brand_id, customer_id, customer_name, contact_phone, delivery_address,
    delivery_area, delivery_city, delivery_region, digital_address,
    delivery_instructions, customer_notes, order_source, order_status,
    payment_status, subtotal, delivery_fee, total_amount
  )
  values (
    p_brand_id, v_customer_id, trim(p_customer_name), v_phone_digits,
    trim(p_delivery_address), nullif(trim(coalesce(p_delivery_area,'')), ''),
    trim(p_delivery_city), nullif(trim(coalesce(p_delivery_region,'')), ''),
    nullif(trim(coalesce(p_digital_address,'')), ''),
    nullif(trim(coalesce(p_delivery_instructions,'')), ''),
    nullif(trim(coalesce(p_customer_notes,'')), ''), 'online', 'pending',
    case when p_payment_method = 'cash_on_delivery' then 'unpaid' else 'pending' end, 0, 0, 0
  )
  returning id, order_number into v_order_id, v_order_number;

  -- Merge duplicate product IDs so stock cannot be oversubscribed by repeating a line.
  for v_item in
    select jsonb_build_object('product_id', item_data.product_id, 'quantity', item_data.total_quantity)
    from (
      select (entry.value ->> 'product_id')::uuid as product_id,
             sum((entry.value ->> 'quantity')::integer)::integer as total_quantity
      from jsonb_array_elements(p_items) as entry(value)
      group by (entry.value ->> 'product_id')::uuid
    ) as item_data
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_quantity := (v_item ->> 'quantity')::integer;
    if v_quantity is null or v_quantity <= 0 then raise exception 'Product quantities must be positive whole numbers' using errcode = '22023'; end if;

    select p.name, p.sku, p.price, i.quantity_on_hand, i.quantity_reserved
      into v_product_name, v_sku, v_unit_price, v_on_hand, v_reserved
    from public.products p join public.inventory i on i.product_id = p.id
    where p.id = v_product_id and p.brand_id = p_brand_id and p.is_active
    for update of i;

    if not found then raise exception 'A product is no longer available in this shop' using errcode = '22023'; end if;
    if (v_on_hand - v_reserved) < v_quantity then raise exception 'Insufficient stock for product: %', v_product_name using errcode = '22023'; end if;

    v_line_total := round(v_unit_price * v_quantity, 2);
    v_subtotal := v_subtotal + v_line_total;
    insert into public.order_items(order_id, product_id, product_name, sku, quantity, unit_price, line_total)
    values (v_order_id, v_product_id, v_product_name, v_sku, v_quantity, v_unit_price, v_line_total);
    update public.inventory set quantity_reserved = quantity_reserved + v_quantity where product_id = v_product_id;
    insert into public.stock_movements(product_id, movement_type, quantity_change, reference_type, reference_id, notes, recorded_by)
    values (v_product_id, 'reservation', -v_quantity, 'order', v_order_id, 'Customer checkout stock reservation', v_customer_id);
  end loop;

  v_total := v_subtotal;
  update public.orders set subtotal = v_subtotal, total_amount = v_total where id = v_order_id;
  insert into public.deliveries(order_id, delivery_fee, delivery_status) values (v_order_id, 0, 'pending');
  insert into public.payments(order_id, amount, currency, method, status) values (v_order_id, v_total, 'GHS', p_payment_method, 'pending');
  insert into public.audit_logs(actor_id, action, table_name, record_id, details)
  values (v_customer_id, 'customer_order_placed', 'orders', v_order_id::text,
    jsonb_build_object('order_number', v_order_number, 'amount', v_total, 'currency', 'GHS'));

  return jsonb_build_object(
    'order_id', v_order_id, 'order_number', v_order_number,
    'subtotal', v_subtotal, 'delivery_fee', 0, 'total_amount', v_total,
    'currency', 'GHS', 'order_status', 'pending',
    'payment_status', case when p_payment_method = 'cash_on_delivery' then 'unpaid' else 'pending' end
  );
end;
$$;

revoke all on function public.place_customer_order(uuid,text,text,text,text,jsonb,text,text,text,text,text,text) from public, anon;
grant execute on function public.place_customer_order(uuid,text,text,text,text,jsonb,text,text,text,text,text,text) to authenticated;
