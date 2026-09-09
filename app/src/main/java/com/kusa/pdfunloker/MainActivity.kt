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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kusa.pdfunloker.ui.theme.PDFUnlokerTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var passwordManager: PasswordManager
    private val unlockedFile = mutableStateOf<File?>(null)
    private val showPasswordDialog = mutableStateOf(false)
    private val passwordDialogError = mutableStateOf<String?>(null)
    private val pendingUri = mutableStateOf<Uri?>(null)
    private val isUnlocking = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        passwordManager = PasswordManager(this)
        PDFBoxResourceLoader.init(applicationContext)

        intent?.let { handleIntent(it) }

        setContent {
            PDFUnlokerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val file by unlockedFile
                    val isDialogVisible by showPasswordDialog
                    val dialogError by passwordDialogError
                    val currentPendingUri by pendingUri
                    val isUnlockingState by isUnlocking

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

                        if (isDialogVisible && currentPendingUri != null) {
                            PasswordInputDialog(
                                errorMessage = dialogError,
                                isProcessing = isUnlockingState,
                                onConfirm = { inputPassword ->
                                    handlePdfIntent(currentPendingUri!!, inputPassword)
                                },
                                onDismiss = {
                                    showPasswordDialog.value = false
                                    passwordDialogError.value = null
                                    pendingUri.value = null
                                }
                            )
                        }
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

    private fun handlePdfIntent(uri: Uri, customPassword: String? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isUnlocking.value = true
                passwordDialogError.value = null
            }

            val tempInFile = File(cacheDir, "temp_input.pdf")
            try {
                // content:// URI から一度ローカルファイルにコピー（シーク可能にするため）
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    tempInFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // if (!tempInFile.exists()) throw Exception("ファイルのコピーに失敗しました")
                if (!tempInFile.exists()) throw Exception(getString(R.string.error_file_copy_failed))

                var document: PDDocument? = null
                if (customPassword != null) {
                    // 個別入力されたパスワードを使用
                    document = PDDocument.load(tempInFile, customPassword)
                } else {
                    // まずはパスワードなしで試行
                    try {
                        document = PDDocument.load(tempInFile)
                    } catch (e: InvalidPasswordException) {
                        // パスワードが必要な場合、保存された共通パスワードを使用
                        val savedPassword = passwordManager.getPassword()
                        if (savedPassword != null) {
                            document = PDDocument.load(tempInFile, savedPassword)
                        } else {
                            // 保存された共通パスワードが無い場合、個別パスワード入力ダイアログを表示
                            withContext(Dispatchers.Main) {
                                pendingUri.value = uri
                                showPasswordDialog.value = true
                                isUnlocking.value = false
                            }
                            return@launch
                        }
                    }
                }

                document?.use { doc ->
                    if (doc.isEncrypted) {
                        doc.isAllSecurityToBeRemoved = true
                        // Android標準の PdfRenderer 対策: PDFのトレイラーから /Encrypt キーを消去する
                        doc.document.trailer.removeItem(COSName.ENCRYPT)
                    }

                    val outputFile = File(cacheDir, UNLOCKED_CACHE_FILE)
                    doc.save(outputFile)
                    
                    withContext(Dispatchers.Main) {
                        showPasswordDialog.value = false
                        passwordDialogError.value = null
                        pendingUri.value = null
                        isUnlocking.value = false

                        // unlockedFileをnullにしてからセットすることで、Composeに再描画を促す
                        unlockedFile.value = null
                        unlockedFile.value = outputFile
                    }
                }
            } catch (e: InvalidPasswordException) {
                withContext(Dispatchers.Main) {
                    isUnlocking.value = false
                    pendingUri.value = uri
                    passwordDialogError.value = getString(R.string.toast_incorrect_password)
                    showPasswordDialog.value = true
                    // Toast.makeText(this@MainActivity, getString(R.string.toast_incorrect_password), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isUnlocking.value = false
                    android.util.Log.e("PDF_UNLOCK", "解除失敗", e)
                    // Toast.makeText(this@MainActivity, "PDFの処理に失敗しました: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    Toast.makeText(this@MainActivity, getString(R.string.toast_pdf_process_failed, e.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
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
fun PasswordInputDialog(
    errorMessage: String?,
    isProcessing: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dialog_title_enter_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.dialog_msg_enter_password))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text(stringResource(R.string.label_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passwordInput) },
                enabled = passwordInput.isNotBlank() && !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(text = stringResource(R.string.btn_unlock))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) {
                Text(text = stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
fun PdfViewerScreen(file: File, onClose: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onClose)

    /* 従来実装
    val pfd = remember(file) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
    val renderer = remember(pfd) { PdfRenderer(pfd) }
    DisposableEffect(pfd, renderer) {
        onDispose {
            renderer.close()
            pfd.close()
        }
    }
    */

    var pfdAndRenderer by remember(file) {
        mutableStateOf<Pair<ParcelFileDescriptor, PdfRenderer>?>(null)
    }
    var renderError by remember(file) { mutableStateOf<String?>(null) }

    DisposableEffect(file) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            pfdAndRenderer = Pair(pfd, renderer)
        } catch (e: Exception) {
            android.util.Log.e("PdfViewerScreen", "PdfRenderer initialization failed", e)
            renderError = e.localizedMessage ?: "Failed to initialize PDF renderer"
        }

        onDispose {
            pfdAndRenderer?.let { (pfd, renderer) ->
                try {
                    renderer.close()
                } catch (_: Exception) {}
                try {
                    pfd.close()
                } catch (_: Exception) {}
            }
        }
    }

    if (renderError != null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.toast_pdf_process_failed, renderError!!),
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onClose) {
                Text(stringResource(R.string.btn_close))
            }
        }
    } else if (pfdAndRenderer != null) {
        val renderer = pfdAndRenderer!!.second
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
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
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
            // contentDescription = "Page ${pageIndex + 1}",
            contentDescription = stringResource(R.string.cd_pdf_page, pageIndex + 1),
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
            // text = "PDF Unloker 設定",
            text = stringResource(R.string.title_settings),
            style = MaterialTheme.typography.headlineMedium
        )

        if (uri != null) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Text(text = "対象ファイル: ${uri.lastPathSegment}")
                    Text(text = stringResource(R.string.target_file, uri.lastPathSegment ?: ""))
                    Text(
                        // text = "解析が終わると自動的にPDFビューアーが起動します。",
                        text = stringResource(R.string.msg_auto_launch_viewer),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            // Text(text = "給与明細などの共通パスワードを設定してください。")
            Text(text = stringResource(R.string.msg_set_common_password))

            OutlinedTextField(
                value = passwordText,
                onValueChange = { passwordText = it },
                // label = { Text("共通パスワード") },
                label = { Text(stringResource(R.string.label_common_password)) },
                visualTransformation = remember { PasswordVisualTransformation() },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (passwordText.isNotBlank()) {
                        passwordManager.savePassword(passwordText)
                        // Toast.makeText(context, "パスワードを保存しました", Toast.LENGTH_SHORT).show()
                        Toast.makeText(context, context.getString(R.string.toast_password_saved), Toast.LENGTH_SHORT).show()
                        (context as? Activity)?.finish()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                // Text("パスワードを保存")
                Text(stringResource(R.string.btn_save_password))
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                // text = "使い方: この画面でパスワードを保存した後、Gmail等でPDFを開く際にこのアプリを選択してください。",
                text = stringResource(R.string.msg_usage_instructions),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
