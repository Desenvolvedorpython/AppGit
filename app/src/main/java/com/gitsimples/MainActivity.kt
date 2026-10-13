package com.gitsimples

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Screen() } }
    }

    /** Converte a pasta escolhida no seletor para um caminho real (só armazenamento interno). */
    private fun uriToPath(uri: Uri): String? {
        val parts = DocumentsContract.getTreeDocumentId(uri).split(":", limit = 2)
        if (parts[0] != "primary") return null
        return "/storage/emulated/0" + if (parts.size > 1 && parts[1].isNotEmpty()) "/${parts[1]}" else ""
    }

    private fun hasAllFilesAccess() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    @Composable
    fun Screen() {
        val prefs = remember { getSharedPreferences("cfg", MODE_PRIVATE) }
        var folder by remember { mutableStateOf(prefs.getString("folder", "") ?: "") }
        var url by remember { mutableStateOf(prefs.getString("url", "") ?: "") }
        var key by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
        var version by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var confirm by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val path = uriToPath(uri)
                if (path == null) status = "Escolha uma pasta do armazenamento interno."
                else { folder = path; prefs.edit().putString("folder", path).apply() }
            }
        }

        fun run(block: () -> String) {
            prefs.edit().putString("url", url).putString("key", key).apply()
            busy = true; status = "Trabalhando..."
            scope.launch {
                status = try { withContext(Dispatchers.IO) { block() } }
                catch (e: Exception) { "Erro: ${e.message ?: e.javaClass.simpleName}" }
                busy = false
            }
        }

        fun ask(action: String) {
            if (!hasAllFilesAccess()) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
                status = "Autorize o acesso a todos os arquivos e volte ao app."
                return
            }
            if (folder.isEmpty() || url.isEmpty() || key.isEmpty()) { status = "Preencha pasta, URL e key."; return }
            if (action == "send" && version.isBlank()) { status = "Digite o nome da versão."; return }
            confirm = action
        }

        Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Git Simples", style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = { picker.launch(null) }, Modifier.fillMaxWidth()) { Text("Selecionar pasta") }
            Text(if (folder.isEmpty()) "Nenhuma pasta selecionada" else folder)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("URL do diretório (https://github.com/usuario/repo.git)") })
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Key (token do GitHub)") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(version, { version = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Nome da versão enviada") })
            Button(onClick = { ask("send") }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Enviar projeto") }
            OutlinedButton(onClick = { ask("download") }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Baixar versão") }
            if (status.isNotEmpty()) Text(status)
        }

        confirm?.let { action ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                title = { Text(if (action == "send") "Enviar projeto pra nuvem?" else "Baixar versão?") },
                text = { if (action == "download") Text("Isso substitui os arquivos da pasta pela versão da nuvem. Mudanças locais não enviadas serão perdidas.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = null
                        val dir = File(folder)
                        if (action == "send") run { GitOps.send(dir, url.trim(), key.trim(), version.trim()) }
                        else run { GitOps.download(dir, url.trim(), key.trim()) }
                    }) { Text("Sim") }
                },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("Não") } }
            )
        }
    }
}
