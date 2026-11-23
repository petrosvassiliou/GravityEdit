#!/usr/bin/env bash
set -euo pipefail

# Generates a Homebrew Cask formula (gravityedit.rb) for GravityEdit for macOS,
# installing GravityEdit.app to /Applications and linking the `gravity` CLI binary.

VERSION_RAW="${1:-1.5.0}"
VERSION_CLEAN="${VERSION_RAW#v}"
DMG_SHA256="${2:-:no_check}"
OUTPUT_PATH="${3:-}"

if [ "${DMG_SHA256}" = ":no_check" ] || [ -z "${DMG_SHA256}" ]; then
    SHA_LINE="  sha256 :no_check"
else
    SHA_LINE="  sha256 \"${DMG_SHA256}\""
fi

CASK_CONTENT=$(cat <<EOF
cask "gravityedit" do
  version "${VERSION_CLEAN}"
${SHA_LINE}

  url "https://github.com/petrosvassiliou/GravityEdit/releases/download/v#{version}/GravityEdit-macOS-v#{version}.dmg"
  name "GravityEdit"
  desc "PowerShell-first BBEdit workbench built natively with SwiftUI Liquid Glass & AppKit"
  homepage "https://github.com/petrosvassiliou/GravityEdit"

  livecheck do
    url :url
    strategy :github_latest
  end

  depends_on macos: ">= :sequoia"

  app "GravityEdit.app"
  binary "#{appdir}/GravityEdit.app/Contents/MacOS/gravity"

  zap trash: [
    "~/Library/Application Support/GravityEdit",
    "~/Library/Caches/com.gravityedit.mac",
    "~/Library/Preferences/com.gravityedit.mac.plist",
    "~/Library/Saved Application State/com.gravityedit.mac.savedState",
  ]
end
EOF
)

if [ -n "${OUTPUT_PATH}" ]; then
    mkdir -p "$(dirname "${OUTPUT_PATH}")"
    printf "%s\n" "${CASK_CONTENT}" > "${OUTPUT_PATH}"
    echo "Generated Homebrew Cask at ${OUTPUT_PATH}"
else
    printf "%s\n" "${CASK_CONTENT}"
fi
