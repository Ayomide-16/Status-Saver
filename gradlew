#!/bin/bash

# Gradle wrapper script
GRADLE_VERSION=8.2
GRADLE_WRAPPER_DIR="gradle/wrapper"

if [ ! -f "$GRADLE_WRAPPER_DIR/gradle-wrapper.jar" ]; then
    echo "Gradle wrapper not found. Please run 'gradle wrapper' to generate it."
    exit 1
fi

java -jar "$GRADLE_WRAPPER_DIR/gradle-wrapper.jar" "$@"
