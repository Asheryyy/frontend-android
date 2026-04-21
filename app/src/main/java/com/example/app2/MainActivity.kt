package com.example.app2

import android.content.Intent
import android.os.Bundle
import android.util.Log // Nhớ thêm cái này để soi lỗi
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.app2.ui.theme.HumidityDto
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {
    private lateinit var txtData: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnWater: Button
    private var isPumping = false
    private lateinit var btnHistory: Button // ← THÊM DÒNG NÀY

    private var globalToken: String = ""
    private val client = getUnsafeOkHttpClient()
    private var entryIndex = 0f  // ← Thêm
    private lateinit var lineChart: LineChart  // ← THÊM
    private val chartEntries = mutableListOf<Entry>()
    private val chartLabels = mutableListOf<String>()
    private val WEATHER_API_KEY = "YOUR_API_KEY_HERE" // ← Paste key vào đây
    private val weatherEntries = mutableListOf<Entry>()
    private val weatherLabels = mutableListOf<String>()
    private var weatherIndex = 0f

    object Config {
        const val BASE_URL = "https://api-tuoi-cay-g0g2cdfmbkc7dubq.southeastasia-01.azurewebsites.net/"
    }

    // --- CHỖ SỬA 1: Khai báo SignalR Hub ---
    private lateinit var hubConnection: HubConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtData = findViewById(R.id.txtData)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnWater = findViewById(R.id.btnWater)
        btnHistory = findViewById(R.id.btnHistory) // ← THÊM DÒNG NÀY
        lineChart = findViewById(R.id.lineChart)  // ← Thêm

        setupSignalR()
        globalToken = intent.getStringExtra("TOKEN") ?: ""
        setupChart()
        loadWeatherHistory() // ← Load lịch sử khi mở app
        fetchWeatherAndUpdateChart() // ← Fetch nhiệt độ mới nhất

        btnWater.setOnClickListener {
            val status = if (!isPumping) 1 else 0
            if (globalToken.isEmpty()) {
                performLogin {
                    sendPumpControl(status)
                    isPumping = !isPumping
                }
            } else {
                sendPumpControl(status)
                isPumping = !isPumping
            }
        }

        // ← THÊM ĐOẠN NÀY
        btnHistory.setOnClickListener {
            Log.d("HISTORY", "Bấm nút lịch sử!")
            val intent = Intent(this, HistoryActivity::class.java)
            intent.putExtra("TOKEN", globalToken)
            startActivity(intent)
        }
        // Fetch ngay khi mở app
        fetchWeatherAndUpdateChart()

// Lặp lại mỗi 1 giờ = 3_600_000ms
        val handler = android.os.Handler(mainLooper)
        val weatherRunnable = object : Runnable {
            override fun run() {
                fetchWeatherAndUpdateChart()
                handler.postDelayed(this, 3_600_000L) // 1 tiếng
            }
        }
        handler.postDelayed(weatherRunnable, 3_600_000L)
    }
    private fun setupChart() {
        lineChart.apply {
            description.text = "Độ ẩm theo thời gian"
            description.textSize = 12f
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            animateX(500)
            axisLeft.apply {
                axisMinimum = 20f  // Nhiệt độ HCM thường từ 20°C
                axisMaximum = 45f  // đến 45°C
                setDrawGridLines(true)
            }
            xAxis.apply {
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index >= 0 && index < chartLabels.size)
                            chartLabels[index] else ""
                    }
                }
                granularity = 1f
                labelRotationAngle = -45f
            }

            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                setDrawGridLines(true)
            }
            axisRight.isEnabled = false
        }
    }

    // --- CHỖ SỬA 3: Hàm thiết lập SignalR ---
    private fun setupSignalR() {
        hubConnection = HubConnectionBuilder
            .create("${Config.BASE_URL}humidityHub")
            .build()

        hubConnection.on("ReceiveHumidityUpdate", { data ->
            runOnUiThread {
                txtData.text = "📢 [REAL-TIME]\n💧 Độ ẩm mới: ${data.value}%"
                Log.d("SIGNALR", "Đã nhận độ ẩm: ${data.value}")
            }
            addChartEntry(data.value.toFloat())  // ← THÊM DÒNG NÀY
        }, HumidityDto.HumidityDto::class.java)
        hubConnection.on("ReceiveAutoLog", { message ->
            if (message.isNotEmpty()) {
                runOnUiThread {
                    // Hiển thị một cái Toast hoặc cập nhật vào một TextView thông báo
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    txtData.text = "🤖 LOG: $message\n" + txtData.text
                }
            }
        }, String::class.java)

        Thread {
            try {
                hubConnection.start().blockingAwait()
                Log.d("SIGNALR", "Đã thông nòng SignalR thành công!")
            } catch (e: Exception) {
                Log.e("SIGNALR", "Lỗi SignalR: ${e.message}")
            }
        }.start()
    }

    // --- CÁC HÀM CŨ CỦA MÀY GIỮ NGUYÊN ---
    private fun performLogin(onSuccess: (() -> Unit)? = null) {
        val url = "${Config.BASE_URL}api/Auth/Login"
        val json = """{"userName": "tai", "password": "1234"}"""
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { txtData.text = "Lỗi kết nối Login: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (response.isSuccessful && responseData != null) {
                    val jsonObject = JSONObject(responseData)
                    globalToken = jsonObject.getString("token")
                    runOnUiThread {
                        txtData.text = "Đăng nhập OK! Đang đợi dữ liệu real-time..."
                        onSuccess?.invoke() // ✅ Gọi callback sau khi có token
                    }
                    fetchCheckData(globalToken)
                }
            }
        })
    }

    private fun fetchCheckData(token: String) {
        val url = "${Config.BASE_URL}api/CheckData"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token") // <--- NÓ NẰM Ở ĐÂY NÈ
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { txtData.text = "Lỗi lấy data: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string()
                Log.d("WEATHER", "Full response: $data") // ← Xem response thật là gì
                if (response.isSuccessful && data != null) {
                    runOnUiThread {
                        val jsonArray = JSONArray(data)
                        if (jsonArray.length() > 0) {
                            val item = jsonArray.getJSONObject(0)
                            val temp = item.optDouble("temperature", 0.0)
                            val humi = item.optInt("humidity", 0)
                            txtData.text = "🌡️ Nhiệt độ: $temp°C\n💧 Độ ẩm: $humi%"
                        }
                    }
                }
            }
        })
    }


    private fun sendPumpControl(status: Int) {
        // Thêm dòng này để kiểm tra token
        Log.e("PUMP_DEBUG", "Token length: ${globalToken.length}")
        Log.e("PUMP_DEBUG", "Token empty: ${globalToken.isEmpty()}")

        if (globalToken.isEmpty()) {
            Toast.makeText(this, "Chưa có token, đang login lại...", Toast.LENGTH_SHORT).show()
            performLogin()
            return
        }
        val url = "${Config.BASE_URL}api/Auth/ControlPump"
        val json = """{"Status": $status}""" // Chữ S viết hoa nhé!
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $globalToken") // <--- CHÈN VÀO ĐÂY
            .addHeader("ngrok-skip-browser-warning", "69420")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Lỗi kết nối!", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Lệnh đã thực thi!", Toast.LENGTH_SHORT).show()
                        btnWater.text = if (status == 1) "🛑 TẮT TƯỚI CÂY" else "🚿 BẬT TƯỚI CÂY"
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
    private fun addChartEntry(humidity: Float) {
        val timeLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        chartLabels.add(timeLabel)
        chartEntries.add(Entry(entryIndex, humidity))
        entryIndex++

        if (chartEntries.size > 20) {
            chartEntries.removeAt(0)
            chartLabels.removeAt(0)
            chartEntries.forEachIndexed { i, entry ->
                chartEntries[i] = Entry(i.toFloat(), entry.y)
            }
            entryIndex = chartEntries.size.toFloat()
        }

        val dataSet = LineDataSet(chartEntries, "Độ ẩm (%)").apply {
            color = android.graphics.Color.parseColor("#1976D2")
            setCircleColor(android.graphics.Color.parseColor("#1976D2"))
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(true)
            valueTextSize = 10f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        runOnUiThread {
            lineChart.data = LineData(dataSet)
            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
            lineChart.moveViewToX(entryIndex)
        }
    }
    private fun fetchWeatherAndUpdateChart() {
        // Không cần API key!
        val url = "https://wttr.in/Ho+Chi+Minh?format=j1"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("WEATHER", "Lỗi: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string() ?: return
                Log.d("WEATHER", "Full response: $data")

                try {
                    val json = JSONObject(data)
                    val temp = json
                        .getJSONArray("current_condition")
                        .getJSONObject(0)
                        .getString("temp_C")
                        .toFloat()

                    val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    Log.d("WEATHER", "Nhiệt độ HCM: $temp°C lúc $timeLabel")

                    weatherLabels.add(timeLabel)
                    weatherEntries.add(Entry(weatherIndex, temp))
                    weatherIndex++

                    if (weatherEntries.size > 24) {
                        weatherEntries.removeAt(0)
                        weatherLabels.removeAt(0)
                        weatherEntries.forEachIndexed { i, entry ->
                            weatherEntries[i] = Entry(i.toFloat(), entry.y)
                        }
                        weatherIndex = weatherEntries.size.toFloat()
                    }

                    updateWeatherChart()

                } catch (e: Exception) {
                    Log.e("WEATHER", "Lỗi parse: ${e.message}")
                }
            }
        })
    }

    private fun updateWeatherChart() {
        val dataSet = LineDataSet(weatherEntries, "🌡️ Nhiệt độ HCM (°C)").apply {
            color = android.graphics.Color.parseColor("#E53935") // Màu đỏ
            setCircleColor(android.graphics.Color.parseColor("#E53935"))
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(true)
            valueTextSize = 10f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        runOnUiThread {
            lineChart.data = LineData(dataSet)
            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
            lineChart.moveViewToX(weatherIndex)
        }
    }
    private fun saveWeatherToBackend(temp: Float) {
        val url = "${Config.BASE_URL}api/Weather"
        val json = """{"Temperature": $temp}"""
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("ngrok-skip-browser-warning", "69420")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("WEATHER", "Lưu thất bại: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                Log.d("WEATHER", "Lưu nhiệt độ OK: $temp°C")
            }
        })
        // Sau dòng updateWeatherChart()
        saveWeatherToBackend(temp) // ← Lưu lên backend
    }
    private fun loadWeatherHistory() {
        val url = "${Config.BASE_URL}api/Weather"
        val request = Request.Builder()
            .url(url)
            .addHeader("ngrok-skip-browser-warning", "69420")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("WEATHER", "Load thất bại: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string() ?: return
                val jsonArray = JSONArray(data)

                weatherEntries.clear()
                weatherLabels.clear()
                weatherIndex = 0f

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val temp = obj.optDouble("temperature", 0.0).toFloat()
                    val time = obj.optString("timestamp", "")
                        .substring(11, 16) // Lấy HH:mm
                    weatherLabels.add(time)
                    weatherEntries.add(Entry(weatherIndex, temp))
                    weatherIndex++
                }

                updateWeatherChart()
                Log.d("WEATHER", "Load ${jsonArray.length()} bản ghi thành công!")
            }
        })
    }

    // Đóng kết nối khi tắt App cho sạch máy
    override fun onDestroy() {
        super.onDestroy()
        hubConnection.stop()
    }
}
