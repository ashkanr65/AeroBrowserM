@echo off
rem Starts the Aero test device (Pixel 7, Android 15) in a normal emulator window.
set "ANDROID_HOME=C:\Users\rezae\.aero-android\android-sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
"%ANDROID_HOME%\emulator\emulator.exe" -avd Aero_Pixel7_API35 -no-boot-anim
