# Neath FX

An Android home-screen currency widget. It shows **THB → CAD** by default, and you can switch to any currency.

## What it does
- **Widget:** shows the currency pair, the live rate, and a reference table (for THB: 100 / 500 / 1,000 / 5,000 baht in CAD). The amounts adjust to whatever currency you pick. It has a ⇄ swap button and a ↻ refresh button.
- **Tap the widget:** opens a calculator popup. Type in either box and the other converts as you type. Tap a currency button to search any of ~160 currencies.
- **Rates:** come from ExchangeRate-API's free endpoint (`open.er-api.com`, no API key needed). They refresh automatically when older than 3 hours. All rates are saved against USD, so any pair still works offline from the last download.
- **Themes:** Graphite (charcoal and gold), Midnight (navy and mint) and Light. In *Follow phone* mode, the widget switches to Light when your phone's dark mode is off. Change it with the 🎨 button in the calculator.
- **Flag buttons:** each currency button is the country's flag with the code printed on it, in both the widget and the calculator.
- **Clear button:** ⊗ beside each amount empties both fields.
- No third-party libraries. It uses only the Android framework.

## Get the APK (no Android Studio needed)
Every push to `main` builds the app on GitHub Actions. Open the repo's **Releases** page on your phone, download `NeathFX.apk` from the latest build and open it to install. You may need to allow installs from your browser. New builds install over the old one.

## Build & install locally
1. Open this folder in Android Studio (Ladybug or newer). Let Gradle sync. If Studio offers to upgrade AGP, that's fine.
2. Plug in your phone with USB debugging on, then press **Run**.
3. Long-press your home screen → **Widgets** → **Neath FX** → drag *Currency converter* onto the screen. It's resizable; 4×3 is a good size.

Open the app once while you're online, before you travel, so the rates get saved for offline use.

Requirements: Android 8.0+ (minSdk 26). Targets SDK 35.

## Editing widget themes
Widget layouts are generated, one per theme. Edit `tools/widget_template.xml` or the palettes in `tools/gen_widget_themes.py`, then run `python3 tools/gen_widget_themes.py`.

## Credits
Flag images: [flag-icons](https://github.com/lipis/flag-icons) by Panayiotis Lipiridis (MIT). Rates: [ExchangeRate-API](https://www.exchangerate-api.com).

## Files
- `Fx.kt` — rate download and cache, settings, conversion, number formatting
- `FxWidgetProvider.kt` — widget drawing, swap/refresh buttons, background refresh
- `CalculatorActivity.kt` — popup calculator and currency picker
