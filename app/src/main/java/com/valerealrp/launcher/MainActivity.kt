package com.valerealrp.launcher

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var btnJogar: Button
    private lateinit var txtStatus: TextView
    private val ZIP_URL = "https://github.com/danleyribeirocastro11-eng/valerealrp-launcher/releases/download/v1/arquivos.zip"
    private val IP_SERVIDOR = "127.0.0.1" // Troque pelo IP do seu host depois!
    private val PORTA_SERVIDOR = 7777

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnJogar = findViewById(R.id.buttonJogar)
        // Se você tirou o TextView do layout, comente a linha abaixo:
        txtStatus = findViewById(R.id.textViewStatus) 

        btnJogar.setOnClickListener {
            val pastaJogo = File(Environment.getExternalStorageDirectory(), "Android/data/ru.unisamp_mobile.game/files/")
            
            // Verifica se o jogo já foi baixado (se a pasta existe e não está vazia)
            if (pastaJogo.exists() && pastaJogo.listFiles()?.isNotEmpty() == true) {
                abrirJogo()
            } else {
                baixarJogo()
            }
        }
    }

    private fun baixarJogo() {
        txtStatus.text = "Iniciando download..."
        val request = DownloadManager.Request(Uri.parse(ZIP_URL))
        request.setTitle("Baixando Vale Real RP")
        request.setDescription("Aguarde, baixando os arquivos do jogo...")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "valereal.zip")

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Fica ouvindo até o download terminar
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                txtStatus.text = "Extraindo arquivos..."
                val arquivoZip = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "valereal.zip")
                extrairZip(arquivoZip)
                unregisterReceiver(this)
            }
        }
        registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun extrairZip(zipFile: File) {
        val targetDir = File(Environment.getExternalStorageDirectory(), "Android/data/ru.unisamp_mobile.game/files/")
        if (!targetDir.exists()) targetDir.mkdirs()

        try {
            val zis = ZipInputStream(zipFile.inputStream())
            var entry = zis.nextEntry
            val buffer = ByteArray(1024)

            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    val fout = FileOutputStream(newFile)
                    var count: Int
                    while (zis.read(buffer).also { count = it } != -1) {
                        fout.write(buffer, 0, count)
                    }
                    fout.close()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
            txtStatus.text = "Download concluído! Clique em JOGAR."
            Toast.makeText(this, "Mods instalados com sucesso!", Toast.LENGTH_LONG).show()
            zipFile.delete() // Apaga o zip depois de extrair para não ocupar espaço
        } catch (e: Exception) {
            txtStatus.text = "Erro ao extrair arquivos."
            e.printStackTrace()
        }
    }

    private fun abrirJogo() {
        try {
            // Tenta abrir o SA-MP Mobile passando o IP e a Porta
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("samp://$IP_SERVIDOR:$PORTA_SERVIDOR"))
            intent.setPackage("ru.unisamp_mobile.game")
            startActivity(intent)
        } catch (e: Exception) {
            // Se o SA-MP não suportar o link direto, tenta abrir o app normal
            val intent = packageManager.getLaunchIntentForPackage("ru.unisamp_mobile.game")
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "SA-MP Mobile não instalado!", Toast.LENGTH_LONG).show()
            }
        }
    }
}
