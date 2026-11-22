#!/usr/bin/env bash
set -euo pipefail

# GravityEdit for macOS — Native .app, .app.zip, .dmg & Sparkle 2 appcast.xml Builder
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MACOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

VERSION="${1:-1.3.4}"
VERSION_CLEAN="${VERSION#v}"
TAG_NAME="v${VERSION_CLEAN}"
BUILD_NUMBER="${BUILD_NUMBER:-13}"
APP_NAME="GravityEdit"
BUNDLE_ID="com.gravityedit.mac"

DIST_DIR="${MACOS_DIR}/dist"
APP_BUNDLE="${DIST_DIR}/${APP_NAME}.app"
CONTENTS_DIR="${APP_BUNDLE}/Contents"
MACOS_BIN_DIR="${CONTENTS_DIR}/MacOS"
RESOURCES_DIR="${CONTENTS_DIR}/Resources"
FRAMEWORKS_DIR="${CONTENTS_DIR}/Frameworks"

rm -rf "${DIST_DIR}"
mkdir -p "${MACOS_BIN_DIR}" "${RESOURCES_DIR}" "${FRAMEWORKS_DIR}"

echo "==> [1/6] Building Universal Release binary (arm64 + x86_64) via SwiftPM..."
cd "${MACOS_DIR}"
if swift build -c release --arch arm64 --arch x86_64 --product GravityEdit; then
    BIN_PATH="${MACOS_DIR}/.build/apple/Products/Release/GravityEdit"
else
    echo "Universal cross-arch build unavailable; building native arm64 Release..."
    swift build -c release --product GravityEdit
    BIN_PATH="$(swift build -c release --show-bin-path)/GravityEdit"
fi

cp "${BIN_PATH}" "${MACOS_BIN_DIR}/${APP_NAME}"
chmod +x "${MACOS_BIN_DIR}/${APP_NAME}"

# Ensure @executable_path/../Frameworks is present in RPATH for Sparkle.framework
if ! otool -l "${MACOS_BIN_DIR}/${APP_NAME}" | grep -q "@executable_path/../Frameworks"; then
    install_name_tool -add_rpath "@executable_path/../Frameworks" "${MACOS_BIN_DIR}/${APP_NAME}" || true
fi

echo "==> [2/6] Embedding Sparkle 2 Framework..."
SPARKLE_FRAMEWORK_SRC="$(find "${MACOS_DIR}/.build" -type d -name "Sparkle.framework" | grep "macos" | head -n 1 || true)"
if [ -z "${SPARKLE_FRAMEWORK_SRC}" ]; then
    SPARKLE_FRAMEWORK_SRC="$(find "${MACOS_DIR}/.build" -type d -name "Sparkle.framework" | head -n 1)"
fi

if [ -n "${SPARKLE_FRAMEWORK_SRC}" ] && [ -d "${SPARKLE_FRAMEWORK_SRC}" ]; then
    ditto "${SPARKLE_FRAMEWORK_SRC}" "${FRAMEWORKS_DIR}/Sparkle.framework"
    echo "Embedded Sparkle.framework from ${SPARKLE_FRAMEWORK_SRC}"
else
    echo "ERROR: Sparkle.framework not found in .build artifacts!"
    exit 1
fi

echo "==> [3/6] Generating Liquid Glass AppIcon.icns..."
ICONSET_TMP="$(mktemp -d)/AppIcon.iconset"
swift "${SCRIPT_DIR}/generate_icon.swift" "${ICONSET_TMP}"
iconutil -c icns "${ICONSET_TMP}" -o "${RESOURCES_DIR}/AppIcon.icns"

echo "==> [4/6] Writing Info.plist & PkgInfo..."
printf "APPL????" > "${CONTENTS_DIR}/PkgInfo"

PUBLIC_ED_KEY="${SPARKLE_PUBLIC_ED_KEY:-}"
PUBLIC_ED_PLIST_ENTRY=""
if [ -n "${PUBLIC_ED_KEY}" ]; then
    PUBLIC_ED_PLIST_ENTRY="    <key>SUPublicEDKey</key>
    <string>${PUBLIC_ED_KEY}</string>"
fi

