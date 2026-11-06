# Royal Glow & Gina's Kids Fashion — Android

Native Android application starter built with Kotlin and Jetpack Compose for a shared online shop and POS operation.

## Brands and currency
- **Royal Glow:** kids' thrift clothing, girly goods, beauty products and accessories.
- **Gina's Kids Fashion:** children's clothing and wholesale/retail products for ages 0–12.
- Currency: Ghana cedis (GHS).

## Android application
The Android source code is in `app/`. The current starter includes:
- Combined storefront, brand filter and product search
- Sample product catalogue and GHS price display
- Cart quantity controls
- Checkout UI requiring customer name, contact phone and full delivery address
- Order confirmation placeholder
- POS counter-sale starter screen
- Basic inventory and admin dashboard screens
- GitHub Actions debug APK build workflow

**Current Android limitation:** The Android app is still a UI starter and is not yet wired to the live Supabase database. Its product catalogue and checkout confirmation still use sample/in-memory data. Do not use it to take real orders or payments until the Android integration and end-to-end tests are completed.

## Supabase backend — provisioned
A new Supabase project has been created and its initial database schema deployed.

| Setting | Value |
|---|---|
| Project | `royal-glow-ginas-kids-fashion` |
| Project reference | `kgkcgfwabglhmsqfplry` |
| Region | EU West (Ireland), `eu-west-1` |
| Project URL | `https://kgkcgfwabglhmsqfplry.supabase.co` |
| Currency | GHS |
| Storage bucket | `product-images` |

The project currently contains these business areas:
- Brands and categories (Royal Glow and Gina's Kids Fashion are seeded)
- Customer profiles and customer delivery addresses
- Staff members and roles (admin, manager, cashier, inventory, fulfilment)
- Products, private product cost/wholesale pricing and inventory
- Stock movement audit trail
- Suppliers, purchase orders and purchase order items
- Customer orders and order items
- Payments and delivery tracking
- POS sales and POS sale items
- Expenses, audit logs and app settings

Row Level Security is enabled on all 21 public tables. Public access is read-only for active brands, categories and products. Customer-owned records are scoped to the signed-in customer. Staff permissions are role-based. Provider payment payloads are stored in a non-exposed `private` schema. The security advisor reports one intentional warning: the authenticated-only `place_customer_order` RPC uses `SECURITY DEFINER` so it can reserve inventory and create an order atomically. Its function body validates `auth.uid()`, prices from the database, checks stock under row locks, has a fixed empty search path, and has EXECUTE revoked from `public` and `anon`. Re-review this decision during production security testing.

### Secure checkout
The database function `public.place_customer_order(...)` is available to authenticated customers. It:
- Requires customer name, valid contact phone, delivery address and city
- Reads current product prices from the database rather than trusting phone-supplied prices
- Locks inventory rows and reserves stock within the same database transaction
- Saves order lines, delivery record and pending payment record
- Writes an audit entry
- Returns the order number and GHS total

The app must sign the customer in and call this function to complete a live order. Guest checkout is not enabled yet.

### Supabase client key
The Android app must use the project's **publishable** key only. Never put a Supabase secret/service-role key in the Android app, GitHub source, or APK. The publishable key is intended for client use and depends on the RLS policies for data protection. Retrieve or rotate it in Supabase Project Settings → API Keys.

## Remaining production work
1. Integrate the Supabase Kotlin SDK into Android; replace sample catalogue and in-memory checkout/POS with live data.
2. Implement customer sign-up/login, profile and saved delivery addresses.
3. Connect the checkout UI to `place_customer_order` and add customer order history/tracking.
4. Invite the first administrator account from Supabase Dashboard → Authentication → Users → Invite user, then confirm the email. The Auth trigger promotes only `kingojoe2000cyber@gmail.com` to admin.
5. Implement POS posting, purchase receiving and inventory updates as atomic server-side operations.
6. Connect a Ghana-supported payment provider (Hubtel or Paystack) through server-side payment initiation and webhook verification. Do not treat a client callback as proof of payment.
7. Complete delivery pricing/zones, notifications, refund flows, backups and production hardening.
8. Build and test the APK on physical Android devices, then configure release signing.

## Build
Open in Android Studio with JDK 17 and Android SDK 35. The repository workflow builds a debug APK on pushes and pull requests to `main`.

```bash
gradle assembleDebug
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Database migration history
The live Supabase project has recorded the following migrations:
- `20261106213753_initial_shop_commerce_schema`
- `20261106213840_tighten_rls_private_pricing_and_staff_access`
- `20261106213851_finalise_order_policy_and_private_payment_rls`
- `20261106213944_secure_customer_checkout_and_profile_creation`
- `20261106214610_bootstrap_primary_administrator_email`

The production schema is already applied remotely. The migration SQL files and Android client configuration are being brought under source control as part of the backend integration work.

## First administrator
The database has an Auth trigger configured to assign the `admin` role only to the account whose Auth email is exactly `kingojoe2000cyber@gmail.com`. As of the latest check, that email has not yet registered in Supabase Auth (zero matching Auth users), so no staff/admin record has been created yet. To activate it, invite this email in Supabase Dashboard → Authentication → Users → Invite user and complete the email verification/sign-in flow. Do not share passwords or secret API keys in GitHub.
