#!/bin/bash

cd /var/www/SMS-bomb

# Paths
BUILD_GRADLE="app/build.gradle.kts"
BASE_DIR="fastlane/metadata/android"
CHANGELOG_MD="CHANGELOG.md"
CHANGELOG_DE_MD="CHANGELOG_de.md"
PLAYSTORE_DIR="fastlane/metadata/android/playstore"

# Must be the same as versionName in app/build.gradle.kts
VERSION_NAME=1.0
# Extract versionCode from app/build.gradle.kts (Kotlin DSL: versionCode = 1)
VERSION_CODE=$(grep -oP 'versionCode\s*=\s*\K\d+' "$BUILD_GRADLE")
# Get current date
CURRENT_DATE=$(date +"%Y-%m-%d")

# Play Store character limit for changelogs (per language)
PLAYSTORE_CHAR_LIMIT=500

# Function to check length and warn if exceeded
check_length() {
  local content="$1"
  local locale="$2"
  # Count only the actual changelog entries, not the XML tags
  local content_without_tags=$(echo "$content" | grep -v "^<.*>$")
  local chars=$(echo "$content_without_tags" | wc -c)
  local limit=$PLAYSTORE_CHAR_LIMIT

  echo "Changelog for $locale: $chars characters"

  if [ $chars -gt $limit ]; then
    local over=$((chars - limit))
    echo "⚠️ WARNING: Changelog for $locale is $over characters too long! (Maximum: $limit)"
    return 1
  else
    local remaining=$((limit - chars))
    echo "✔️ OK: $remaining characters remaining."
    return 0
  fi
}

# German Changelog
DE_CHANGES=$(cat << EOF
- Erste Version
- Periodischer SMS-Versand an einstellbare Nummer mit eigenem Text und Intervall
- Sende-Protokoll und Live-Status in der App
EOF
)

# English Changelog
EN_CHANGES=$(cat << EOF
- Initial release
- Periodic SMS sending to a configurable number with custom text and interval
- In-app send log and live status
EOF
)

# Play Store has a limit of 500 characters
PLAYSTORE_DE_DE=$(cat << EOF
<de-DE>
</de-DE>
EOF
)

PLAYSTORE_EN_GB=$(cat << EOF
<en-GB>
</en-GB>
EOF
)

# If PLAYSTORE sections are empty, use content from DE_CHANGES and EN_CHANGES
if [ "$(echo "$PLAYSTORE_DE_DE" | grep -v '^<.*>$' | tr -d '\n' | tr -d ' ')" = "" ]; then
  PLAYSTORE_DE_DE=$(cat << EOF
<de-DE>
$DE_CHANGES
</de-DE>
EOF
)
  echo "Using DE_CHANGES for PLAYSTORE_DE_DE as it was empty"
fi

if [ "$(echo "$PLAYSTORE_EN_GB" | grep -v '^<.*>$' | tr -d '\n' | tr -d ' ')" = "" ]; then
  PLAYSTORE_EN_GB=$(cat << EOF
<en-GB>
$EN_CHANGES
</en-GB>
EOF
)
  echo "Using EN_CHANGES for PLAYSTORE_EN_GB as it was empty"
fi

# Create fastlane changelogs
# German changelog
DE_CHANGELOG_FILE="$BASE_DIR/de/changelogs/${VERSION_CODE}.txt"
mkdir -p "$(dirname "$DE_CHANGELOG_FILE")"
echo "$DE_CHANGES" > "$DE_CHANGELOG_FILE"

# English changelog
EN_CHANGELOG_FILE="$BASE_DIR/en-US/changelogs/${VERSION_CODE}.txt"
mkdir -p "$(dirname "$EN_CHANGELOG_FILE")"
echo "$EN_CHANGES" > "$EN_CHANGELOG_FILE"

# Create Play Store specific changelogs
mkdir -p "$PLAYSTORE_DIR"
PLAYSTORE_CHANGELOG_FILE="$PLAYSTORE_DIR/changelog_${VERSION_CODE}.txt"
echo "# Play Store Changelogs für Version $VERSION_NAME" > "$PLAYSTORE_CHANGELOG_FILE"
echo "" >> "$PLAYSTORE_CHANGELOG_FILE"
echo "$PLAYSTORE_EN_GB" >> "$PLAYSTORE_CHANGELOG_FILE"
echo "$PLAYSTORE_DE_DE" >> "$PLAYSTORE_CHANGELOG_FILE"

# check length of Play Store Changelogs
echo ""
echo "check length of Play Store Changelogs:"
echo "-----------------------------------------------"
check_length "$PLAYSTORE_EN_GB" "en-GB"
echo ""
check_length "$PLAYSTORE_DE_DE" "de-DE"
echo "-----------------------------------------------"

