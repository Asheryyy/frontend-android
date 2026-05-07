package com.example.app2

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

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var etEmail: EditText
    private lateinit var btnSendNewPassword: Button
    private lateinit var tvGoLogin: TextView
    private val client = getUnsafeOkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        etEmail = findViewById(R.id.etEmail)
        btnSendNewPassword = findViewById(R.id.btnSendNewPassword)
        tvGoLogin = findViewById(R.id.tvGoLogin)

        btnSendNewPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập email!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendNewPassword(email)
        }

        tvGoLogin.setOnClickListener { finish() }
    }

    private fun sendNewPassword(email: String) {
        btnSendNewPassword.isEnabled = false
        btnSendNewPassword.text = "Đang gửi..."

        val url = "${MainActivity.Config.BASE_URL}api/Auth/ForgotPassword"
        val json = """{"email": "$email"}"""
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("ngrok-skip-browser-warning", "69420")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Lỗi kết nối!", Toast.LENGTH_SHORT).show()
                    btnSendNewPassword.isEnabled = true
                    btnSendNewPassword.text = "GỬI MẬT KHẨU MỚI"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(applicationContext, "Mật khẩu mới đã gửi về email!", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        val msg = try { JSONObject(data ?: "").getString("message") }
                        catch (e: Exception) { "Thất bại!" }
                        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                        btnSendNewPassword.isEnabled = true
                        btnSendNewPassword.text = "GỬI MẬT KHẨU MỚI"
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