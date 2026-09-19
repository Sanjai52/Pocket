# Pocket

A personal expense manager Android app built with Jetpack Compose, Room, and Material 3.

## Features

- Add, edit, and delete expenses/income
- 6 built-in categories (Food, Entertainment, Bills, Groceries, Health, Other) + custom categories
- Calendar view with daily transaction breakdown
- Insights with donut chart, category breakdown, and monthly trend bar chart
- Month-wise filtering across Home, Calendar, and Insights
- Settings with theme, currency, backup/restore, and CSV export
- Splash screen with wallet illustration
- First-launch welcome screen for name entry

## Tech Stack

- **UI**: Jetpack Compose + Material 3
- **Database**: Room with KSP
- **Architecture**: MVVM with StateFlow
- **Navigation**: Compose Navigation
- **Prefs**: DataStore Preferences
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35

## Build

```bash
./gradlew assembleRelease
```

Release APK: `app/build/outputs/apk/release/app-release.apk`

## License

Personal use only.
