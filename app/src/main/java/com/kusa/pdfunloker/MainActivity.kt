package com.kusa.pdfunloker

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kusa.pdfunloker.ui.theme.PDFUnlokerTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var passwordManager: PasswordManager
    private val unlockedFile = mutableStateOf<File?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        passwordManager = PasswordManager(this)
        PDFBoxResourceLoader.init(applicationContext)

        intent?.let { handleIntent(it) }

        setContent {
            PDFUnlokerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val file by unlockedFile
                    if (file != null) {
                        PdfViewerScreen(
                            file = file!!,
                            onClose = { closeViewer() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        MainScreen(
                            uri = intent.data,
                            passwordManager = passwordManager,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        File(cacheDir, UNLOCKED_CACHE_FILE).delete()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val intentData: Uri? = intent.data
        if (intent.action == Intent.ACTION_VIEW && intentData != null) {
            handlePdfIntent(intentData)
        }
    }

    private fun closeViewer() {
        File(cacheDir, UNLOCKED_CACHE_FILE).delete()
        unlockedFile.value = null
    }

    private fun handlePdfIntent(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val tempInFile = File(cacheDir, "temp_input.pdf")
            try {
                // content:// URI から一度ローカルファイルにコピー（シーク可能にするため）
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    tempInFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (!tempInFile.exists()) throw Exception("ファイルのコピーに失敗しました")

                // まずはパスワードなしで試行
                var document: PDDocument? = null
                try {
                    document = PDDocument.load(tempInFile)
                } catch (e: InvalidPasswordException) {
                    // パスワードが必要な場合、保存されたパスワードを使用
                    val savedPassword = passwordManager.getPassword()
                    if (savedPassword == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "このファイルは保護されています。パスワードを設定してください。", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    document = PDDocument.load(tempInFile, savedPassword)
                }

                document?.use { doc ->
                    if (doc.isEncrypted) {
                        doc.setAllSecurityToBeRemoved(true)
                    }

                    val outputFile = File(cacheDir, UNLOCKED_CACHE_FILE)
                    doc.save(outputFile)
                    
                    withContext(Dispatchers.Main) {
                        // unlockedFileをnullにしてからセットすることで、Composeに再描画を促す
                        unlockedFile.value = null
                        unlockedFile.value = outputFile
                    }
                }
            } catch (e: InvalidPasswordException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "パスワードが正しくありません。設定を確認してください。", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.util.Log.e("PDF_UNLOCK", "解除失敗", e)
                    Toast.makeText(this@MainActivity, "PDFの処理に失敗しました: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                if (tempInFile.exists()) tempInFile.delete()
            }
        }
    }

    companion object {
        private const val UNLOCKED_CACHE_FILE = "unlocked_preview.pdf"
    }
}

@Composable
fun PdfViewerScreen(file: File, onClose: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onClose)

    // file を key にすることで、ファイルが変わった際に再初期化されるようにする
    val pfd = remember(file) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
    val renderer = remember(pfd) { PdfRenderer(pfd) }
    DisposableEffect(pfd, renderer) {
        onDispose {
            renderer.close()
            pfd.close()
        }
    }

    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx().toInt()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF424242))
    ) {
        items(renderer.pageCount) { index ->
            PdfPageItem(
                renderer = renderer,
                pageIndex = index,
                targetWidthPx = screenWidthPx,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
fun PdfPageItem(
    renderer: PdfRenderer,
    pageIndex: Int,
    targetWidthPx: Int,
    modifier: Modifier = Modifier
) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            synchronized(renderer) {
                val page = renderer.openPage(pageIndex)
                val scale = targetWidthPx / page.width.toFloat()
                val bmp = Bitmap.createBitmap(
                    targetWidthPx,
                    (page.height * scale).toInt(),
                    Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bmp.asImageBitmap()
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = "Page ${pageIndex + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = modifier.fillMaxWidth()
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.414f), // A4比率
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
fun MainScreen(uri: Uri?, passwordManager: PasswordManager, modifier: Modifier = Modifier) {
    var passwordText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "PDF Unloker 設定",
            style = MaterialTheme.typography.headlineMedium
        )

        if (uri != null) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "対象ファイル: ${uri.lastPathSegment}")
                    Text(
                        text = "解析が終わると自動的にPDFビューアーが起動します。",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Text(text = "給与明細などの共通パスワードを設定してください。")

            OutlinedTextField(
                value = passwordText,
                onValueChange = { passwordText = it },
                label = { Text("共通パスワード") },
                visualTransformation = remember { PasswordVisualTransformation() },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (passwordText.isNotBlank()) {
                        passwordManager.savePassword(passwordText)
                        Toast.makeText(context, "パスワードを保存しました", Toast.LENGTH_SHORT).show()
                        (context as? Activity)?.finish()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("パスワードを保存")
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "使い方: この画面でパスワードを保存した後、Gmail等でPDFを開く際にこのアプリを選択してください。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
