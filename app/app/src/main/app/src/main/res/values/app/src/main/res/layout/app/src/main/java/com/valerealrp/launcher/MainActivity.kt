package com.valerealrp.launcher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/** Estrutura do version.json */
data class VersionInfo(
    val version: Int = 0,
    val zipUrl: String = "",
    val serverIp: String = "",
    val serverPort: Int = 7777
)

class MainActivity : AppCompatActivity() {

    companion object {
        // ====== CONFIGURE AQUI ======
        const val VERSION_URL = "https://danleyribeirocastro11-eng.github.io/valerealrp-launcher/version.json"
        // Confirme o pacote do cliente instalado no seu celular
        const val SAMP_PACKAGE = "ru.unisamp_mobile.game"
        // ============================

        private const val PREFS = "launcher_prefs"
        private const val KEY_VERSION = "local_version"
        private const val KEY_IP = "server_ip"
        private const val KEY_PORT = "server_port"
    }

    private val client = OkHttpClient()
    private val gson = Gson()
    private lateinit var prefs: SharedPreferences

    private lateinit var tvStatus: TextView
    private lateinit var tvPercent: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnPlay: Button

    // Quando true, o botão serve para tentar novamente em vez de jogar
    private var retryMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        tvStatus = findViewById(R.id.tvStatus)
        tvPercent = findViewById(R.id.tvPercent)
        progressBar = findViewById(R.id.progressBar)
        btnPlay = findViewById(R.id.btnPlay)

        btnPlay.setOnClickListener {
            if (retryMode) startCheck() else playGame()
        }

        startCheck()
    }

    /** Fluxo principal: verifica versão, baixa e extrai se necessário. */
    private fun startCheck() {
        retryMode = false
        btnPlay.isEnabled = false
        btnPlay.text = "JOGAR"
        progressBar.visibility = View.GONE
        tvPercent.visibility = View.GONE
        tvStatus.text = "Verificando atualizações..."

        lifecycleScope.launch {
            try {
                val info = fetchVersionInfo()
                prefs.edit()
                    .putString(KEY_IP, info.serverIp)
                    .putInt(KEY_PORT, info.serverPort)
                    .apply()

                val localVersion = prefs.getInt(KEY_VERSION, 0)

                if (info.version > localVersion) {
                    tvStatus.text = "Baixando atualização (v${info.version})..."
                    progressBar.progress = 0
                    progressBar.visibility = View.VISIBLE
                    tvPercent.text = "0%"
                    tvPercent.visibility = View.VISIBLE

                    val tmpZip = File(cacheDir, "update.zip")
                    downloadZip(info.zipUrl, tmpZip)

                    tvStatus.text = "Extraindo arquivos..."
                    val destDir = getExternalFilesDir(null) ?: filesDir
                    unzip(tmpZip, destDir)
                    tmpZip.delete()

                    prefs.edit().putInt(KEY_VERSION, info.version).apply()
                    progressBar.visibility = View.GONE
                    tvPercent.visibility = View.GONE
                }

                tvStatus.text = "Tudo atualizado (v${prefs.getInt(KEY_VERSION, 0)})"
                btnPlay.isEnabled = true

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                tvPercent.visibility = View.GONE
                if (prefs.getInt(KEY_VERSION, 0) > 0) {
                    tvStatus.text = "Sem conexão com o servidor de updates. Usando versão local."
                    btnPlay.isEnabled = true
                } else {
                    tvStatus.text = "Erro: ${e.message}"
                    retryMode = true
                    btnPlay.text = "TENTAR NOVAMENTE"
                    btnPlay.isEnabled = true
                }
            }
        }
    }

    /** GET do version.json (thread IO). */
    private suspend fun fetchVersionInfo(): VersionInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(VERSION_URL).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} ao buscar version.json")
            val body = resp.body?.string() ?: throw IOException("Resposta vazia")
            gson.fromJson(body, VersionInfo::class.java)
                ?: throw IOException("JSON inválido")
        }
    }

    /** Baixa o ZIP atualizando a barra de progresso (0-100%). */
    private suspend fun downloadZip(url: String, dest: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} ao baixar o zip")
            val body = resp.body ?: throw IOException("Download vazio")
            val total = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var lastPercent = -1
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = (downloaded * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                runOnUiThread {
                                    progressBar.progress = percent
                                    tvPercent.text = "$percent%"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Extrai o ZIP com proteção contra Zip Slip. */
    private suspend fun unzip(zipFile: File, destDir: File) = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        val destPath = destDir.canonicalPath
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (!outFile.canonicalPath.startsWith(destPath + File.separator)) {
                    throw SecurityException("Entrada inválida no zip: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /** Abre o cliente SA-MP Mobile passando IP e porta. */
    private fun playGame() {
        val intent = packageManager.getLaunchIntentForPackage(SAMP_PACKAGE)
        if (intent == null) {
            AlertDialog.Builder(this)
                .setTitle("Cliente não instalado")
                .setMessage("Instale o SA-MP Mobile ($SAMP_PACKAGE) para jogar no Vale Real RP.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val ip = prefs.getString(KEY_IP, "") ?: ""
        val port = prefs.getInt(KEY_PORT, 7777)
        intent.putExtra("ip", ip)
        intent.putExtra("port", port)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
