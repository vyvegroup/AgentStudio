#!/bin/bash

# Setup environment
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Source SDKMAN for Gradle
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Navigate to project
cd /home/z/my-project/AgentStudio

echo "=== Environment Info ===" > /tmp/build_output.log
echo "ANDROID_HOME: $ANDROID_HOME" >> /tmp/build_output.log
echo "Java: $(java -version 2>&1 | head -1)" >> /tmp/build_output.log
echo "Gradle: $(gradle --version 2>&1 | head -3)" >> /tmp/build_output.log
echo "" >> /tmp/build_output.log

# Run build
echo "=== Starting Build ===" >> /tmp/build_output.log
gradle assembleRelease --stacktrace --no-daemon 2>&1 >> /tmp/build_output.log

echo "" >> /tmp/build_output.log
echo "=== Build Complete ===" >> /tmp/build_output.log
echo "Exit code: $?" >> /tmp/build_output.log

# Check for APK
if [ -f app/build/outputs/apk/release/app-release.apk ]; then
    echo "APK found at: app/build/outputs/apk/release/app-release.apk" >> /tmp/build_output.log
    ls -la app/build/outputs/apk/release/ >> /tmp/build_output.log
else
    echo "APK not found!" >> /tmp/build_output.log
    find app/build -name "*.apk" 2>/dev/null >> /tmp/build_output.log
fi
