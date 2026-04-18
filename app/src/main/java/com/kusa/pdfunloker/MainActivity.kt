package com.kusa.pdfunloker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kusa.pdfunloker.ui.theme.PDFUnlokerTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
class MainActivity : ComponentActivity() {
    private lateinit var passwordManager: PasswordManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        passwordManager = PasswordManager(this) // 初期化
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
        val savedPassword = passwordManager.getPassword()

        if (savedPassword == null) {
            Toast.makeText(this, "パスワードを設定してください", Toast.LENGTH_LONG).show()
            return
        }

        // lifecycleScope.launch を使ってバックグラウンドで実行
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    // ここで重い解析処理を実行
                    val document = PDDocument.load(inputStream, savedPassword)

                    if (document.isEncrypted) {
                        document.setAllSecurityToBeRemoved(true)
                    }

                    val outputFile = File(cacheDir, "unlocked_preview.pdf")
                    document.save(outputFile)
                    document.close()

                    // UI操作（アプリ起動）はメインスレッドに戻す
                    withContext(Dispatchers.Main) {
                        openPdfWithExternalApp(outputFile)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.util.Log.e("PDF_UNLOCK", "解除失敗: ${e.message}")
                    // ユーザーにエラーを通知
                    Toast.makeText(this@MainActivity, "解除に失敗しました。パスワードが正しいか確認してください。", Toast.LENGTH_LONG).show()
                }
            }
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
    // 画面の状態を管理
    var passwordText by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf("")
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val passwordManager = androidx.compose.runtime.remember { PasswordManager(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "PDF Unloker 設定",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
        )

        if (uri != null) {
            androidx.compose.material3.Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "対象ファイル: ${uri.lastPathSegment}")
                    Text(text = "解析が終わると自動的にPDFビューアーが起動します。", color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            Text(text = "給与明細などの共通パスワードを設定してください。")

            // パスワード入力フィールド
            androidx.compose.material3.OutlinedTextField(
                value = passwordText,
                onValueChange = { passwordText = it },
                label = { Text("共通パスワード") },
                visualTransformation = androidx.compose.runtime.remember {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            )

            // 保存ボタン
            androidx.compose.material3.Button(
                onClick = {
                    if (passwordText.isNotBlank()) {
                        passwordManager.savePassword(passwordText)
                        android.widget.Toast.makeText(context, "パスワードを保存しました", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            ) {
                Text("パスワードを保存")
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "使い方: この画面でパスワードを保存した後、Gmail等でPDFを開く際にこのアプリを選択してください。",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
    }
}