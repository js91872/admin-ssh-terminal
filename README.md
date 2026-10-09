# Admin SSH Terminal

Native Android SSH client focused on saved connections, reusable commands, and offline terminal history.

## V1 roadmap
- SSH password and private-key authentication with verified server host keys
- Secure local credential storage using Android Keystore-backed encryption
- Interactive ANSI/xterm terminal and fixed monospace font
- Preset terminal colors; preserve remote ANSI output
- Saved commands and execution transcripts
- Searchable offline history and TXT export

## Development
Open in Android Studio (JDK 17). Build with `gradle assembleDebug` after installing the Android SDK. The GitHub Actions workflow builds a debug APK on pushes.

**Security:** Never commit passwords, signing keys, or server secrets. This is an initial project scaffold, not yet a working SSH client.
