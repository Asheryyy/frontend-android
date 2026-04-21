package com.example.app2

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

class RegisterActivity : AppCompatActivity() {
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvGoLogin: TextView
    private val client = getUnsafeOkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etEmail = findViewById(R.id.etEmail)
        btnRegister = findViewById(R.id.btnRegister)
        tvGoLogin = findViewById(R.id.tvGoLogin)

        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val email = etEmail.text.toString().trim()

            if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performRegister(username, password, email)
        }

        tvGoLogin.setOnClickListener {
            finish() // Quay lại LoginActivity
        }
    }

    private fun performRegister(username: String, password: String, email: String) {
        val url = "${MainActivity.Config.BASE_URL}api/Auth/Register"
        val json = """{"username": "$username", "password": "$password", "email": "$email"}"""
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("ngrok-skip-browser-warning", "69420")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "Lỗi kết nối!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@RegisterActivity, "Đăng ký thành công! Vui lòng đăng nhập.", Toast.LENGTH_LONG).show()
                        finish() // Quay lại LoginActivity
                    } else {
                        val errorMsg = try {
                            JSONObject(data ?: "").getString("message")
                        } catch (e: Exception) { "Đăng ký thất bại!" }
                        Toast.makeText(this@RegisterActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun getUnsafeOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
}