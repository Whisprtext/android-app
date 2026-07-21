# Implementation Plan - Fix App Crash on Startup

The app is crashing immediately on opening due to a `NullPointerException` in `PreferencesManager`.

## User Review Required

> [!IMPORTANT]
> This fix involves reordering code in `PreferencesManager` to ensure properties are initialized before being accessed in the `init` block. This is a common Kotlin pitfall where properties declared after an `init` block are `null` if accessed within that block.

## Proposed Changes

### [Core Data Layer]

#### [MODIFY] [PreferencesManager.kt](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/java/com/whisprtext/app/data/local/PreferencesManager.kt)
- Move Flow property declarations (`appearanceSettings`, `userId`, etc.) above the `init` block.
- This ensures that when `managerScope.launch` executes (especially with `Dispatchers.Main.immediate`), the flows are already initialized and not `null`.

## Verification Plan

### Automated Tests
- I will attempt to build the project to ensure no syntax errors were introduced.
- Since this is a runtime NPE during initialization, I will also verify if I can run a simple unit test for `PreferencesManager` if available.

### Manual Verification
- Deploy the app to the device and verify it no longer crashes on opening.
- Check Logcat for any other potential issues.
