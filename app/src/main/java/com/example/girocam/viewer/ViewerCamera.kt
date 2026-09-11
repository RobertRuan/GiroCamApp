package com.example.girocam.viewer

import android.opengl.Matrix
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 3D Viewer 相机（第一人称）。
 *
 * 相机（视点）固定在房间内部 [EYE_X]/[EYE_Y]/[EYE_Z]，
 * 视线方向直接等于摄像头光轴方向 [dirX]/[dirY]/[dirZ]：
 *   target = eye + dir
 *
 * 这样手机摄像头朝哪拍、3D 里就朝哪看，方向与俯仰一一对应，
 * 且相机始终位于封闭房间内部，可直观验证漫游效果。
 */
class ViewerCamera {

    @Volatile
    private var dirX = 0f
    @Volatile
    private var dirY = 0f
    @Volatile
    private var dirZ = -1f // 默认看向世界 -Z
    @Volatile
    private var rollDeg = 0f

    /** 视野角（FOV），双指缩放调整，范围 [MIN_FOV, MAX_FOV] */
    @Volatile
    private var fovDeg = DEFAULT_FOV

    /** 由传感器更新方向与横滚角（内部做归一化） */
    fun update(dirX: Float, dirY: Float, dirZ: Float, rollDeg: Float) {
        val len = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
        if (len <= 1e-6f) return
        this.dirX = dirX / len
        this.dirY = dirY / len
        this.dirZ = dirZ / len
        this.rollDeg = rollDeg
    }

    /** 双指缩放：factor > 1 为放大（FOV 减小、画面拉近） */
    fun zoomBy(factor: Float) {
        if (factor <= 0f) return
        fovDeg = (fovDeg / factor).coerceIn(MIN_FOV, MAX_FOV)
    }

    /** 生成视图矩阵（GL 线程调用） */
    fun getViewMatrix(out: FloatArray) {
        // 视线接近垂直（|dirY| 趋近 1）时，世界 up=(0,1,0) 会与视线平行导致矩阵退化，
        // 此时切换到 X 轴作为 up。
        val upX = if (abs(dirY) > 0.99f) 1f else 0f
        val upY = if (abs(dirY) > 0.99f) 0f else 1f
        val upZ = 0f

        // 第一人称：视点固定在房间内部，注视点 = 视点 + 光轴方向
        Matrix.setLookAtM(
            out, 0,
            EYE_X, EYE_Y, EYE_Z,
            EYE_X + dirX, EYE_Y + dirY, EYE_Z + dirZ,
            upX, upY, upZ
        )

        // roll 补偿：绕视线（相机 z 轴）旋转，使画面横滚与手机一致。
        // 若真机上方向相反，将 rollDeg 取负即可。
        if (rollDeg != 0f) {
            Matrix.rotateM(out, 0, rollDeg, 0f, 0f, 1f)
        }
    }

    /** 当前 FOV（渲染线程读取，用于重建投影矩阵） */
    fun getFovDeg(): Float = fovDeg

    companion object {
        /** 视点固定位置：房间中心、离地 2.5（房间高 9），保证相机始终在封闭房间内部 */
        const val EYE_X = 0f
        const val EYE_Y = 2.5f
        const val EYE_Z = 0f

        const val DEFAULT_FOV = 60f
        const val MIN_FOV = 25f
        const val MAX_FOV = 100f
    }
}