cat <<EOF > "${CONTENTS_DIR}/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleLocalizations</key>
    <array>
        <string>en</string>
        <string>de</string>
        <string>it</string>
        <string>es</string>
        <string>fr</string>
        <string>el</string>
    </array>
    <key>CFBundleExecutable</key>
    <string>${APP_NAME}</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>CFBundleIdentifier</key>
    <string>${BUNDLE_ID}</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>${APP_NAME}</string>
    <key>CFBundleDisplayName</key>
    <string>${APP_NAME}</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>${VERSION_CLEAN}</string>
    <key>CFBundleVersion</key>
    <string>${BUILD_NUMBER}</string>
    <key>LSApplicationCategoryType</key>
    <string>public.app-category.developer-tools</string>
    <key>LSMinimumSystemVersion</key>
    <string>15.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSSupportsAutomaticTermination</key>
    <false/>
    <key>SUFeedURL</key>
    <string>https://github.com/petrosvassiliou/GravityEdit/releases/latest/download/appcast.xml</string>
    <key>SUEnableAutomaticChecks</key>
    <true/>
${PUBLIC_ED_PLIST_ENTRY}
    <key>CFBundleDocumentTypes</key>
    <array>
        <dict>
            <key>CFBundleTypeName</key>
            <string>Source Code &amp; Plain Text Document</string>
            <key>CFBundleTypeRole</key>
            <string>Editor</string>
            <key>LSHandlerRank</key>
            <string>Alternate</string>
            <key>CFBundleTypeExtensions</key>
            <array>
                <string>ps1</string>
                <string>psm1</string>
                <string>psd1</string>
                <string>swift</string>
                <string>json</string>
                <string>xml</string>
                <string>yaml</string>
                <string>yml</string>
                <string>md</string>
                <string>py</string>
                <string>kt</string>
                <string>sh</string>
                <string>zsh</string>
                <string>txt</string>
                <string>log</string>
            </array>
        </dict>
    </array>
</dict>
</plist>
EOF

echo "==> [5/6] Signing nested Sparkle XPC/Updater Mach-O binaries & GravityEdit.app..."
xattr -cr "${APP_BUNDLE}" || true
SIGN_IDENTITY="${MACOS_SIGN_IDENTITY:--}"

# IMPORTANT: Never pass `--options runtime` when ad-hoc signing (`--sign -`)!
# Hardened Runtime (`CS_RUNTIME`) activates kernel Library Validation, which requires a non-empty
# Apple Developer Team ID to match across the main binary and `@rpath/Sparkle.framework`; with ad-hoc
# empty Team IDs, dyld4 aborts at launch with "mapping process and mapped file have different Team IDs".
if [ "${SIGN_IDENTITY}" = "-" ]; then
    CODESIGN_ARGS=(--force --timestamp=none --sign "-")
else
    CODESIGN_ARGS=(--force --options runtime --timestamp=none --sign "${SIGN_IDENTITY}")
fi

# Inside-out code signing of every Mach-O slice so zero upstream Sparkle Team IDs remain
if [ -d "${FRAMEWORKS_DIR}/Sparkle.framework/Versions/B/XPCServices" ]; then
    for xpc in "${FRAMEWORKS_DIR}/Sparkle.framework/Versions/B/XPCServices/"*.xpc; do
        if [ -d "$xpc" ]; then
            for xpc_bin in "$xpc/Contents/MacOS/"*; do
                [ -f "$xpc_bin" ] && codesign "${CODESIGN_ARGS[@]}" "$xpc_bin"
            done
            codesign "${CODESIGN_ARGS[@]}" "$xpc"
        fi
    done
fi
if [ -f "${FRAMEWORKS_DIR}/Sparkle.framework/Versions/B/Autoupdate" ]; then
    codesign "${CODESIGN_ARGS[@]}" "${FRAMEWORKS_DIR}/Sparkle.framework/Versions/B/Autoupdate"
fi
if [ -d "${FRAMEWORKS_DIR}/Sparkle.framework/Versions/B/Updater.app" ]; then
    if [ -f "${FRAMEWORKS_DIR}/Sparkle.framework/Versions/B/Updater.app/Contents/MacOS/Updater" ]; then
        codesign "${CODESIGN_ARGS[@]}" "${FRAMEWORKS_DIR}/Sparkle.framework/Versions/B/Updater.app/Contents/MacOS/Updater"
    fi
    codesign "${CODESIGN_ARGS[@]}" "${FRAMEWORKS_DIR}/Sparkle.framework/Versions/B/Updater.app"
fi
if [ -f "${FRAMEWORKS_DIR}/Sparkle.framework/Versions/B/Sparkle" ]; then
    codesign "${CODESIGN_ARGS[@]}" "${FRAMEWORKS_DIR}/Sparkle.framework/Versions/B/Sparkle"
fi
codesign "${CODESIGN_ARGS[@]}" "${FRAMEWORKS_DIR}/Sparkle.framework"
codesign "${CODESIGN_ARGS[@]}" "${MACOS_BIN_DIR}/${APP_NAME}"
codesign "${CODESIGN_ARGS[@]}" "${APP_BUNDLE}"

