# Fix 16 KB Page Size Compatibility Error

The app is showing a compatibility warning because some native libraries (`libcurve25519.so`, `libdatastore_shared_counter.so`, `libandroidx.graphics.path.so`) are not aligned to 16 KB page boundaries. This is a requirement for Android 15+ devices running with a 16 KB page size kernel.

## User Review Required

> [!IMPORTANT]
> To fully fix this for all libraries, the third-party dependencies (like Signal Protocol) would need to be recompiled with 16 KB alignment by their maintainers. Since some of these are legacy libraries, we will use the official Android workaround to suppress the warning and allow the app to run in compatibility mode.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/gradle/libs.versions.toml)
- Update `androidx.datastore` to `1.2.1` (latest stable version which is likely 16 KB aligned).
- Check and update other `androidx` dependencies if possible.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/build.gradle.kts)
- Add `packaging { jniLibs { useLegacyPackaging = true } }` to ensure libraries are compressed in the APK, which allows them to be extracted and run in compatibility mode on 16 KB devices.

### Android Manifest

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/nilan/Projects/whisprtext-app/whisprtext/app/src/main/AndroidManifest.xml)
- Add `android:pageSizeCompat="enabled"` to the `<application>` tag. This attribute tells the system that the app is aware of 16 KB page sizes and explicitly requests compatibility mode, which suppresses the user-facing warning dialog.

## Verification Plan

### Manual Verification
- Rebuild the app and deploy it to the 16 KB emulator.
- Verify that the "Android App Compatibility" warning dialog no longer appears on launch.
- Check the app's functionality (especially DataStore and Signal Protocol features) to ensure no regressions in compatibility mode.
