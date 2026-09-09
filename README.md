# PDF Unloker

An Android application that automatically unlocks password-protected PDFs (such as pay stubs) using a pre-configured password and displays them in an in-app viewer.

[Japanese Version (日本語版はこちら)](README.JP.md)

## Key Features

- **Automatic Password Unlocking**: Simply tap a PDF attachment in apps like Gmail, and it will be unlocked automatically using your saved password.
- **In-App PDF Viewer**: View unlocked PDFs directly inside the app without needing external tools.
- **Secure Password Storage**: Passwords are saved securely using `EncryptedSharedPreferences` (AES-256).

## Workflow

1. **Initial Setup**: Launch the app and save your password (e.g., date of birth).
2. **Open a PDF**: Tap a PDF attachment in Gmail or another app, then select "PDFUnloker" from the app chooser.
3. **Auto-Unlock & Display**: Unlocking process runs in the background and opens the in-app viewer upon completion.

## Technical Stack

| Item | Details |
|------|---------|
| Target OS | Android 6.0 (API 23) or higher |
| UI | Jetpack Compose |
| PDF Parsing | PdfBox-Android 2.0.27.0 |
| PDF Rendering | Standard Android `PdfRenderer` (API 21+) |
| Password Storage | `EncryptedSharedPreferences` (security-crypto 1.0.0) |

## Security Features

- Temporary unlocked files are automatically deleted when the viewer is closed (`onDestroy` or Back button pressed).
- Prevents leakage to Android Backups via `android:allowBackup="false"`.
- `FileProvider` scope is strictly restricted to `cache-path`.

## License

MIT License
