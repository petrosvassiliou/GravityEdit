cask "gravityedit" do
  version "1.5.0"
  sha256 :no_check

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
