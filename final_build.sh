#!/bin/bash
# Complete build script for AgentStudio

# Setup environment
export ANDROID_HOME=$HOME/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Source SDKMAN
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Navigate to project
cd /home/z/my-project/AgentStudio

echo "========================================" | tee /tmp/build_status.txt
echo "Building AgentStudio APK" | tee -a /tmp/build_status.txt
echo "Date: $(date)" | tee -a /tmp/build_status.txt
echo "========================================" | tee -a /tmp/build_status.txt

# Clean and build
echo "Running gradle assembleRelease..." | tee -a /tmp/build_status.txt
gradle clean assembleRelease --no-daemon 2>&1 | tee -a /tmp/build_status.txt

# Check result
echo "" | tee -a /tmp/build_status.txt
echo "========================================" | tee -a /tmp/build_status.txt
if [ -f app/build/outputs/apk/release/app-release.apk ]; then
    echo "BUILD SUCCESSFUL!" | tee -a /tmp/build_status.txt
    echo "APK location: app/build/outputs/apk/release/app-release.apk" | tee -a /tmp/build_status.txt
    ls -la app/build/outputs/apk/release/ | tee -a /tmp/build_status.txt
    cp app/build/outputs/apk/release/app-release.apk /home/z/my-project/download/AgentStudio.apk
    echo "APK copied to: /home/z/my-project/download/AgentStudio.apk" | tee -a /tmp/build_status.txt
else
    echo "BUILD FAILED - APK not found" | tee -a /tmp/build_status.txt
    find app/build -name "*.apk" 2>/dev/null | tee -a /tmp/build_status.txt
fi
echo "========================================" | tee -a /tmp/build_status.txt
