# 🌱 PlantTracker - 植物种植记录

一款 Android 原生工具类应用，帮你记录种植进度，到时间自动闹钟提醒。

## ✨ 核心功能

| 功能 | 说明 |
|------|------|
| 🪟 **悬浮窗** | 常驻悬浮球，展开查看所有植物状态，可拖拽 |
| 🌾 **植物记录** | 20种预设植物（小麦、番茄、草莓等）+ 自定义 |
| ⏰ **智能闹钟** | 每个植物设置成熟时间，到期自动提醒 |
| 🔄 **闹钟去重** | 多个植物成熟时间相同 → 只创建一个闹钟 |
| ✅ **收获管理** | 一键标记收获，查看历史记录 |
| 📊 **统计面板** | 生长中/已成熟/总计 实时统计 |

## 🏗️ 技术架构

```
Kotlin + Jetpack Compose + Hilt + Room
├── UI Layer        → Jetpack Compose (Material Design 3)
├── State           → ViewModel + StateFlow
├── DI              → Hilt
├── Database        → Room (SQLite)
├── Alarm           → AlarmManager + BroadcastReceiver
└── Floating Window → WindowManager + TYPE_APPLICATION_OVERLAY
```

## 📁 项目结构

```
app/src/main/java/com/planttracker/
├── PlantTrackerApp.kt          # Application 入口
├── alarm/
│   ├── AlarmReceiver.kt        # 闹钟广播接收（发送通知）
│   └── PlantAlarmManager.kt    # 闹钟管理（创建/取消/去重）
├── data/
│   ├── db/
│   │   ├── PlantDao.kt         # Room DAO
│   │   └── PlantDatabase.kt    # Room 数据库
│   ├── model/
│   │   └── Plant.kt            # 植物数据实体
│   └── repository/
│       └── PlantRepository.kt  # 数据仓库
├── di/
│   └── AppModule.kt            # Hilt 依赖注入模块
├── service/
│   └── FloatingWindowService.kt# 悬浮窗服务
└── ui/
    ├── MainActivity.kt         # 主 Activity（权限管理）
    ├── theme/
    │   └── Theme.kt            # Material Design 3 主题
    ├── viewmodel/
    │   └── PlantViewModel.kt   # ViewModel
    └── screen/
        ├── PlantListScreen.kt  # 植物列表主界面
        └── AddPlantDialog.kt   # 添加植物弹窗
```

## 🚀 如何编译

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK 34
- Gradle 8.6

### 步骤
1. 用 Android Studio 打开项目根目录
2. 等待 Gradle Sync 完成
3. 连接 Android 设备或启动模拟器（API 26+）
4. 点击 Run ▶️

## 🔔 闹钟去重逻辑

```
用户添加 3 个植物：
  番茄 → 明天 14:00 成熟
  小麦 → 明天 14:00 成熟  ← 时间相同！
  玉米 → 明天 18:00 成熟

系统只创建 2 个闹钟（而非 3 个）：
  ✅ 闹钟1: 明天 14:00 → 通知 "番茄、小麦 已成熟！"
  ✅ 闹钟2: 明天 18:00 → 通知 "玉米 已成熟！"
```

**去重原理**：`PlantDao.getDistinctFutureMatureTimes()` 使用 SQL `DISTINCT` 查询，
`PendingIntent` 的 `requestCode` 使用 `matureAt.hashCode()`，保证相同时间复用同一个闹钟。

## 📱 权限说明

| 权限 | 用途 | 类型 |
|------|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗显示 | 运行时申请 |
| `POST_NOTIFICATIONS` | 成熟通知提醒 | 运行时申请 |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟 | 运行时申请 |
| `RECEIVE_BOOT_COMPLETED` | 开机后恢复闹钟 | 自动授予 |

## 📄 License

MIT
