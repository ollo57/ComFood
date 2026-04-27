# ComFood

ComFood is an Android food logging app with a Wear OS companion.

The phone app supports:
- voice meal capture
- barcode scanning
- ingredient avoidance filters
- daily and weekly macro logging
- on-device local storage and export
- candidate meal matching from:
  - local restaurant and generic food catalogs
  - Open Food Facts
  - USDA FoodData Central

The watch app supports:
- quick voice capture from Wear OS
- sending meal transcripts to the phone
- creating a pending approval card on the phone log screen

## Project structure

- `:app` - Android phone app
- `:wear` - Wear OS companion app

## Requirements

- Android Studio
- Android SDK installed
- JDK 11
- Internet access for Open Food Facts and USDA lookups

For the watch flow:
- an Android phone
- a paired Wear OS watch
- phone app and watch app built from the same project signing key

## Local setup

Open the project in Android Studio and let Gradle sync.

Create or update `local.properties` in the project root with:

```properties
sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
usda.apiKey=YOUR_USDA_FOODDATA_CENTRAL_KEY
```

Notes:
- This project expects `local.properties` to be present in the repo for hackathon handoff convenience.
- The USDA key is optional for the app to build, but USDA results will be missing if it is blank.

## Build the APKs

### Phone APK

In Android Studio:
1. Select the `app` run configuration for the phone
2. Use `Build > Build Bundle(s) / APK(s) > Build APK(s)`

Or from the terminal:

```powershell
.\gradlew.bat :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Watch APK

In Android Studio:
1. Select the `wear` run configuration for the watch
2. Use `Build > Build Bundle(s) / APK(s) > Build APK(s)`

Or from the terminal:

```powershell
.\gradlew.bat :wear:assembleDebug
```

Output:

```text
wear/build/outputs/apk/debug/wear-debug.apk
```

## Sending the APK to a friend

If your friend only needs the phone demo:
- send `app/build/outputs/apk/debug/app-debug.apk`

If your friend needs phone + watch:
- send both:
  - `app-debug.apk`
  - `wear-debug.apk`

Install steps for your friend:
1. Enable app installs from unknown sources on the phone if needed
2. Install `app-debug.apk` on the phone
3. If using a watch, install `wear-debug.apk` on the paired watch

Important:
- For the watch handoff to work, the phone app and watch app must come from the same signed build set.
- If you build both APKs on your machine and send those exact APKs, that condition is satisfied.

## Running from Android Studio

### Phone

Create or select a run configuration with:
- Module: `ComFood.app`

Run it on the phone.

### Watch

Create or select a run configuration with:
- Module: `wear` or `ComFood.wear`

Run it on the watch.

If Android Studio only shows `ComFood.app`, run:
1. `File > Sync Project with Gradle Files`
2. reopen `Edit Configurations`

## Demo flow

### Phone voice flow

1. Open the phone app
2. Go to `Voice`
3. Tap the mic
4. Speak a meal
5. Review candidate matches
6. Tap `Log this match`

### Watch flow

1. Open the watch app
2. Tap the round voice button
3. Speak the meal
4. Watch sends the transcript to the phone
5. Open the phone app
6. Go to `Log`
7. Open the `Pending approval` card
8. Approve the closest candidate

## How watch handoff works

The watch sends meal data to the phone in two ways:
- immediate `MessageClient` delivery to connected nodes
- `DataItem` backup sync for recovery if the phone misses the live event

The phone:
- listens with a `WearableListenerService`
- imports live messages
- imports synced data items
- resyncs from Wear when the app comes to the foreground

This makes the watch flow much more reliable than relying on only one transport path.

## Troubleshooting

### The watch app launches as a white screen

Make sure you are launching the `wear` module, not the `app` module, on the watch.

### The watch sends speech but nothing appears on the phone

Check:
1. phone app is installed
2. watch app is installed
3. watch and phone are paired
4. both APKs came from the same build/signing set
5. open the phone app and go to `Log`
6. close and reopen the phone app once so foreground resync runs

### Android Studio warns that the app is not a watch app

You are probably trying to run the phone module on the watch.
Use the `wear` run configuration instead.

### USDA results are missing

Make sure `usda.apiKey` is present in `local.properties`.

### Open Food Facts results are inconsistent

Open Food Facts can rate-limit anonymous traffic or return partial data sometimes.
ComFood uses:
- local food catalogs
- Open Food Facts
- USDA FoodData Central

So the app should still offer candidates even when one source is thin.

## Test commands

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :wear:assembleDebug
```

## GitHub notes

Safe to commit:
- source code
- Gradle files
- assets
- README

Before pushing:
1. make sure `local.properties` contains the SDK path and any demo key values you want shared
2. sync Gradle
3. rebuild the app

## Data sources

- Open Food Facts: https://world.openfoodfacts.org/data
- USDA FoodData Central: https://fdc.nal.usda.gov/
