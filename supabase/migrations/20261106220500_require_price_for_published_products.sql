-- Draft products may have a zero price, but a product cannot be published without a positive selling price.
alter table public.products
  add constraint products_published_price_positive
  check (not is_active or price > 0);
