#!/bin/sh

#
# Self-healing Gradle wrapper script.
# If gradle-wrapper.jar is missing, it downloads it from the Gradle project.
#

APP_HOME=$( cd "$(dirname "$0")" && pwd )
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

# Download wrapper jar if it doesn't exist.
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Wrapper jar not found. Downloading..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    # Gradle wrapper jar URL for 8.7
    WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar"
    if command -v curl >/dev/null 2>&1; then
        curl -L -o "$WRAPPER_JAR" "$WRAPPER_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$WRAPPER_JAR" "$WRAPPER_URL"
    else
        echo "ERROR: curl or wget is required to download the Gradle wrapper."
        exit 1
    fi
fi

# Build JVM arguments
JAVA_OPTS="${JAVA_OPTS:-}"
GRADLE_OPTS="${GRADLE_OPTS:-}"

exec java $JAVA_OPTS $GRADLE_OPTS \
    -Dorg.gradle.appname=gradlew \
    -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
