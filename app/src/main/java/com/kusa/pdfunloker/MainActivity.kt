package com.kusa.pdfunloker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kusa.pdfunloker.ui.theme.PDFUnlokerTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)

        val intentData: Uri? = intent.data
        if (intent.action == Intent.ACTION_VIEW && intentData != null) {
            handlePdfIntent(intentData)
        }

        setContent {
            PDFUnlokerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(intentData, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun handlePdfIntent(uri: Uri) {
        // TODO: 本来は設定画面から取得。テスト用に正しいパスワードを入れてください
        val testPassword = "ここにお使いのPDFのパスワードを入力"

        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = PDDocument.load(inputStream, testPassword)

                if (document.isEncrypted) {
                    document.setAllSecurityToBeRemoved(true)
                }

                // 1. 解除したPDFをキャッシュディレクトリに一時保存
                val outputFile = File(cacheDir, "unlocked_preview.pdf")
                document.save(outputFile)
                document.close()

                // 2. 保存したファイルを外部アプリで開く
                openPdfWithExternalApp(outputFile)
            }
        } catch (e: Exception) {
            android.util.Log.e("PDF_UNLOCK", "解除失敗: ${e.message}")
            // ここで「パスワードが違います」等のトーストを出すと親切です
        }
    }

    private fun openPdfWithExternalApp(file: File) {
        // FileProviderを使ってURIを生成
        val contentUri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }

        // PDF対応アプリを選択して起動
        startActivity(Intent.createChooser(intent, "PDFを開くアプリを選択"))
    }
}

@Composable
fun MainScreen(uri: Uri?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "PDFファイルを解析中...")
        if (uri != null) {
            Text(text = "対象ファイル: ${uri.lastPathSegment}")
            Text(text = "解析が終わると自動的にPDFビューアーが起動します。")
        } else {
            Text(text = "GmailなどのアプリからPDFファイルを開いてください。")
        }
    }
}