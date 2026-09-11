package com.example.girocam.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/** 摄像头朝向 */
enum class CameraFacing { BACK, FRONT }

/**
 * CameraX 摄像头控制器：
 * - 绑定生命周期，自动处理前台/后台释放
 * - 支持后置/前置切换（切换后需重新绑定 [OrientationBridge] 的 facing）
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewView: PreviewView? = null

    @Volatile
    var facing: CameraFacing = CameraFacing.BACK
        private set

    /**
     * 将预览绑定到 [previewView]。ProcessCameraProvider 初始化是异步的，
     * 内部通过主线程回调完成绑定。
     */
    fun bind(previewView: PreviewView) {
        this.previewView = previewView
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(context))
    }

    /** 切换前后摄像头 */
    fun switchFacing(newFacing: CameraFacing) {
        facing = newFacing
        bindCamera()
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val view = previewView ?: return
        provider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(view.surfaceProvider)
        }
        val selector = when (facing) {
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
        try {
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview)
        } catch (e: Exception) {
            Log.e(TAG, "绑定摄像头失败", e)
        }
    }

    /** 释放摄像头（在页面销毁时调用） */
    fun shutdown() {
        cameraProvider?.unbindAll()
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
