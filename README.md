# PDF Unloker

パスワード保護されたPDF（給与明細など）を、あらかじめ設定したパスワードで自動的に解除し、アプリ内ビューアーで表示するAndroidアプリです。

## 主な機能

- **自動パスワード解除**: Gmail等の添付PDFをタップするだけで、設定済みパスワードで自動アンロック
- **アプリ内PDF表示**: 解除後のPDFをアプリ内でそのまま表示（外部アプリ不要）
- **安全なパスワード保存**: `EncryptedSharedPreferences`（AES256）でパスワードを暗号化保存

## 動作フロー

1. **初回設定**: アプリを起動してパスワード（生年月日など）を保存
2. **PDFを開く**: Gmail等でPDF添付をタップ → アプリ選択で本アプリを選択
3. **自動解除 → 表示**: バックグラウンドで解除処理が走り、完了次第アプリ内ビューアーが起動

## 技術構成

| 項目 | 内容 |
|------|------|
| 対象OS | Android 6.0 (API 23) 以上 |
| UI | Jetpack Compose |
| PDF解析 | PdfBox-Android 2.0.27.0 |
| PDFレンダリング | Android標準 `PdfRenderer` (API 21+) |
| パスワード保管 | `EncryptedSharedPreferences` (security-crypto 1.0.0) |

## セキュリティ設計

- 解除済み一時ファイルはビューアーを閉じた時点（`onDestroy` または 戻るボタン押下時）で削除
- `android:allowBackup="false"` によりAndroidバックアップへの漏洩を防止
- `FileProvider` のスコープは `cache-path` のみに限定

## ライセンス

MIT License
