package com.example.girocam.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.girocam.bridge.OrientationBridge
import com.example.girocam.camera.CameraController
import com.example.girocam.camera.CameraFacing
import com.example.girocam.sensor.DeviceOrientationProvider
import com.example.girocam.viewer.ModelViewer
import com.example.girocam.viewer.ViewerCamera
import java.util.Locale

/**
 * 主界面入口：先做摄像头权限门控，授权后再进入同步演示界面。
 *
 * 这样可避免"权限尚未授予就绑定 CameraX 导致预览失败"的问题。
 */
@Composable
fun MainScreen() {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // 首次进入自动申请；用户拒绝后不会反复弹窗（key 不变则不再触发）
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraSyncScreen()
    } else {
        PermissionPane(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
    }
}

/** 摄像头权限未授予时的提示界面（可手动重新申请） */
@Composable
private fun PermissionPane(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "本应用需要摄像头权限，用于实时预览并将拍摄方向同步到 3D 场景。",
                textAlign = TextAlign.Center
            )
            Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
                Text("授予摄像头权限")
            }
        }
    }
}

/**
 * 同步演示界面：
 * - 摄像头预览（PreviewView）+ 前后摄切换 + 实时角度 HUD
 * - 3D 模型 Viewer（GLSurfaceView），相机随手机姿态同步
 * - 竖屏上下分栏，横屏左右分栏；3D Viewer 支持双指缩放 FOV
 */
@Composable
private fun CameraSyncScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 各模块实例（进程内共享同一 ViewerCamera，实现"物理方向 ↔ 3D 视角"一一对应）
    val cameraController = remember { CameraController(context, lifecycleOwner) }
    val orientationProvider = remember { DeviceOrientationProvider(context) }
    val viewerCamera = remember { ViewerCamera() }
    val bridge = remember { OrientationBridge(orientationProvider, viewerCamera) }

    var facing by remember { mutableStateOf(CameraFacing.BACK) }
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var roll by remember { mutableFloatStateOf(0f) }

    // 启动传感器桥接，并实时刷新角度显示
    DisposableEffect(Unit) {
        bridge.start()
        bridge.onAnglesChanged = { o ->
            yaw = o.yawDeg
            pitch = o.pitchDeg
            roll = o.rollDeg
        }
        onDispose {
            bridge.onAnglesChanged = null
            bridge.stop()
            cameraController.shutdown()
        }
    }

    fun switchFacing() {
        facing = if (facing == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK
        cameraController.switchFacing(facing)
        orientationProvider.facing = facing
        bridge.resetSmoother()
        Toast.makeText(
            context,
            if (facing == CameraFacing.FRONT) "前置摄像头" else "后置摄像头",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ---------- 摄像头预览面板 ----------
    @Composable
    fun CameraPane() {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }.also { cameraController.bind(it) }
                }
            )

            // 前后摄切换按钮
            Button(
                onClick = ::switchFacing,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Text(if (facing == CameraFacing.FRONT) "切到后置" else "切到前置")
            }

            // 实时角度 HUD（Yaw / Pitch / Roll）
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = String.format(
                        Locale.US,
                        "Yaw: %6.1f°  Pitch: %6.1f°  Roll: %6.1f°",
                        yaw, pitch, roll
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }

    // ---------- 3D Viewer 面板（双指缩放 FOV） ----------
    @Composable
    fun ViewerPane() {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> ModelViewer(ctx, viewerCamera) }
        )
    }

    // ---------- 横竖屏自适应布局 ----------
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) { CameraPane() }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) { ViewerPane() }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { CameraPane() }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { ViewerPane() }
        }
    }
}
