package com.example.girocam.viewer

import java.util.ArrayList

/**
 * 程序化生成的封闭房间（无需外部模型文件）：
 * 地板 + 天花板 + 四面墙，输出 [x, y, z, u, v] 交错顶点数组（每面 4 顶点、2 三角形）。
 *
 * 房间尺寸：宽 24 × 高 9 × 深 24，中心在原点，方便在封闭空间内漫游观察相机运动。
 */
object RoomScene {

    const val WIDTH = 24f
    const val HEIGHT = 9f
    const val DEPTH = 24f

    /** 地板/天花板棋盘纹理的重复次数 */
    private const val UV_SCALE_FLOOR = 12f

    /** 构建房间全部顶点：[x, y, z, u, v] * N */
    fun buildVertices(): FloatArray {
        val out = ArrayList<Float>()
        val hw = WIDTH / 2f
        val hd = DEPTH / 2f

        // 地板 (y = 0)
        quad(
            out,
            -hw, 0f, hd, hw, 0f, hd, hw, 0f, -hd, -hw, 0f, -hd,
            0f, 0f, UV_SCALE_FLOOR, 0f, UV_SCALE_FLOOR, UV_SCALE_FLOOR, 0f, UV_SCALE_FLOOR
        )
        // 天花板 (y = HEIGHT)
        quad(
            out,
            -hw, HEIGHT, -hd, hw, HEIGHT, -hd, hw, HEIGHT, hd, -hw, HEIGHT, hd,
            0f, 0f, UV_SCALE_FLOOR, 0f, UV_SCALE_FLOOR, UV_SCALE_FLOOR, 0f, UV_SCALE_FLOOR
        )
        // 后墙 (z = -hd)
        quad(
            out,
            -hw, 0f, -hd, hw, 0f, -hd, hw, HEIGHT, -hd, -hw, HEIGHT, -hd,
            0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f
        )
        // 前墙 (z = +hd)
        quad(
            out,
            hw, 0f, hd, -hw, 0f, hd, -hw, HEIGHT, hd, hw, HEIGHT, hd,
            0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f
        )
        // 左墙 (x = -hw)
        quad(
            out,
            -hw, 0f, hd, -hw, 0f, -hd, -hw, HEIGHT, -hd, -hw, HEIGHT, hd,
            0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f
        )
        // 右墙 (x = +hw)
        quad(
            out,
            hw, 0f, -hd, hw, 0f, hd, hw, HEIGHT, hd, hw, HEIGHT, -hd,
            0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f
        )
        return out.toFloatArray()
    }

    private fun quad(
        out: ArrayList<Float>,
        x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, u2: Float, v2: Float, u3: Float, v3: Float
    ) {
        // 两个三角形：v0-v1-v2 与 v0-v2-v3
        addVertex(out, x0, y0, z0, u0, v0)
        addVertex(out, x1, y1, z1, u1, v1)
        addVertex(out, x2, y2, z2, u2, v2)
        addVertex(out, x0, y0, z0, u0, v0)
        addVertex(out, x2, y2, z2, u2, v2)
        addVertex(out, x3, y3, z3, u3, v3)
    }

    private fun addVertex(out: ArrayList<Float>, x: Float, y: Float, z: Float, u: Float, v: Float) {
        out.add(x); out.add(y); out.add(z); out.add(u); out.add(v)
    }
}