codesign --verify --deep --strict --verbose=2 "${APP_BUNDLE}"

echo "==> [6/6] Packaging .app.zip, .dmg, .sha256 & Sparkle appcast.xml..."
ZIP_NAME="GravityEdit-macOS-${TAG_NAME}.app.zip"
DMG_NAME="GravityEdit-macOS-${TAG_NAME}.dmg"

ditto -c -k --sequesterRsrc --keepParent "${APP_BUNDLE}" "${DIST_DIR}/${ZIP_NAME}"

# Stage DMG folder with /Applications symlink for standard macOS drag-to-install UX
DMG_STAGE="$(mktemp -d)"
cp -R "${APP_BUNDLE}" "${DMG_STAGE}/"
ln -s /Applications "${DMG_STAGE}/Applications"
hdiutil create -volname "${APP_NAME} ${TAG_NAME}" \
    -srcfolder "${DMG_STAGE}" \
    -ov -format UDZO \
    "${DIST_DIR}/${DMG_NAME}"
rm -rf "${DMG_STAGE}"

(
    cd "${DIST_DIR}"
    shasum -a 256 "${ZIP_NAME}" > "${ZIP_NAME}.sha256"
    shasum -a 256 "${DMG_NAME}" > "${DMG_NAME}.sha256"
)

ZIP_SIZE="$(stat -f%z "${DIST_DIR}/${ZIP_NAME}")"
ZIP_SHA256="$(awk '{print $1}' "${DIST_DIR}/${ZIP_NAME}.sha256")"
PUB_DATE="$(LC_ALL=C date -u +"%a, %d %b %Y %H:%M:%S +0000")"
DOWNLOAD_URL="https://github.com/petrosvassiliou/GravityEdit/releases/download/${TAG_NAME}/${ZIP_NAME}"

ED_SIGNATURE_ATTR=""
if [ -n "${SPARKLE_EDDSA_PRIVATE_KEY:-}" ]; then
    SIGN_TOOL="$(find "${MACOS_DIR}/.build" -type f -name "sign_update" | head -n 1 || true)"
    if [ -n "${SIGN_TOOL}" ] && [ -x "${SIGN_TOOL}" ]; then
        SIG_OUT="$(printf "%s" "${SPARKLE_EDDSA_PRIVATE_KEY}" | "${SIGN_TOOL}" --ed-key-file - "${DIST_DIR}/${ZIP_NAME}" || true)"
        if [ -n "${SIG_OUT}" ]; then
            ED_SIGNATURE_ATTR="${SIG_OUT}"
        fi
    fi
fi

cat <<EOF > "${DIST_DIR}/appcast.xml"
<?xml version="1.0" encoding="utf-8"?>
<rss version="2.0" xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle" xmlns:dc="http://purl.org/dc/elements/1.1/">
  <channel>
    <title>GravityEdit for macOS Changelog</title>
    <link>https://github.com/petrosvassiliou/GravityEdit/releases</link>
    <description>Most recent updates to GravityEdit for macOS (Liquid Glass Edition).</description>
    <language>en</language>
    <item>
      <title>GravityEdit ${TAG_NAME}</title>
      <pubDate>${PUB_DATE}</pubDate>
      <sparkle:version>${BUILD_NUMBER}</sparkle:version>
      <sparkle:shortVersionString>${VERSION_CLEAN}</sparkle:shortVersionString>
      <sparkle:minimumSystemVersion>15.0</sparkle:minimumSystemVersion>
      <description><![CDATA[
        <h2>GravityEdit ${TAG_NAME} — macOS Liquid Glass &amp; PowerShell Workbench</h2>
        <ul>
          <li>Native SwiftUI + AppKit (<code>NSTextView</code>) macOS edition built with Apple Liquid Glass design.</li>
          <li>Full PowerShell AST &amp; Procedural Function Navigator, Code Folding, Auto-Completion, and live PSScriptAnalyzer Quick Fixes.</li>
          <li>14 BBEdit Text Factory operations, PCRE Find/Replace &amp; Multi-File Search, Side-by-Side Myers Diff, and Clippings.</li>
          <li>Verified SHA-256: <code>${ZIP_SHA256}</code></li>
        </ul>
      ]]></description>
      <enclosure url="${DOWNLOAD_URL}" length="${ZIP_SIZE}" type="application/octet-stream" ${ED_SIGNATURE_ATTR} />
    </item>
  </channel>
</rss>
EOF

echo "==> Packaging complete! Artifacts in ${DIST_DIR}:"
ls -lh "${DIST_DIR}"
