#!/bin/bash
# Release build script for O2 Free mit Weiter Speed Unlocker (adapted from Lalumo's mobile-build.sh,
# minus the Capacitor/npm parts — this is a pure Gradle project).
#
# Usage: bash release.sh [options]
# Options:
#   -h, --help            Show help
#   -u, --update-version  Bump the minor version (major.minor only) in
#                         app/build.gradle.kts, dev/add_changelog.sh and
#                         metadata/com.smsbomb.app.yml, then build
#
# Without -u it just builds the current version.

UPDATE_VERSION=false

show_help() {
  echo ""
  echo "O2 Free mit Weiter Speed Unlocker Release Build Script"
  echo ""
  echo "Usage: bash release.sh [options]"
  echo "Options:"
  echo "  -h, --help            Show this help message"
  echo "  -u, --update-version  Increment minor version and sync versionName/versionCode"
  echo "                        into app/build.gradle.kts, dev/add_changelog.sh and"
  echo "                        metadata/com.smsbomb.app.yml"
  echo ""
  exit 0
}

while [[ $# -gt 0 ]]; do
  case $1 in
    -h|--help)
      show_help
      ;;
    -u|--update-version)
      UPDATE_VERSION=true
      shift
      ;;
    *)
      echo "Unknown option: $1"
      show_help
      ;;
  esac
done

GRADLE_FILE="app/build.gradle.kts"
CHANGELOG_GENERATOR_FILE="dev/add_changelog.sh"
FDROID_METADATA_FILE="metadata/com.smsbomb.app.yml"

update_version() {
  if [ ! -f "$GRADLE_FILE" ]; then
    echo "Error: $GRADLE_FILE not found!"
    return 1
  fi

  # extract current version from app/build.gradle.kts (Kotlin DSL)
  CURRENT_VERSION=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$GRADLE_FILE")
  CURRENT_CODE=$(grep -oP 'versionCode\s*=\s*\K\d+' "$GRADLE_FILE")

  if [ -z "$CURRENT_VERSION" ] || [ -z "$CURRENT_CODE" ]; then
    echo "Error: Could not find versionName/versionCode in $GRADLE_FILE"
    return 1
  fi

  # split version into major.minor and increment minor, rollover at 99
  MAJOR=$(echo "$CURRENT_VERSION" | cut -d. -f1)
  MINOR=$(echo "$CURRENT_VERSION" | cut -d. -f2)

  if [ "$MINOR" -eq 99 ]; then
    NEW_MAJOR=$((MAJOR + 1))
    NEW_VERSION="$NEW_MAJOR.0"
    echo "Major version rollover: $CURRENT_VERSION → $NEW_VERSION (minor reached 99)"
  else
    NEW_VERSION="$MAJOR.$((MINOR + 1))"
  fi
  NEW_CODE=$((CURRENT_CODE + 1))

  echo "###### Updating version: $CURRENT_VERSION → $NEW_VERSION (code $CURRENT_CODE → $NEW_CODE)"

  # Kotlin DSL: versionName = "1.0" / versionCode = 1
  sed -i "s/versionName = \"[^\"]*\"/versionName = \"$NEW_VERSION\"/" "$GRADLE_FILE"
  sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$GRADLE_FILE"

  # Update version in add_changelog.sh
  if [ -f "$CHANGELOG_GENERATOR_FILE" ]; then
    sed -i "s/VERSION_NAME=[0-9.]\+/VERSION_NAME=$NEW_VERSION/" "$CHANGELOG_GENERATOR_FILE"
    echo "Updated $CHANGELOG_GENERATOR_FILE to $NEW_VERSION"
  else
    echo "Changelog generator not found at $CHANGELOG_GENERATOR_FILE"
  fi

  # Update F-Droid metadata: Builds entry (first occurrences), Current* (last lines), tag
  if [ -f "$FDROID_METADATA_FILE" ]; then
    sed -i "0,/versionName: '[0-9.]\+'/s//versionName: '$NEW_VERSION'/" "$FDROID_METADATA_FILE"
    sed -i "0,/versionCode: [0-9]\+/s//versionCode: $NEW_CODE/" "$FDROID_METADATA_FILE"
    sed -i "s/CurrentVersion: '[0-9.]\+'/CurrentVersion: '$NEW_VERSION'/" "$FDROID_METADATA_FILE"
    sed -i "s/CurrentVersionCode: [0-9]\+/CurrentVersionCode: $NEW_CODE/" "$FDROID_METADATA_FILE"
    sed -i "s/commit: v[0-9.]\+/commit: v$NEW_VERSION/" "$FDROID_METADATA_FILE"
    echo "Updated $FDROID_METADATA_FILE to $NEW_VERSION ($NEW_CODE), tag v$NEW_VERSION"
  else
    echo "F-Droid metadata file not found at $FDROID_METADATA_FILE"
  fi

  echo ""
  echo "Next steps:"
  echo "  1. Edit changelog entries in $CHANGELOG_GENERATOR_FILE"
  echo "  2. bash $CHANGELOG_GENERATOR_FILE"
  echo "  3. git commit && git tag v$NEW_VERSION"
}

if [ "$UPDATE_VERSION" = true ]; then
  echo "###### Updating version numbers..."
  update_version || exit 1
fi

echo "###### Building and testing..."
./gradlew clean assembleDebug assembleRelease testDebugUnitTest --console=plain
