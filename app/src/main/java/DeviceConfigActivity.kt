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

class DeviceConfigActivity : AppCompatActivity() {
    private lateinit var etDeviceName: EditText
    private lateinit var etLowerThreshold: EditText
    private lateinit var etUpperThreshold: EditText
    private lateinit var btnSaveConfig: Button
    private lateinit var tvGoBack: TextView
    private val client = getUnsafeOkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_config)

        etDeviceName = findViewById(R.id.etDeviceName)
        etLowerThreshold = findViewById(R.id.etLowerThreshold)
        etUpperThreshold = findViewById(R.id.etUpperThreshold)
        btnSaveConfig = findViewById(R.id.btnSaveConfig)
        tvGoBack = findViewById(R.id.tvGoBack)

        val token = intent.getStringExtra("TOKEN") ?: ""

        // Load config hiện tại
        loadCurrentConfig()

        btnSaveConfig.setOnClickListener {
            val deviceName = etDeviceName.text.toString().trim()
            val lower = etLowerThreshold.text.toString().trim()
            val upper = etUpperThreshold.text.toString().trim()

            if (deviceName.isEmpty() || lower.isEmpty() || upper.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (lower.toFloat() >= upper.toFloat()) {
                Toast.makeText(this, "Ngưỡng thấp phải nhỏ hơn ngưỡng cao!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveConfig(token, deviceName, lower.toFloat(), upper.toFloat())
        }

        tvGoBack.setOnClickListener { finish() }
    }

    private fun loadCurrentConfig() {
        val deviceName = "ESP32_Wokwi_Tai"
        val url = "${MainActivity.Config.BASE_URL}api/device/config?deviceName=$deviceName"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string() ?: return
                if (response.isSuccessful) {
                    val json = JSONObject(data)
                    runOnUiThread {
                        etDeviceName.setText(json.optString("deviceName", "ESP32_Wokwi_Tai"))
                        etLowerThreshold.setText(json.optDouble("lowerThreshold", 30.0).toString())
                        etUpperThreshold.setText(json.optDouble("upperThreshold", 80.0).toString())
                    }
                }
            }
        })
    }

    private fun saveConfig(token: String, deviceName: String, lower: Float, upper: Float) {
        val url = "${MainActivity.Config.BASE_URL}api/device/config"
        val json = """{"deviceName": "$deviceName", "lowerThreshold": $lower, "upperThreshold": $upper}"""
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .put(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Lỗi kết nối!", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(applicationContext, "Lưu cài đặt thành công!", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        val msg = try { JSONObject(data ?: "").getString("message") }
                        catch (e: Exception) { "Lưu thất bại!" }
                        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
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