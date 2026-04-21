package com.example.app2

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var listHistory: ListView
    private val client = getUnsafeOkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        listHistory = findViewById(R.id.listHistory)

        // Nhận token từ MainActivity
        val token = intent.getStringExtra("TOKEN") ?: ""
        fetchHistory(token)
    }

    private fun fetchHistory(token: String) {
        Log.d("HISTORY", "Bắt đầu fetch, token length: ${token.length}")
        Log.d("HISTORY", "URL: ${MainActivity.Config.BASE_URL}api/Humidity/history")

        val url = "${MainActivity.Config.BASE_URL}api/Humidity/history"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("ngrok-skip-browser-warning", "69420")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HISTORY", "Lỗi: ${e.message}") // ← Thêm
                runOnUiThread {
                    Toast.makeText(applicationContext, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("HISTORY", "Response code: ${response.code}") // ← Thêm
                val data = response.body?.string()
                Log.d("HISTORY", "Data: $data") // ← Thêm
                // ... rest of code
                if (response.isSuccessful && data != null) {
                    val jsonArray = JSONArray(data)
                    val items = mutableListOf<String>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val value = obj.optDouble("value", 0.0)
                        val device = obj.optString("deviceName", "?")
                        val time = obj.optString("timestamp", "?")
                            .substring(0, 19)
                            .replace("T", " ")
                        items.add("💧 $value%  |  $device  |  $time")
                    }

                    runOnUiThread {
                        val adapter = ArrayAdapter(
                            applicationContext,
                            android.R.layout.simple_list_item_1,
                            items
                        )
                        listHistory.adapter = adapter
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