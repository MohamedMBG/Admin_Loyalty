# AdminLoyalty

AdminLoyalty is an Android app for managing a loyalty program from the business side.
It helps teams track customer activity, manage rewards/promotions, and support cashier redemption flows in one place.

## Why this project

Many loyalty tools focus on customers first.  
This project focuses on **operations**: giving admins and cashiers a clear workflow for day-to-day execution.

## Current scope (high-level)

- Admin dashboard for monitoring activity trends
- Customer/member management views
- Rewards and promotions management
- Cashier-side redemption flow
- Logs/history screens for operational visibility

Note: detailed business logic is intentionally not documented publicly.

## Tech stack

- Android (Java, ViewBinding, Fragments)
- Firebase (Auth, Firestore, Functions, Analytics)
- MPAndroidChart
- ZXing (QR/Barcode utilities)
- OkHttp

## Project structure

- `app/src/main/java/com/example/adminloyalty/authetification` - login/auth screens
- `app/src/main/java/com/example/adminloyalty/fragments` - admin feature screens
- `app/src/main/java/com/example/adminloyalty/cashier` - cashier/redeeming flows
- `app/src/main/java/com/example/adminloyalty/data` - repositories/data access
- `app/src/main/java/com/example/adminloyalty/models` - app models
- `app/src/main/java/com/example/adminloyalty/utils` - utility classes

## Run locally

1. Open the project in Android Studio.
2. Add your Firebase config (`google-services.json`) to the `app/` module.
3. Sync Gradle dependencies.
4. Build and run on an emulator/device (min SDK 24).

## Status

Active development.  
Public docs are intentionally brief to protect product strategy while still enabling collaboration and setup.

