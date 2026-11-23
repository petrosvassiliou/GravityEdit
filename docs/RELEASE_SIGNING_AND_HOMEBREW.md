# Release Signing, Apple Notarization, Sparkle 2 EdDSA & Homebrew Tap Runbook

GravityEdit's macOS CI pipeline (`.github/workflows/macos-ci.yml`) and packaging script (`macos/scripts/package_app.sh`) support **zero-configuration ad-hoc builds** by default while automatically upgrading to **Apple Developer ID Application signing, Apple Notary Service (`notarytool`) stapling, Sparkle 2 EdDSA signatures, and Homebrew Tap publishing** as soon as the corresponding GitHub Repository Secrets are configured.

---

## 1. GitHub Actions Secrets Overview

| Secret Name | Purpose | Fallback When Unset |
| :--- | :--- | :--- |
| `MACOS_CERT_P12_BASE64` | Base64-encoded `Developer ID Application` `.p12` certificate | Ad-hoc (`--sign -`) |
| `MACOS_CERT_PASSWORD` | Export passphrase for the `.p12` certificate | Skipped |
| `MACOS_SIGN_IDENTITY` | Full identity string, e.g. `Developer ID Application: Your Name (TEAMID1234)` | `-` |
| `APPLE_ID` | Apple Developer account email for `xcrun notarytool` | Notarization skipped |
| `APPLE_TEAM_ID` | 10-character Apple Developer Team ID | Notarization skipped |
| `APPLE_APP_SPECIFIC_PASSWORD` | App-Specific Password created at `appleid.apple.com` | Notarization skipped |
| `SPARKLE_PUBLIC_ED_KEY` | Public Ed25519 key injected into `Info.plist` (`SUPublicEDKey`) | SHA-256 fallback |
| `SPARKLE_EDDSA_PRIVATE_KEY` | Private Ed25519 key used by Sparkle `sign_update` on `appcast.xml` | Unsigned enclosure |
| `HOMEBREW_TAP_TOKEN` | GitHub PAT (`contents:write` on `petrosvassiliou/homebrew-tap`) | In-repo `Casks/gravityedit.rb` |

---

## 2. Exporting an Apple Developer ID Certificate

1. Open **Xcode → Settings → Accounts → Manage Certificates…** and click **+** → **Developer ID Application**.
2. Open **Keychain Access**, right-click **Developer ID Application: …**, choose **Export…**, and save as `DeveloperID.p12` with a strong passphrase.
3. Base64-encode the `.p12` archive onto your clipboard:
   ```bash
   base64 -i DeveloperID.p12 | pbcopy
   ```
4. Paste into GitHub **Settings → Secrets and variables → Actions → `MACOS_CERT_P12_BASE64`**, and store the passphrase in `MACOS_CERT_PASSWORD`.

---

## 3. Generating Sparkle 2 EdDSA Keys

```bash
cd macos
swift package resolve
GENERATE_KEYS="$(find .build -type f -name generate_keys | head -n 1)"
"${GENERATE_KEYS}"
```

- Copy the printed `<string>...</string>` public key value into `SPARKLE_PUBLIC_ED_KEY`.
- Export the private key string (`"${GENERATE_KEYS}" -x sparkle_eddsa.key`) into `SPARKLE_EDDSA_PRIVATE_KEY` and delete `sparkle_eddsa.key`.

---

## 4. Installing via Homebrew Cask

Users can install both `GravityEdit.app` and the `gravity` command-line tool in one command directly from this repository's built-in tap:

```bash
brew tap petrosvassiliou/gravityedit https://github.com/petrosvassiliou/GravityEdit
brew install --cask gravityedit
```

If `HOMEBREW_TAP_TOKEN` is also configured for a dedicated `petrosvassiliou/homebrew-tap` repository, every `v*` git tag push automatically commits the SHA-256-pinned `Casks/gravityedit.rb` into `petrosvassiliou/homebrew-tap`.
