package com.example.girocam.sensor

import kotlin.math.sqrt

/**
 * 一阶惯性低通滤波器：对摄像头光轴方向向量与横滚角做平滑，降低传感器抖动。
 *
 * 选择对方向向量滤波而非欧拉角，是因为向量本身连续，
 * 不会出现 yaw 在 ±180° 跳变导致的滤波异常；
 * 横滚角则单独做 ±180° 展开后再滤波，避免跨零点跳变。
 *
 * @param alpha 平滑系数：越大越跟手（抖动多），越小越平滑（延迟高）。
 *              建议范围 0.15 ~ 0.35，默认 0.22。
 */
class OrientationSmoother(private val alpha: Float = 0.22f) {

    private var sx = 0f
    private var sy = 0f
    private var sz = 0f
    private var initialized = false

    private var sRoll = 0f
    private var rollInitialized = false

    /** 输入原始方向向量，返回平滑后的单位向量 [x, y, z] */
    fun smooth(dirX: Float, dirY: Float, dirZ: Float): FloatArray {
        if (!initialized) {
            sx = dirX
            sy = dirY
            sz = dirZ
            initialized = true
        } else {
            sx += alpha * (dirX - sx)
            sy += alpha * (dirY - sy)
            sz += alpha * (dirZ - sz)
            val len = sqrt(sx * sx + sy * sy + sz * sz)
            if (len > 1e-6f) {
                sx /= len
                sy /= len
                sz /= len
            }
        }
        return floatArrayOf(sx, sy, sz)
    }

    /**
     * 输入原始横滚角（度），返回平滑后的角度。
     * 内部先把角度差展开到 -180°..180°，避免跨零点时被滤波"拉回去"。
     */
    fun smoothRoll(rollDeg: Float): Float {
        if (!rollInitialized) {
            sRoll = rollDeg
            rollInitialized = true
        } else {
            var delta = rollDeg - sRoll
            while (delta > 180f) delta -= 360f
            while (delta < -180f) delta += 360f
            sRoll += alpha * delta
        }
        return sRoll
    }

    /** 重置滤波状态（例如切换前后摄像头时） */
    fun reset() {
        initialized = false
        rollInitialized = false
    }
}
