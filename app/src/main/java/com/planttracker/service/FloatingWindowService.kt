package com.planttracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.planttracker.data.model.Plant
import com.planttracker.data.repository.PlantRepository
import com.planttracker.ui.screen.ScreenCaptureActivity
import com.planttracker.ui.theme.PlantTrackerTheme
import com.planttracker.util.TimeParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class FloatingWindowService : Service() {

    @Inject
    lateinit var plantRepository: PlantRepository

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 悬浮窗位置
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // 广播接收器
    private var plantRecognizedReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundService()
        createFloatingWindow()
        registerPlantRecognizedReceiver()
    }

    private fun registerPlantRecognizedReceiver() {
        plantRecognizedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ScreenCaptureActivity.ACTION_PLANT_RECOGNIZED) {
                    val plantName = intent.getStringExtra("plant_name")
                    val matureTime = intent.getIntExtra("mature_time", 0)
                    android.util.Log.d("FloatingWindowService", "收到植物识别广播: $plantName, 成熟时间: ${matureTime}分钟")
                    // 数据会自动通过 repository.getActivePlants() 刷新
                    // 可以在这里添加一个 Toast 提示
                    android.widget.Toast.makeText(
                        this@FloatingWindowService,
                        "已添加: $plantName",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        val filter = IntentFilter(ScreenCaptureActivity.ACTION_PLANT_RECOGNIZED)
        registerReceiver(plantRecognizedReceiver, filter)
    }

    private fun startForegroundService() {
        val channelId = "floating_window_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持悬浮窗运行"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("植物追踪器")
            .setContentText("悬浮窗运行中")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(1002, notification)
    }

    private fun createFloatingWindow() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        val lifecycleOwner = FloatingWindowLifecycleOwner()
        val composeView = ComposeView(this).apply {
            // 为 ComposeView 提供必要的 Lifecycle/SavedState owner
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }

        composeView.setContent {
            PlantTrackerTheme {
                FloatingWidget(
                    repository = plantRepository,
                    scope = serviceScope,
                    onAddPlant = {
                        // 打开主界面添加植物
                        val intent = Intent(this@FloatingWindowService,
                            com.planttracker.ui.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("action", "add_plant")
                        }
                        startActivity(intent)
                    },
                    onCaptureScreen = {
                        // 启动截图专用 Activity - 使用透明主题，不跳转回主界面
                        val intent = Intent(this@FloatingWindowService,
                            com.planttracker.ui.screen.ScreenCaptureActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        }
                        startActivity(intent)
                    },
                    onDrag = { dx, dy ->
                        params.x += dx.roundToInt()
                        params.y += dy.roundToInt()
                        windowManager.updateViewLayout(composeView, params)
                    },
                    onClose = {
                        stopSelf()
                    }
                )
            }
        }

        floatView = composeView
        windowManager.addView(composeView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatView?.let { windowManager.removeView(it) }
        plantRecognizedReceiver?.let { unregisterReceiver(it) }
        serviceScope.cancel()
    }

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, FloatingWindowService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingWindowService::class.java))
        }
    }
}

// ── Floating Widget Composable ──────────────────────────────────────────────

@Composable
fun FloatingWidget(
    repository: PlantRepository,
    scope: CoroutineScope,
    onAddPlant: () -> Unit,
    onCaptureScreen: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onClose: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val plants by repository.getActivePlants().collectAsState(initial = emptyList())

    if (!expanded) {
        // 折叠态：悬浮球
        FloatingBall(
            plantCount = plants.size,
            onDrag = onDrag,
            onClick = { expanded = true }
        )
    } else {
        // 展开态：植物列表面板
        FloatingPanel(
            plants = plants,
            repository = repository,
            scope = scope,
            onClose = { expanded = false },
            onStop = onClose,
            onAddPlant = onAddPlant,
            onCaptureScreen = onCaptureScreen,
            onDrag = onDrag
        )
    }
}

