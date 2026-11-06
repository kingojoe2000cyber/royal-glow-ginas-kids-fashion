# Royal Glow & Gina's Kids Fashion — Android

Native Android starter app using Kotlin and Jetpack Compose.

## Brands
- Royal Glow: kids thrift clothing, girly goods, beauty products and accessories.
- Gina's Kids Fashion: children's clothing and wholesale/retail products, ages 0–12.
- Currency: Ghana cedis (GHS).

## Project structure
The Android source code is in `app/`. Open this repository in Android Studio with JDK 17 and Android SDK 35.

## Current MVP features
- Combined storefront with brand filters and product search
- Sample catalogue in Ghana cedis
- Shopping cart and quantity controls
- Checkout requires customer name, contact phone number and full delivery address
- Order confirmation placeholder
- Counter-sale POS interface
- Basic inventory dashboard
- GitHub Actions workflow to build a debug APK

## Demo-only limitations
This first starter uses sample products in memory. It does not yet persist or synchronize orders, sales, inventory or customer accounts online. Checkout currently records a demo order state only. Do not use this version to collect real payments or process live orders.

## Production setup still required
1. Configure Supabase (database, authentication, Row Level Security and image storage).
2. Connect products, inventory, customer accounts, orders and POS transactions to Supabase.
3. Integrate a Ghana-supported payment provider such as Hubtel or Paystack through a secure server-side payment verification flow. Never embed secret API keys in the Android APK.
4. Add delivery fees/zones, order notifications, refunds and administration access controls.
5. Test on physical Android phones and configure release signing.

## Build
Open in Android Studio (Gradle 8.9, JDK 17, Android Gradle Plugin 8.7.3, SDK 35), or run:

```bash
gradle assembleDebug
```

Debug APK output:
`app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions builds the debug APK on pushes and pull requests to `main`.
