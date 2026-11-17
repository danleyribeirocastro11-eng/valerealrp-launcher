package com.valerealrp.launcher

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var btnJogar: Button
    private val ZIP_URL = "https://github.com/danleyribeirocastro11-eng/valerealrp-launcher/releases/download/v1/arquivos.zip"
    private val IP_SERVIDOR = "127.0.0.1" // Troque pelo IP real do seu servidor depois!
    private val PORTA_SERVIDOR = 7777
    private val PERMISSAO_ARMAZENAMENTO = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnJogar = findViewById(R.id.buttonJogar)

        // Pede permissão de armazenamento (necessário para Android antigo)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSAO_ARMAZENAMENTO)
        }

        btnJogar.setOnClickListener {
            val pastaJogo = File(Environment.getExternalStorageDirectory(), "Android/data/ru.unisamp_mobile.game/files/")
            
            // Se o jogo já foi baixado, abre direto
            if (pastaJogo.exists() && pastaJogo.listFiles()?.isNotEmpty() == true) {
                abrirJogo()
            } else {
                // Se não, inicia o download
                baixarJogo()
            }
        }
    }

    private fun baixarJogo() {
        Toast.makeText(this, "Iniciando download do Vale Real RP...", Toast.LENGTH_LONG).show()
        btnJogar.text = "BAIXANDO..."

        val request = DownloadManager.Request(Uri.parse(ZIP_URL))
        request.setTitle("Vale Real RP")
        request.setDescription("Baixando arquivos do jogo...")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "valereal.zip")

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        // Fica ouvindo até o download terminar
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Toast.makeText(context, "Download concluído! Extraindo...", Toast.LENGTH_LONG).show()
                btnJogar.text = "EXTRAINDO..."
                
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
            
            btnJogar.text = "JOGAR"
            Toast.makeText(this, "Tudo pronto! Clique em JOGAR.", Toast.LENGTH_LONG).show()
            zipFile.delete() // Apaga o zip para não ocupar espaço
            
        } catch (e: Exception) {
            btnJogar.text = "JOGAR"
            Toast.makeText(this, "Erro ao extrair. Verifique o espaço.", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun abrirJogo() {
        try {
            // Tenta abrir o SA-MP Mobile passando o IP e a Porta via Intent
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("samp://$IP_SERVIDOR:$PORTA_SERVIDOR"))
            intent.setPackage("ru.unisamp_mobile.game")
            startActivity(intent)
        } catch (e: Exception) {
            // Se falhar, tenta abrir o app do SA-MP normal
            val intent = packageManager.getLaunchIntentForPackage("ru.unisamp_mobile.game")
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "SA-MP Mobile não instalado!", Toast.LENGTH_LONG).show()
            }
        }
    }
}
