# PlantTracker 功能更新说明

## 新增功能

### 1. 悬浮窗增强
- 悬浮球显示植物数量
- 展开后显示植物列表
- 支持拖动悬浮窗位置
- 点击植物可编辑/删除

### 2. 截图识别功能
- 点击悬浮窗的相机图标启动截图识别
- 自动截取屏幕并 OCR 识别文字
- 识别包含"后成熟"的时间信息
- 提取昵称作为植物名称
- 自动添加到植物列表

### 3. 智能时间输入
- 支持中文格式："1小时15分钟", "2小时46分钟"
- 支持简写格式："1h15m", "2h30m"
- 支持纯分钟："75分钟"
- 支持冒号格式："12:15:34"
- 实时显示解析结果

### 4. 修改植物功能
- 点击悬浮窗中的植物打开编辑对话框
- 可修改植物名称
- 可修改剩余时间（使用智能时间输入）
- 可删除植物

### 5. 常驻通知
- 显示最近快要成熟的5种植物
- 每分钟自动更新
- 点击通知打开应用

## 修改的文件

### 1. app/build.gradle.kts
- 添加 ML Kit OCR 依赖
- 添加中文文字识别支持

### 2. app/src/main/AndroidManifest.xml
- 添加截图权限 FOREGROUND_SERVICE_MEDIA_PROJECTION
- 添加 ScreenCaptureActivity 声明
- 添加 PlantNotificationService 声明

### 3. app/src/main/java/com/planttracker/ui/MainActivity.kt
- 添加截图识别结果处理
- 启动常驻通知服务
- 处理从悬浮窗启动的意图

### 4. app/src/main/java/com/planttracker/service/FloatingWindowService.kt
- 添加截图识别按钮
- 添加植物编辑功能
- 优化悬浮窗 UI

### 5. app/src/main/java/com/planttracker/ui/screen/AddPlantDialog.kt
- 集成智能时间解析
- 实时显示时间解析结果

## 新增的文件

### 1. app/src/main/java/com/planttracker/util/TimeParser.kt
智能时间解析工具类
- 支持多种时间格式解析
- 从 OCR 文本提取成熟时间
- 提取昵称

### 2. app/src/main/java/com/planttracker/util/ScreenCaptureHelper.kt
屏幕截图工具类
- 使用 MediaProjection API 截图
- 管理截图权限

### 3. app/src/main/java/com/planttracker/util/OcrHelper.kt
OCR 识别工具类
- 使用 ML Kit 识别文字
- 返回识别结果

### 4. app/src/main/java/com/planttracker/service/PlantNotificationService.kt
常驻通知服务
- 显示最近5个将要成熟的植物
- 每分钟更新一次

### 5. app/src/main/java/com/planttracker/ui/screen/ScreenCaptureActivity.kt
截图识别 Activity
- 请求截图权限
- 执行截图和 OCR 识别
- 显示识别结果并添加到列表

## 使用方法

### 启动悬浮窗
1. 打开应用
2. 点击顶部工具栏的悬浮窗图标
3. 授权悬浮窗权限

### 截图识别
1. 点击悬浮窗展开面板
2. 点击相机图标
3. 切换到农场应用界面
4. 等待自动截图识别
5. 确认识别结果后添加到列表

### 手动添加植物
1. 点击主界面的 + 按钮
2. 选择植物类型或自定义
3. 选择预设时间或输入自定义时间
4. 支持智能时间输入（如：1小时15分钟）

### 修改植物
1. 点击悬浮窗展开面板
2. 点击要修改的植物
3. 在编辑对话框中修改名称或时间
4. 点击保存

### 常驻通知
- 应用启动后自动显示
- 下拉通知栏查看最近5个将要成熟的植物
- 点击通知打开应用

## 注意事项

1. 截图识别需要授权"在其他应用上显示"权限
2. 常驻通知需要通知权限
3. 截图识别功能需要 Android 5.0 (API 21) 以上
4. OCR 识别需要联网下载模型（首次使用）
