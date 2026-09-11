package com.example.girocam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.girocam.ui.MainScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 摄像头权限统一在 MainScreen 内申请：仅在授权后才创建预览，避免无权限时绑定失败
        setContent {
            MainScreen()
        }
    }
}