@Composable
fun FloatingBall(
    plantCount: Int,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF4CAF50))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🌱", fontSize = 24.sp)
            if (plantCount > 0) {
                Text(
                    text = "$plantCount",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FloatingPanel(
    plants: List<Plant>,
    repository: PlantRepository,
    scope: CoroutineScope,
    onClose: () -> Unit,
    onStop: () -> Unit,
    onAddPlant: () -> Unit,
    onCaptureScreen: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    var showEditDialog by remember { mutableStateOf<Plant?>(null) }
    // 列表折叠状态：true=折叠（只显示标题栏），false=展开（默认）
    var listCollapsed by remember { mutableStateOf(false) }
    // 待审核植物数量
    val pendingCount = plants.count { it.isPendingReview }

    // 折叠列表时，如果有待审核植物，先取消它们的待审核状态
    fun handleCollapse() {
        if (pendingCount > 0) {
            // 批量取消所有待审核状态
            scope.launch {
                repository.confirmAllPendingReviews()
            }
        }
        listCollapsed = true
    }

    Card(
        modifier = Modifier
            .width(300.dp)
            .let { if (listCollapsed) it else it.heightIn(max = 450.dp) }
            .shadow(16.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FBE7))
    ) {
        Column {
            // 标题栏（可拖拽）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF4CAF50))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌱 我的植物园",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row {
                    // 折叠/展开列表按钮
                    IconButton(
                        onClick = {
                            if (listCollapsed) {
                                // 展开：直接展开
                                listCollapsed = false
                            } else {
                                // 折叠：如果是展开状态，处理待审核后折叠
                                handleCollapse()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (listCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (listCollapsed) "展开列表" else "折叠列表",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // 截图识别按钮
                    IconButton(
                        onClick = onCaptureScreen,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "截图识别",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onAddPlant,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加植物",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "收起",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 植物列表（折叠时只显示待审核植物，展开时显示全部）
            if (plants.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有植物 🪴", fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onAddPlant) {
                            Text("点击添加", color = Color(0xFF4CAF50))
                        }
                    }
                }
            } else {
                // 待审核植物列表（折叠时也显示）
                val pendingPlants = plants.filter { it.isPendingReview }
                val confirmedPlants = plants.filter { !it.isPendingReview }

                // 待审核区域
                if (pendingPlants.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 待审核标题
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "待审核 (${pendingPlants.size})",
                                fontSize = 11.sp,
                                color = Color(0xFFFF8F00),
                                fontWeight = FontWeight.Medium
                            )
                            if (listCollapsed) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "点击确认",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        pendingPlants.forEach { plant ->
                            PlantFloatItem(
                                plant = plant,
                                onEdit = {
                                    if (!listCollapsed) {
                                        // 展开状态下可以编辑
                                        showEditDialog = plant
                                    } else {
                                        // 折叠状态下点击直接确认审核
                                        scope.launch {
                                            repository.confirmReview(plant)
                                        }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        repository.deletePlant(plant)
                                    }
                                },
                                onConfirmReview = if (!listCollapsed) {
                                    {
                                        scope.launch {
                                            repository.confirmReview(plant)
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }

                // 已确认植物列表（折叠时隐藏）
                if (!listCollapsed && confirmedPlants.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 已确认标题
                        if (pendingPlants.isNotEmpty()) {
                            Text(
                                text = "已确认",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        confirmedPlants.forEach { plant ->
                            PlantFloatItem(
                                plant = plant,
                                onEdit = { showEditDialog = plant },
                                onDelete = {
                                    scope.launch {
                                        repository.deletePlant(plant)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 编辑对话框
    showEditDialog?.let { plant ->
        EditPlantDialog(
            plant = plant,
            onDismiss = { showEditDialog = null },
            onSave = { newName, newTimeText ->
                scope.launch {
                    val timeMillis = TimeParser.parseToMillis(newTimeText)
                    if (timeMillis != null) {
                        val updatedPlant = plant.copy(
                            name = newName,
                            matureAt = System.currentTimeMillis() + timeMillis,
                            // 编辑保存后取消待审核状态（视为已确认）
                            isPendingReview = false
                        )
                        repository.updatePlant(updatedPlant)
                    }
                    showEditDialog = null
                }
            },
            onDelete = {
                scope.launch {
                    repository.deletePlant(plant)
                    showEditDialog = null
                }
            }
        )
    }
}

@Composable
fun PlantFloatItem(
    plant: Plant,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConfirmReview: (() -> Unit)? = null  // 确认审核回调
) {
    val isMature = plant.isMature
    // 待审核植物用黄色背景突出显示
    val bgColor = when {
        plant.isPendingReview -> Color(0xFFFFF9C4)  // 浅黄色背景
        isMature -> Color(0xFFFFF3E0)
        else -> Color(0xFFE8F5E9)
    }
    val borderColor = when {
        plant.isPendingReview -> Color(0xFFFFC107)  // 黄色边框
        isMature -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = plant.emoji, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onEdit() }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plant.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 待审核标签
                if (plant.isPendingReview) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFC107),
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Text(
                            text = "待审核",
                            fontSize = 9.sp,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = plant.formatRemaining(),
                fontSize = 11.sp,
                color = if (isMature) Color(0xFFE65100) else Color(0xFF388E3C)
            )
        }
        if (isMature) {
            Text("🎉", fontSize = 16.sp)
        }
        // 待审核植物：显示确认按钮
        if (plant.isPendingReview && onConfirmReview != null) {
            IconButton(
                onClick = onConfirmReview,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "确认",
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF4CAF50)
                )
            }
        }
        // 删除按钮
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                modifier = Modifier.size(18.dp),
                tint = Color(0xFFE53935)
            )
        }
        // 编辑按钮
        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "编辑",
                modifier = Modifier.size(18.dp),
                tint = Color.Gray
            )
        }
    }
}

@Composable
fun EditPlantDialog(
    plant: Plant,
    onDismiss: () -> Unit,
    onSave: (name: String, timeText: String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(plant.name) }
    var timeText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // 使用自定义弹窗而不是 AlertDialog，避免 WindowManager BadTokenException
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(280.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "编辑植物",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("植物名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = timeText,
                    onValueChange = {
                        timeText = it
                        error = null
                    },
                    label = { Text("剩余时间 (如: 1小时15分钟)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = Color.Red) } }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.Red
                        )
                    ) {
                        Text("删除")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (name.isBlank()) {
                                error = "请输入植物名称"
                                return@TextButton
                            }
                            if (timeText.isBlank()) {
                                // 只修改名称，不修改时间
                                onSave(name, plant.formatRemaining())
                            } else {
                                val parsed = TimeParser.parseToMillis(timeText)
                                if (parsed == null) {
                                    error = "时间格式不正确"
                                } else {
                                    onSave(name, timeText)
                                }
                            }
                        }
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

// ── Lifecycle helpers for ComposeView in Service ────────────────────────────

class FloatingWindowLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }
}
