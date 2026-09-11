# GiroCam — 手机摄像头方向同步控制 3D Viewer 相机

Android 应用解决方案框架：调用前后摄像头，基于三轴陀螺仪（融合传感器）获取摄像头
光轴的**水平朝向角（Yaw）、俯仰角（Pitch）与横滚角（Roll）**，实时驱动三维模型
Viewer 中的相机，使**手机摄像头的实际拍摄方向与 3D 视角保持一致**。

## 功能

- 后置/前置摄像头实时预览与一键切换（CameraX）
- 旋转向量传感器（陀螺仪+加速度计+磁力计融合）输出设备姿态，无积分漂移
- **封闭房间场景**（程序化生成，零外部资源）：棋盘格地板/天花板 + 四面墙 + 异色参考柱，
  相机固定在房间内部漫游，可直观验证相机运动
- **数据平滑**：方向向量与横滚角分别做一阶惯性低通滤波，降低抖动（可调系数）
- **横竖屏自适应**：传感器按屏幕旋转动态重映射，UI 竖屏上下分栏 / 横屏左右分栏
- **Roll 补偿**：手机横滚时 3D 画面同步横滚
- **双指缩放 FOV**：模拟镜头变焦（25°~100°）
- **运行时权限门控**：未授予摄像头权限时不创建预览，避免 CameraX 绑定失败
- 实时显示 Yaw / Pitch / Roll 角度 HUD

## 技术栈

| 模块 | 技术 |
|------|------|
| 摄像头 | AndroidX CameraX（Preview + CameraSelector） |
| 姿态 | SensorManager `TYPE_ROTATION_VECTOR`（系统级融合） |
| 3D 渲染 | OpenGL ES 2.0（自绘 GLSurfaceView，程序化房间 + 纹理） |
| UI | Jetpack Compose + Material3 |
| 语言 | Kotlin，minSdk 24，targetSdk 34 |
| 构建 | AGP 8.5.2 / Gradle 8.7（已含 Wrapper）/ JDK 17 |

## 项目结构

```
app/src/main/java/com/example/girocam/
├── MainActivity.kt                 # Compose 入口
├── camera/
│   └── CameraController.kt         # CameraX 封装：预览绑定、前后摄切换
├── sensor/
│   ├── DeviceOrientationProvider.kt# 旋转向量传感器 → 方向向量/欧拉角（横竖屏动态重映射）
│   └── OrientationSmoother.kt      # 方向向量 + 横滚角低通滤波器（降抖）
├── viewer/
│   ├── ViewerCamera.kt             # 第一人称 3D 相机：视线方向 + roll 补偿 + 动态 FOV
│   ├── GLViewRenderer.kt           # OpenGL ES 渲染：房间/参考柱/立方体
│   ├── ModelViewer.kt              # GLSurfaceView 封装 + 双指缩放
│   ├── RoomScene.kt                # 程序化房间几何（地板/天花板/四面墙）
│   └── TextureHelper.kt            # 程序化纹理（棋盘格/纯色）
├── bridge/
│   └── OrientationBridge.kt        # 桥接层：传感器 → 平滑 → 3D 相机
└── ui/
    └── MainScreen.kt               # 权限门控 + 横竖屏自适应 UI + 角度 HUD
```

## 相机模型（关键）

Viewer 采用**第一人称相机**：视点固定在房间内部（`ViewerCamera.EYE_X / EYE_Y / EYE_Z`，
默认房间中心、离地 2.5），**视线方向直接等于摄像头光轴方向 `dir`**：

```
target = eye + dir
```

因此"手机朝哪拍，3D 里就朝哪看"，方向与俯仰一一对应，且相机始终位于封闭房间内部。

> 说明：早期版本把相机放在 `dir × 距离` 的球面上并注视原点，会同时导致两个问题——
> 视线方向与拍摄方向**相反**、且相机半径（13）大于房间半宽（12）而**跑到房间外**。
> 现已改为第一人称方案修正。若真机上发现水平/垂直方向整体相反，可在
> `DeviceOrientationProvider` 中调整光轴符号（见"调参与扩展"）。