# Original text for reference: check length of full changes
echo ""
echo "For Reference - check length of full changes:"
echo "-----------------------------------------------"
TEMP_EN=$(mktemp)
echo "$EN_CHANGES" > "$TEMP_EN"
TEMP_DE=$(mktemp)
echo "$DE_CHANGES" > "$TEMP_DE"
check_length "$(cat $TEMP_EN)" "Full EN"
echo ""
check_length "$(cat $TEMP_DE)" "Full DE"
rm "$TEMP_EN" "$TEMP_DE"
echo "-----------------------------------------------"

# Update CHANGELOG.md (English only)
if [ ! -f "$CHANGELOG_MD" ]; then
  echo "# Changelog" > "$CHANGELOG_MD"
  echo "=========" >> "$CHANGELOG_MD"
  echo "" >> "$CHANGELOG_MD"
fi

TEMP_FILE=$(mktemp)

# Check if the current version already exists in the changelog
if grep -q "## Version ${VERSION_NAME}" "$CHANGELOG_MD"; then
    # Version exists, update it instead of adding a new entry
    echo "Updating existing entry for Version ${VERSION_NAME} in CHANGELOG.md"

    VERSION_LINE=$(grep -n "## Version ${VERSION_NAME}" "$CHANGELOG_MD" | cut -d ':' -f1)

    head -n $((VERSION_LINE - 1)) "$CHANGELOG_MD" > "$TEMP_FILE"

    echo "## Version ${VERSION_NAME} (${CURRENT_DATE})" >> "$TEMP_FILE"

    echo "$EN_CHANGES" >> "$TEMP_FILE"
    echo "" >> "$TEMP_FILE"

    # Find the next version header or end of file
    NEXT_VERSION_LINE=$(tail -n +$((VERSION_LINE + 1)) "$CHANGELOG_MD" | grep -n "^## Version" | head -n 1 | cut -d ':' -f1)

    if [ -n "$NEXT_VERSION_LINE" ]; then
      NEXT_VERSION_LINE=$((VERSION_LINE + NEXT_VERSION_LINE))
      tail -n +$NEXT_VERSION_LINE "$CHANGELOG_MD" >> "$TEMP_FILE"
    fi
  else
    # Version doesn't exist, add a new entry
    head -n 3 "$CHANGELOG_MD" > "$TEMP_FILE"

    echo "" >> "$TEMP_FILE"
    echo "## Version ${VERSION_NAME} (${CURRENT_DATE})" >> "$TEMP_FILE"
    echo "$EN_CHANGES" >> "$TEMP_FILE"
    echo "" >> "$TEMP_FILE"

    tail -n +4 "$CHANGELOG_MD" >> "$TEMP_FILE"
  fi

  mv "$TEMP_FILE" "$CHANGELOG_MD"

# Update CHANGELOG_de.md (German only)
if [ ! -f "$CHANGELOG_DE_MD" ]; then
  echo "# Changelog" > "$CHANGELOG_DE_MD"
  echo "=========" >> "$CHANGELOG_DE_MD"
  echo "" >> "$CHANGELOG_DE_MD"
fi

TEMP_FILE=$(mktemp)

  if grep -q "## Version ${VERSION_NAME}" "$CHANGELOG_DE_MD"; then
    echo "Updating existing entry for Version ${VERSION_NAME} in CHANGELOG_de.md"

    VERSION_LINE=$(grep -n "## Version ${VERSION_NAME}" "$CHANGELOG_DE_MD" | cut -d ':' -f1)

    head -n $((VERSION_LINE - 1)) "$CHANGELOG_DE_MD" > "$TEMP_FILE"

    echo "## Version ${VERSION_NAME} (${CURRENT_DATE})" >> "$TEMP_FILE"

    echo "$DE_CHANGES" >> "$TEMP_FILE"
    echo "" >> "$TEMP_FILE"

    NEXT_VERSION_LINE=$(tail -n +$((VERSION_LINE + 1)) "$CHANGELOG_DE_MD" | grep -n "^## Version" | head -n 1 | cut -d ':' -f1)

    if [ -n "$NEXT_VERSION_LINE" ]; then
      NEXT_VERSION_LINE=$((VERSION_LINE + NEXT_VERSION_LINE))
      tail -n +$NEXT_VERSION_LINE "$CHANGELOG_DE_MD" >> "$TEMP_FILE"
    fi
  else
    head -n 3 "$CHANGELOG_DE_MD" > "$TEMP_FILE"

    echo "" >> "$TEMP_FILE"
    echo "## Version ${VERSION_NAME} (${CURRENT_DATE})" >> "$TEMP_FILE"
    echo "$DE_CHANGES" >> "$TEMP_FILE"
    echo "" >> "$TEMP_FILE"

    tail -n +4 "$CHANGELOG_DE_MD" >> "$TEMP_FILE"
  fi

  mv "$TEMP_FILE" "$CHANGELOG_DE_MD"

echo "Changelogs successfully updated!"
echo "Fastlane changelogs: de/${VERSION_CODE}.txt and en-US/${VERSION_CODE}.txt"
echo "Play Store changelog: $PLAYSTORE_CHANGELOG_FILE"
echo ""
echo "Play Store changelogs can be copied like this:"
echo "cat $PLAYSTORE_CHANGELOG_FILE"
