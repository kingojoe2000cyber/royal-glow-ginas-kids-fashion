-- Promote only the specified, verified Auth email to the first administrator.
-- Auth signup/email verification creates the auth.users record; this trigger then assigns
-- the exact matching account the admin role. No client API can set arbitrary roles.

create or replace function private.bootstrap_primary_admin()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if lower(coalesce(new.email, '')) = 'kingojoe2000cyber@gmail.com' then
    insert into public.staff_members(user_id, full_name, phone, role, is_active)
    values (
      new.id,
      coalesce(nullif(new.raw_user_meta_data ->> 'full_name', ''), 'Primary Administrator'),
      new.phone,
      'admin',
      true
    )
    on conflict (user_id) do update
      set role = 'admin',
          is_active = true,
          updated_at = now();
  end if;
  return new;
end;
$$;

revoke all on function private.bootstrap_primary_admin() from public, anon, authenticated;

drop trigger if exists on_auth_user_bootstrap_primary_admin on auth.users;
create trigger on_auth_user_bootstrap_primary_admin
after insert or update of email on auth.users
for each row execute function private.bootstrap_primary_admin();
