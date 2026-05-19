#!/usr/bin/env bash
set -euxo pipefail

PLATFORM_VERSION="36"
BLD_TOOL_VERSION="36.0.0"
NDK_VERSION="29.0.14206865"
CMAKE_VERSION="4.1.2"
CMDLINE_TOOLS_VERSION="13114758"

export ANDROID_HOME="${ANDROID_HOME:-/usr/lib/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_HOME}"

CMDLINE_TOOLS_ZIP="commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"
SDK_MANAGER="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"

echo "Persisting Android SDK environment"
{
    echo "export ANDROID_HOME=${ANDROID_HOME}"
    echo "export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}"
    echo "export PATH=${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/cmdline-tools/latest/bin:\$PATH"
} >> "${HOME}/.bashrc"

echo "Getting Android command line tools"
mkdir -p "${ANDROID_HOME}/cmdline-tools"
wget -O /tmp/android-commandlinetools.zip "${CMDLINE_TOOLS_URL}"

echo "Unpacking Android command line tools"
rm -rf "${ANDROID_HOME}/cmdline-tools/latest" /tmp/android-cmdline-tools
mkdir -p /tmp/android-cmdline-tools
unzip -q /tmp/android-commandlinetools.zip -d /tmp/android-cmdline-tools
mkdir -p "${ANDROID_HOME}/cmdline-tools/latest"
mv /tmp/android-cmdline-tools/cmdline-tools/* "${ANDROID_HOME}/cmdline-tools/latest/"

echo "Configuring Gradle for Codex"
mkdir -p "${HOME}/.gradle"
cat > "${HOME}/.gradle/gradle.properties" <<'EOF'
org.gradle.daemon=false
org.gradle.caching=true
org.gradle.jvmargs=-Djava.net.useSystemProxies=true -Xmx4g
systemProp.java.net.useSystemProxies=true
EOF

echo "Checking dependency endpoints"
curl -I https://plugins.gradle.org/m2/ || true
curl -I https://repo.maven.apache.org/maven2/ || true
curl -I https://maven.google.com/ || true
curl -I https://dl.google.com/android/repository/repository2-1.xml || true

echo "Updating sdkmanager"
"${SDK_MANAGER}" --sdk_root="${ANDROID_HOME}" --update

echo "Accepting Android SDK licenses"
yes | "${SDK_MANAGER}" --sdk_root="${ANDROID_HOME}" --licenses || true

echo "Installing Android SDK"
"${SDK_MANAGER}" --sdk_root="${ANDROID_HOME}" \
    "platform-tools" \
    "platforms;android-${PLATFORM_VERSION}" \
    "build-tools;${BLD_TOOL_VERSION}" \
    "ndk;${NDK_VERSION}" \
    "cmake;${CMAKE_VERSION}"

echo "Git Submodule Init"
git submodule update --init --recursive

echo "Gradle version"
./gradlew --version

echo "Warming Gradle plugin and dependency cache"
./gradlew help --no-daemon --refresh-dependencies --info