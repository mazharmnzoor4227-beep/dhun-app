# Dhun

A music player for Android. Developed by Mazhar Ansari.

## Build

First run `scripts/restore-fonts.sh` (two large fonts are stored in pieces under `app/fontparts/`).

JDK 21 and the Android SDK (platform 36, NDK 27.0.12077973, CMake 3.22.1) are needed.

```
export JAVA_HOME=/path/to/jdk21 ANDROID_HOME=/path/to/sdk
# put your own keystore at keystore/dhun.jks and export STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
./gradlew :app:assembleArm64FossRelease
```

The APK lands in `app/build/outputs/apk/arm64Foss/release/`.

Package id: `com.mazharansari.dhun`.

## Licence

GPL-3.0, see `LICENSE`. Based on open-source projects; their licence and copyright notices are kept in the source files.
