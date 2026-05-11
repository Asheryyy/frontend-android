package com.example.app2

import android.os.Bundle
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

class PumpHistoryActivity : AppCompatActivity() {

    private lateinit var listPumpHistory: ListView
    private val client = getUnsafeOkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pump_history)

        listPumpHistory = findViewById(R.id.listPumpHistory)
        fetchPumpHistory()
    }

    private fun fetchPumpHistory() {
        val url = "${MainActivity.Config.BASE_URL}api/Pump/History"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Lỗi kết nối!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string()
                if (response.isSuccessful && data != null) {
                    val jsonArray = JSONArray(data)
                    val items = mutableListOf<String>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val action = obj.optString("action", "?")
                        val source = obj.optString("source", "?")
                        val rawTime = obj.optString("timestamp", "?")
                        val time = if (rawTime.length >= 19) {
                            rawTime.substring(0, 19).replace("T", " ")
                        } else {
                            rawTime
                        }

                        val icon = if (action == "BẬT") "💧" else "🛑"
                        val sourceIcon = if (source == "Thủ công") "🖐️" else "🤖"
                        items.add("$icon $action  |  $sourceIcon $source  |  $time")
                    }

                    runOnUiThread {
                        val adapter = ArrayAdapter(
                            applicationContext,
                            android.R.layout.simple_list_item_1,
                            items
                        )
                        listPumpHistory.adapter = adapter
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