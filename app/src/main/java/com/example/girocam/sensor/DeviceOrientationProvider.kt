package com.example.girocam.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import com.example.girocam.camera.CameraFacing
import kotlin.math.asin
import kotlin.math.atan2

/**
 * 一次姿态采样结果。
 *
 * [dirX]/[dirY]/[dirZ]：摄像头光轴在世界坐标系（Y 轴向上）中的单位方向向量。
 * [yawDeg]：水平朝向角，atan2(dirX, dirZ)，范围 -180°..180°，0° = 屏幕顶边方向。
 * [pitchDeg]：俯仰角，asin(dirY)，范围 -90°..90°，正值 = 向上看。
 * [rollDeg]：横滚角（绕光轴旋转），正值 = 机身向右倾斜（顺时针）。
 */
data class DeviceOrientation(
    val dirX: Float,
    val dirY: Float,
    val dirZ: Float,
    val yawDeg: Float,
    val pitchDeg: Float,
    val rollDeg: Float
)

/**
 * 设备姿态提供器。
 *
 * 使用 TYPE_ROTATION_VECTOR（陀螺仪 + 加速度计 + 磁力计的融合输出），
 * 底层系统已做传感器融合与校准，避免自行积分陀螺仪带来的漂移。
 *
 * 坐标系约定：
 * - 传感器原始坐标系：X 向右、Y 向上、Z 朝向屏幕外（手机平放时定义）
 * - 根据屏幕旋转动态 remap 到"屏幕顶边朝上"的自然方向，横竖屏均支持
 * - 后置摄像头光轴 = 设备 -Z 轴；前置摄像头光轴 = 设备 +Z 轴
 */
class DeviceOrientationProvider(context: Context) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var listener: ((DeviceOrientation) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 当前生效的摄像头朝向（切换摄像头时同步更新） */
    @Volatile
    var facing: CameraFacing = CameraFacing.BACK

    /** 开始监听。回调在主线程。 */
    fun start(listener: (DeviceOrientation) -> Unit) {
        this.listener = listener
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    /** 停止监听并释放资源 */
    fun stop() {
        sensorManager.unregisterListener(this)
        listener = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // 根据当前屏幕旋转动态重映射，横竖屏均适用
        val (xAxis, yAxis) = remapAxesForDisplayRotation(currentRotation())
        SensorManager.remapCoordinateSystem(rotationMatrix, xAxis, yAxis, remapped)

        // 横滚角：绕屏幕法向（设备 Z 轴）的旋转
        SensorManager.getOrientation(remapped, orientationAngles)
        val rollRaw = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        // remapped 的第 3 列 = 设备 Z 轴在世界坐标中的方向。
        // 后置摄像头光轴 = -Z，前置摄像头光轴 = +Z。
        val sign = if (facing == CameraFacing.BACK) -1f else 1f
        val dx = sign * remapped[6]
        val dy = sign * remapped[7]
        val dz = sign * remapped[8]

        val yaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
        val pitch = Math.toDegrees(asin(dy.toDouble().coerceIn(-1.0, 1.0))).toFloat()
        // 前置摄像头画面为镜像，横滚方向取反
        val roll = if (facing == CameraFacing.BACK) rollRaw else -rollRaw

        val orientation = DeviceOrientation(dx, dy, dz, yaw, pitch, roll)
        mainHandler.post { listener?.invoke(orientation) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 融合传感器精度变化，通常无需处理
    }

    private fun currentRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appContext.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.rotation
        }
    }

    /**
     * 屏幕旋转 → remap 轴映射表。
     * 该映射将设备"屏幕顶边方向"作为世界 +Z（屏幕外方向），
     * 适用于自然方向为竖屏的手机，横竖屏切换时自动切换映射。
     */
    private fun remapAxesForDisplayRotation(rotation: Int): Pair<Int, Int> {
        return when (rotation) {
            Surface.ROTATION_90 -> Pair(SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y)
            Surface.ROTATION_180 -> Pair(SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X)
            Surface.ROTATION_270 -> Pair(SensorManager.AXIS_X, SensorManager.AXIS_Y)
            else -> Pair(SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X) // ROTATION_0
        }
    }
}
