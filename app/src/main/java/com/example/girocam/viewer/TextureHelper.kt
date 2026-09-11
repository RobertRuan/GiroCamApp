package com.example.girocam.viewer

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils

/**
 * 程序化生成 OpenGL 纹理的工具类（无需外部图片资源）。
 * 支持棋盘格纹理（地板/天花板）与纯色纹理。
 */
object TextureHelper {

    /**
     * 生成棋盘格纹理并上传 GPU。
     * @param size 纹理边长（像素）
     * @param cell 每格边长（像素）
     * @param colorA / @param colorB 两种格子的颜色
     */
    fun createCheckerTexture(
        size: Int = 64,
        cell: Int = 8,
        colorA: Int = 0xFFF2F2F2.toInt(),
        colorB: Int = 0xFF9A9A9A.toInt()
    ): Int {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = (x / cell + y / cell) % 2
                bmp.setPixel(x, y, if (idx == 0) colorA else colorB)
            }
        }
        return upload(bmp)
    }

    /** 生成纯色纹理 */
    fun createSolidTexture(color: Int): Int {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return upload(bmp)
    }

    private fun upload(bmp: Bitmap): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        // GL_REPEAT 允许 UV 超出 [0,1]（地板棋盘格重复）
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        return ids[0]
    }
}
