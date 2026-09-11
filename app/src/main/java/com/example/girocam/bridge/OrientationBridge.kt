package com.example.girocam.bridge

import com.example.girocam.sensor.DeviceOrientation
import com.example.girocam.sensor.DeviceOrientationProvider
import com.example.girocam.sensor.OrientationSmoother
import com.example.girocam.viewer.ViewerCamera

/**
 * 桥接层：把手机摄像头实际朝向实时映射到 3D Viewer 相机。
 *
 * 数据流：
 *   DeviceOrientationProvider（传感器）
 *       └─> DeviceOrientation(dirX, dirY, dirZ, yaw, pitch, roll)
 *       └─> OrientationSmoother（方向向量 + 横滚角低通滤波，降低抖动）
 *       └─> ViewerCamera.update(...)  → 驱动 OpenGL 视图矩阵
 *
 * 由于 [ViewerCamera] 直接把摄像头光轴方向用作视线方向，
 * 物理朝向与 3D 视角天然一致，无需欧拉角换算。
 */
class OrientationBridge(
    private val provider: DeviceOrientationProvider,
    private val camera: ViewerCamera,
    private val smoother: OrientationSmoother = OrientationSmoother(alpha = 0.22f)
) {

    /** 外部监听（例如 UI 实时显示角度） */
    var onAnglesChanged: ((DeviceOrientation) -> Unit)? = null

    fun start() {
        provider.start { orientation ->
            val s = smoother.smooth(orientation.dirX, orientation.dirY, orientation.dirZ)
            val roll = smoother.smoothRoll(orientation.rollDeg)
            camera.update(s[0], s[1], s[2], roll)
            onAnglesChanged?.invoke(
                orientation.copy(dirX = s[0], dirY = s[1], dirZ = s[2], rollDeg = roll)
            )
        }
    }

    fun stop() {
        provider.stop()
        smoother.reset()
    }

    /** 重置平滑状态（切换前后摄像头时调用） */
    fun resetSmoother() {
        smoother.reset()
    }
}
