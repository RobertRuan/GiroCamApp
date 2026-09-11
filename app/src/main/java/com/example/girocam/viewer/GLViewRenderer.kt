package com.example.girocam.viewer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 渲染器。
 *
 * 场景：程序化封闭房间（[RoomScene]，棋盘格地板/天花板）+ 四根异色参考柱
 * + 中央立方体。相机在房间内部漫游，视角随手机姿态实时变化，
 * 能直观验证"摄像头方向 ↔ 3D 视角"的一致性。
 *
 * 渲染细节：
 * - 单着色器通过 uUseTexture 开关支持"纹理"与"纯色"两种绘制
 * - FOV 可动态调整（双指缩放），检测到变化时重建投影矩阵
 */
class GLViewRenderer(private val camera: ViewerCamera) : GLSurfaceView.Renderer {

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var program = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uMvpLoc = 0
    private var uColorLoc = 0
    private var uTextureLoc = 0
    private var uUseTextureLoc = 0

    private var roomVbo = 0
    private var roomVertexCount = 0
    private var roomTex = 0
    private var cubeVbo = 0
    private var cubeVertexCount = 0

    /** 参考柱：房间内不对称摆放，便于观察相机运动 */
    private data class Pillar(val cx: Float, val cz: Float, val w: Float, val h: Float, val color: Int)

    private val pillars = listOf(
        Pillar(3.5f, 2.5f, 1.0f, 4.0f, 0xFF4A90D9.toInt()),  // 蓝
        Pillar(-4.0f, -3.0f, 1.2f, 5.0f, 0xFFE05C4A.toInt()), // 红
        Pillar(-2.0f, 4.5f, 0.8f, 3.0f, 0xFFE0B94A.toInt()),  // 黄
        Pillar(5.0f, -4.5f, 1.4f, 6.0f, 0xFF5ABF6A.toInt())   // 绿
    )

    private var currentFov = 0f
    private var aspect = 1f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.05f, 0.07f, 0.10f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE) // 房间内从内部观察，关闭背面剔除

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        uMvpLoc = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        uColorLoc = GLES20.glGetUniformLocation(program, "uColor")
        uTextureLoc = GLES20.glGetUniformLocation(program, "uTexture")
        uUseTextureLoc = GLES20.glGetUniformLocation(program, "uUseTexture")

        // 房间
        val room = RoomScene.buildVertices()
        roomVbo = createVbo(room)
        roomVertexCount = room.size / 5
        roomTex = TextureHelper.createCheckerTexture(
            size = 64,
            cell = 8,
            colorA = 0xFFF2F2F2.toInt(),
            colorB = 0xFF9A9A9A.toInt()
        )

        // 中央立方体（纯色，UV 全 0）
        val cube = createCubeVertices(0.9f)
        cubeVbo = createVbo(cube)
        cubeVertexCount = cube.size / 5

        currentFov = 0f // 强制首次重建投影
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = if (height == 0) 1f else width.toFloat() / height
        rebuildProjection(camera.getFovDeg())
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        // FOV 动态变化时重建投影矩阵
        val fov = camera.getFovDeg()
        if (fov != currentFov) rebuildProjection(fov)

        camera.getViewMatrix(viewMatrix)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, roomTex)
        GLES20.glUniform1i(uTextureLoc, 0)

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)

        // 房间（棋盘纹理，白色调制）
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        drawTextured(roomVbo, roomVertexCount, 1f, 1f, 1f, 1f, useTexture = true)

        // 参考柱（各自模型矩阵 + 纯色）
        for (p in pillars) drawPillar(p)

        // 中央立方体（主 mvp，纯色）
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        drawTextured(cubeVbo, cubeVertexCount, 1f, 0.85f, 0.40f, 1f, useTexture = false)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawPillar(p: Pillar) {
        val model = FloatArray(16)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, p.cx, p.h / 2f, p.cz)
        Matrix.scaleM(model, 0, p.w, p.h, p.w)

        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, viewMatrix, 0, model)
        Matrix.multiplyMM(mvp, 0, projectionMatrix, 0, mvp)
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvp, 0)

        val r = ((p.color shr 16) and 0xFF) / 255f
        val g = ((p.color shr 8) and 0xFF) / 255f
        val b = (p.color and 0xFF) / 255f
        drawTextured(cubeVbo, cubeVertexCount, r, g, b, 1f, useTexture = false)
    }

    private fun drawTextured(
        vbo: Int,
        count: Int,
        r: Float, g: Float, b: Float, a: Float,
        useTexture: Boolean
    ) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        // 顶点布局：[x, y, z, u, v]，5 float = 20 字节
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 20, 0)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 20, 12)
        GLES20.glUniform4f(uColorLoc, r, g, b, a)
        GLES20.glUniform1f(uUseTextureLoc, if (useTexture) 1f else 0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
    }

    private fun rebuildProjection(fov: Float) {
        currentFov = fov
        Matrix.perspectiveM(projectionMatrix, 0, fov, aspect, 0.1f, 200f)
    }

    // ---------------- 几何数据 ----------------

    /** 立方体（带 UV，UV 固定为 0 便于纯色绘制），中心在原点 */
    private fun createCubeVertices(size: Float): FloatArray {
        val s = size
        val v = arrayOf(
            floatArrayOf(-s, -s, -s), floatArrayOf(s, -s, -s), floatArrayOf(s, s, -s), floatArrayOf(-s, s, -s),
            floatArrayOf(-s, -s, s), floatArrayOf(s, -s, s), floatArrayOf(s, s, s), floatArrayOf(-s, s, s)
        )
        val faces = arrayOf(
            intArrayOf(0, 2, 1), intArrayOf(0, 3, 2), // 背面 z=-s
            intArrayOf(4, 5, 6), intArrayOf(4, 6, 7), // 正面 z=+s
            intArrayOf(1, 2, 6), intArrayOf(1, 6, 5), // 右面 x=+s
            intArrayOf(0, 4, 7), intArrayOf(0, 7, 3), // 左面 x=-s
            intArrayOf(0, 1, 5), intArrayOf(0, 5, 4), // 底面 y=-s
            intArrayOf(3, 7, 6), intArrayOf(3, 6, 2)  // 顶面 y=+s
        )
        val out = ArrayList<Float>()
        for (f in faces) {
            for (idx in f) {
                out.add(v[idx][0]); out.add(v[idx][1]); out.add(v[idx][2])
                out.add(0f); out.add(0f)
            }
        }
        return out.toFloatArray()
    }

    // ---------------- GL 工具 ----------------

    private fun createVbo(vertices: FloatArray): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        val bb = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder())
        bb.asFloatBuffer().put(vertices).position(0)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, bb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return ids[0]
    }

    private fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "program link error: " + GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "shader compile error: " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {
        private const val TAG = "GLViewRenderer"

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uMvpMatrix;
            varying vec2 vTexCoord;
            void main() {
                vTexCoord = aTexCoord;
                gl_Position = uMvpMatrix * aPosition;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec4 uColor;
            uniform float uUseTexture;
            varying vec2 vTexCoord;
            void main() {
                vec4 texel = texture2D(uTexture, vTexCoord) * uColor;
                gl_FragColor = mix(uColor, texel, uUseTexture);
            }
        """
    }
}
