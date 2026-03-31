#!/bin/bash
set -e

export ANDROID_HOME=~/android-sdk
source "$HOME/.sdkman/bin/sdkman-init.sh"

cd /home/z/my-project/AgentStudio

echo "Starting build at $(date)" > /tmp/build_status.log
echo "ANDROID_HOME: $ANDROID_HOME" >> /tmp/build_status.log
echo "Java version: $(java -version 2>&1)" >> /tmp/build_status.log
echo "Gradle version: $(gradle --version 2>&1 | head -3)" >> /tmp/build_status.log

echo "Running gradle clean assembleRelease..." >> /tmp/build_status.log
gradle clean assembleRelease --no-daemon --info >> /tmp/gradle_full.log 2>&1

echo "Build completed at $(date)" >> /tmp/build_status.log
echo "Exit code: $?" >> /tmp/build_status.log

ls -la app/build/outputs/apk/release/ >> /tmp/build_status.log 2>&1 || echo "No APK output" >> /tmp/build_status.log
