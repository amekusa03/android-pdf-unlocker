# PDFビューアー機能の修正タスク

- [x] `MainActivity.kt` の修正
    - [x] `onNewIntent` の実装
    - [x] `handlePdfIntent` の安定化（一時ファイル経由の読み込み、未暗号化PDF対応）
    - [x] `PdfViewerScreen` の再描画ロジック修正 (`remember(file)`)
- [x] ビルド確認 (assembleDebug)
- [ ] 動作確認（ユーザーによる実機確認を推奨）
    - [ ] 未暗号化PDFの閲覧
    - [ ] 暗号化PDFの閲覧（保存済みパスワード使用）
    - [ ] 連続して異なるPDFを開いた際の挙動
