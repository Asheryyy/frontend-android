package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WaterPumpApp()
        }
    }
}

@Composable
fun WaterPumpApp() {
    // Biến trạng thái (State): Lưu trữ xem máy bơm đang BẬT (true) hay TẮT (false)
    var isPumpOn by remember { mutableStateOf(false) }

    // Column xếp các phần tử theo chiều dọc
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tiêu đề App
        Text(
            text = "HỆ THỐNG ĐIỀU KHIỂN BƠM",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Hiển thị trạng thái máy bơm (Chữ và màu sắc đổi tự động theo biến isPumpOn)
        Text(
            text = if (isPumpOn) "Trạng thái: ĐANG BƠM 💦" else "Trạng thái: ĐÃ TẮT 🛑",
            fontSize = 20.sp,
            color = if (isPumpOn) Color(0xFF009688) else Color.Red, // Màu xanh Teal nếu bật, Đỏ nếu tắt
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Nút bấm điều khiển
        Button(
            onClick = {
                // Hành động khi bấm: Đảo ngược trạng thái (đang bật thành tắt, đang tắt thành bật)
                isPumpOn = !isPumpOn

                // NẾU LÀ APP THẬT: Chỗ này sẽ là code gọi API (HTTP Request)
                // hoặc gửi bản tin MQTT xuống Server/Mạch Arduino để đóng ngắt rơ-le.
            },
            colors = ButtonDefaults.buttonColors(
                // Nút cũng tự đổi màu: Đang bật thì nút màu Đỏ (để báo bấm vào là tắt), ngược lại màu Xanh
                containerColor = if (isPumpOn) Color.Red else Color(0xFF009688)
            ),
            modifier = Modifier.size(width = 220.dp, height = 60.dp)
        ) {
            Text(
                text = if (isPumpOn) "TẮT MÁY BƠM" else "BẬT MÁY BƠM",
                fontSize = 18.sp
            )
        }
    }
}