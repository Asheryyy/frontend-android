package com.example.app2

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
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
    private lateinit var etOtp: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var btnVerifyOtp: Button
    private lateinit var btnRegister: Button
    private lateinit var tvGoLogin: TextView
    private lateinit var tvCountdown: TextView

    private var isOtpVerified = false
    private var countDownTimer: CountDownTimer? = null
    private val client = getUnsafeOkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etEmail = findViewById(R.id.etEmail)
        etOtp = findViewById(R.id.etOtp)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)
        btnRegister = findViewById(R.id.btnRegister)
        tvGoLogin = findViewById(R.id.tvGoLogin)
        tvCountdown = findViewById(R.id.tvCountdown)

        // Gửi OTP
        btnSendOtp.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập email!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendOtp(email)
        }

        // Xác thực OTP
        btnVerifyOtp.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val otp = etOtp.text.toString().trim()
            if (otp.length != 6) {
                Toast.makeText(this, "OTP phải có 6 số!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyOtp(email, otp)
        }

        // Đăng ký
        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val email = etEmail.text.toString().trim()

            if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isOtpVerified) {
                Toast.makeText(this, "Vui lòng xác thực OTP trước!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performRegister(username, password, email)
        }

        tvGoLogin.setOnClickListener { finish() }
    }

    private fun sendOtp(email: String) {
        btnSendOtp.isEnabled = false
        btnSendOtp.text = "Đang gửi..."

        val url = "${MainActivity.Config.BASE_URL}api/Otp/Send"
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
                    Toast.makeText(this@RegisterActivity, "Lỗi gửi OTP!", Toast.LENGTH_SHORT).show()
                    btnSendOtp.isEnabled = true
                    btnSendOtp.text = "Gửi OTP"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@RegisterActivity, "OTP đã gửi về email!", Toast.LENGTH_LONG).show()
                        startCountdown() // Bắt đầu đếm ngược 10 phút
                    } else {
                        Toast.makeText(this@RegisterActivity, "Gửi OTP thất bại!", Toast.LENGTH_SHORT).show()
                        btnSendOtp.isEnabled = true
                        btnSendOtp.text = "Gửi OTP"
                    }
                }
            }
        })
    }

    private fun startCountdown() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(600000, 1000) { // 10 phút
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                tvCountdown.text = "OTP hết hạn sau: ${minutes}:${String.format("%02d", seconds)}"
            }

            override fun onFinish() {
                tvCountdown.text = "OTP đã hết hạn! Vui lòng gửi lại."
                btnSendOtp.isEnabled = true
                btnSendOtp.text = "Gửi lại OTP"
            }
        }.start()
    }

    private fun verifyOtp(email: String, otp: String) {
        val url = "${MainActivity.Config.BASE_URL}api/Otp/Verify"
        val json = """{"email": "$email", "otpCode": "$otp"}"""
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
                runOnUiThread {
                    if (response.isSuccessful) {
                        isOtpVerified = true
                        countDownTimer?.cancel()
                        tvCountdown.text = "✅ OTP xác thực thành công!"
                        tvCountdown.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                        btnVerifyOtp.isEnabled = false
                        btnRegister.isEnabled = true // Mở khóa nút đăng ký
                        Toast.makeText(this@RegisterActivity, "Xác thực thành công!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@RegisterActivity, "OTP sai hoặc hết hạn!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
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
                        Toast.makeText(applicationContext, "Đăng ký thành công!", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        // ← Sửa chỗ này
                        val msg = try {
                            JSONObject(data ?: "").getString("message")
                        } catch (e: Exception) {
                            data ?: "Đăng ký thất bại!" // ← Nếu không phải JSON thì dùng thẳng string
                        }
                        Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
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