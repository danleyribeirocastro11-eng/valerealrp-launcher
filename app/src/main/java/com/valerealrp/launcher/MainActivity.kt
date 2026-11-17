package com.valerealrp.launcher

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Encontra os elementos da tela
        val btnJogar = findViewById<Button>(R.id.buttonJogar)
        val txtStatus = findViewById<TextView>(R.id.textViewStatus)

        // Define o texto inicial
        txtStatus.text = "Tudo atualizado (v1)"

        // Ação do botão Jogar
        btnJogar.setOnClickListener {
            try {
                // Tenta abrir o SA-MP Mobile
                val intent = packageManager.getLaunchIntentForPackage("ru.unisamp_mobile.game")
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "SA-MP Mobile não instalado!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Erro ao abrir o jogo.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
