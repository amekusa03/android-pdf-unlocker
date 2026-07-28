# PDFビューアー機能の修正と改善計画

ユーザーから「PDFのビューア機能が使えない」との報告を受け、現在のコードを分析した結果、いくつかの潜在的な不具合と改善点が見つかりました。特に、既にアプリが開いている状態で別のPDFを開いた際の挙動や、レンダリングエンジンのライフサイクル管理に問題がある可能性があります。

## 調査結果
- `onNewIntent` が実装されていないため、アプリ起動中に別のPDFを開いても処理されない可能性がある。
- `PdfViewerScreen` 内の `remember` で `PdfRenderer` を生成しているが、キャッシュファイルのパスが固定であるため、別のファイルが読み込まれても再生成されない可能性がある（バグ）。
- PDFBox での読み込み時に、パスワードが設定されていない場合の考慮が不足している。
- `PdfRenderer` はシーク可能なファイル記述子を必要とするが、ストリームから直接読み込むよりも一度ファイルに保存してから処理する方が安定する。

## 提案される変更

### 1. MainActivity のライフサイクル対応 [MODIFY] [MainActivity.kt](file:///home/kusa/ドキュメント/AndroidStudioProjects/PDFUnloker/app/src/main/java/com/kusa/pdfunloker/MainActivity.kt)
- `onNewIntent` をオーバーライドし、新しい Intent が届いた際にも `handlePdfIntent` を呼び出すようにします。
- `handlePdfIntent` を改善し、パスワードが不要な場合はそのまま、必要な場合のみ保存されたパスワードを使用するように試行プロセスを追加します。
- 解析中の状態（Loading）を明確にし、ユーザーにフィードバックを返します。

### 2. PDFビューアーの信頼性向上 [MODIFY] [MainActivity.kt](file:///home/kusa/ドキュメント/AndroidStudioProjects/PDFUnloker/app/src/main/java/com/kusa/pdfunloker/MainActivity.kt)
- `PdfViewerScreen` において、`unlockedFile` の変更を検知して `PdfRenderer` を確実に再生成するように修正します。
- `ParcelFileDescriptor` のオープンに失敗した場合のエラーハンドリングを追加します。

### 3. PDFBox 読み込み処理の最適化 [MODIFY] [MainActivity.kt](file:///home/kusa/ドキュメント/AndroidStudioProjects/PDFUnloker/app/src/main/java/com/kusa/pdfunloker/MainActivity.kt)
- 一度テンポラリファイルに保存してから `PDDocument.load` を行うことで、メモリ使用量を抑え、読み込みの安定性を高めます。

## 検証計画

### 修正後の確認
- 他のアプリ（Gmailなど）からPDFを開いた際に、ビューアーが正しく起動するか。
- パスワード保護されていないPDFも閲覧できるか。
- アプリ起動中に別のPDFを開き直した際に、内容が更新されるか。

### 自動テスト
- `PasswordManager` の動作確認（既存の機能が壊れていないか）。