## 数据流

```
手机陀螺仪/加速度计/磁力计
        │  TYPE_ROTATION_VECTOR（系统融合）
        ▼
DeviceOrientationProvider.onSensorChanged
        │  旋转矩阵 → 按屏幕旋转动态 remap（横竖屏）
        │  后置取设备 -Z 轴 / 前置取 +Z 轴 → 光轴方向 + roll
        ▼
OrientationBridge（主线程回调）
        │  OrientationSmoother：方向向量 + roll 低通滤波
        ▼
ViewerCamera.update(dir, roll)
        │  视点固定 EYE，注视点 = EYE + dir（第一人称）
        ▼
ViewerCamera.getViewMatrix()
        │  再绕视线轴旋转 rollDeg（横滚补偿）
        ▼
GLViewRenderer.onDrawFrame  →  3D 视角 = 手机拍摄视角（房间内漫游）
```

## 坐标系约定（关键）

- **传感器原始坐标系**：手机平放，X 向右、Y 向上（屏幕短边）、Z 朝向屏幕外
- **屏幕旋转 remap 映射表**（自然方向为竖屏的设备）：

  | 屏幕旋转 | remap 轴 |
  |----------|----------|
  | ROTATION_0（竖屏） | AXIS_Y, AXIS_MINUS_X |
  | ROTATION_90 | AXIS_MINUS_X, AXIS_MINUS_Y |
  | ROTATION_180 | AXIS_MINUS_Y, AXIS_X |
  | ROTATION_270 | AXIS_X, AXIS_Y |

- **后置摄像头光轴 = 设备 -Z 轴；前置摄像头光轴 = 设备 +Z 轴**
- 世界坐标系 Y 轴向上；`yaw = atan2(dirX, dirZ)`，`pitch = asin(dirY)`（正值向上），
  `roll` 为绕光轴旋转（正值右倾）
- Viewer 中相机视点固定在房间内部，**视线方向即手机拍摄方向**（第一人称）

## 构建与运行

1. 用 Android Studio（Hedgehog 及以上）打开本项目，等待 Gradle 同步
   （项目已内置 Gradle Wrapper 8.7，也可命令行执行 `./gradlew assembleDebug`）
2. 需要 JDK 17（Android Studio 自带的 JBR 即可）
3. 连接 Android 7.0+（API 24）**真机**（传感器必需，模拟器不支持）
4. 运行 `app`，授予摄像头权限
5. 转动手机，观察 3D 房间视角随摄像头方向同步；双指缩放调整 FOV；
   旋转屏幕切换横竖屏；右上角按钮切换前后摄

## 调参与扩展

- **平滑系数**：`OrientationBridge` 的 `smoother` 参数 `alpha`（0.15 更跟手 / 0.35 更平滑）
- **视点位置**：`ViewerCamera.EYE_X / EYE_Y / EYE_Z`（默认房间中心、离地 2.5）
- **Roll 方向**：若真机上画面横滚方向相反，将 `ViewerCamera.getViewMatrix` 的 rollDeg 取负
- **光轴方向**：若水平/垂直整体相反，调整 `DeviceOrientationProvider` 中 `sign` 或 `dx/dy/dz` 的符号

## 已知限制与后续扩展（待补充）

- **未加载外部真实模型**：当前为程序化封闭房间（`RoomScene` + 参考柱 + 立方体），
  足以验证相机运动；如需真实模型可接入 glTF/OBJ 加载器（Filament、Rajawali 或自建）。
- **"漫游"目前为原地环视**：尚未实现位移行走，可增加虚拟摇杆/单指拖拽位移并做房间边界钳制。
- **平滑为一阶低通**：可升级为 Madgwick / Mahony 或卡尔曼滤波，进一步降低延迟与抖动。
- **机型差异**：前置摄像头镜像与 roll 正负在不同厂商 ROM 上可能有差异，需真机微调。
