package com.example.girocam.viewer

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.ScaleGestureDetector

/**
 * 3D 模型 Viewer 视图。
 *
 * - 由外部注入共享的 [ViewerCamera]，传感器数据通过它驱动渲染相机
 * - 连续渲染模式，传感器回调更新方向后无需主动刷新即可生效
 * - 内置双指缩放手势：缩放调整 [ViewerCamera] 的 FOV（模拟镜头变焦）
 */
class ModelViewer(
    context: Context,
    private val viewerCamera: ViewerCamera
) : GLSurfaceView(context) {

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                viewerCamera.zoomBy(detector.scaleFactor)
                return true
            }
        }
    )

    init {
        setEGLContextClientVersion(2) // OpenGL ES 2.0
        setRenderer(GLViewRenderer(viewerCamera))
        renderMode = RENDERMODE_CONTINUOUSLY
        setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            true
        }
    }
}
