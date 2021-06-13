#!/usr/bin/env bash
set -e

# 5.0.0 rc1
CMDLINE_TOOLS_VERSION=7006259

if [[ "$OSTYPE" == "linux-gnu"* ]]; then
  CMDLINE_TOOLS_OS="linux"
elif [[ "$OSTYPE" == "darwin"* ]]; then
  CMDLINE_TOOLS_OS="mac"
else
  echo "Unsupported OS $OSTYPE"
  exit 1
fi

echo "Setting up Android SDK"
mkdir -p $ANDROID_HOME/cmdline-tools
mkdir -p $ANDROID_HOME/licenses

echo "Installing cmdline-tools"
wget -q -O /tmp/android-sdk.zip https://dl.google.com/android/repository/commandlinetools-${CMDLINE_TOOLS_OS}-${CMDLINE_TOOLS_VERSION}_latest.zip
unzip -qo /tmp/android-sdk.zip -d $ANDROID_HOME/cmdline-tools
mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest

echo "Adding licenses"
echo -e "24333f8a63b6825ea9c5514f83c2829b004d1fee\n" > "$ANDROID_HOME/licenses/android-sdk-license"
echo -e "84831b9409646a918e30573bab4c9c91346d8abd\n" > "$ANDROID_HOME/licenses/android-sdk-preview-license"

echo "Done!"