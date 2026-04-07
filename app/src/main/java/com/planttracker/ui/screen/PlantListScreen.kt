package com.planttracker.ui.screen

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.planttracker.data.model.Plant
import com.planttracker.service.FloatingWindowService
import com.planttracker.ui.viewmodel.PlantViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantListScreen(
    viewModel: PlantViewModel = hiltViewModel()
) {
    val plants by viewModel.plants.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showHarvestedTab by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 悬浮窗服务状态
    var isFloatingRunning by remember { mutableStateOf(false) }

    // 倒计时刷新状态，每秒更新一次
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            tick++
        }
    }

    // 显示设置页面
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🌱 植物种植记录", fontWeight = FontWeight.Bold)
                        Text(
                            "正在追踪 ${plants.size} 种植物",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // 设置按钮
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // 悬浮窗开关
                    IconButton(onClick = {
                        if (isFloatingRunning) {
                            FloatingWindowService.stop(context)
                            isFloatingRunning = false
                        } else {
                            // 检查悬浮窗权限
                            if (!Settings.canDrawOverlays(context)) {
                                // 没有权限，跳转到设置页面
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                FloatingWindowService.start(context)
                                isFloatingRunning = true
                            }
                        }
                    }) {
                        Icon(
                            if (isFloatingRunning) Icons.Default.PictureInPicture
                            else Icons.Default.PictureInPictureAlt,
                            contentDescription = if (isFloatingRunning) "关闭悬浮窗" else "开启悬浮窗",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加植物", tint = Color.White)
            }
        },
        bottomBar = {
            // Tab 切换
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Eco, contentDescription = "种植中") },
                    label = { Text("种植中 (${plants.size})") },
                    selected = !showHarvestedTab,
                    onClick = { showHarvestedTab = false }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Agriculture, contentDescription = "已收获") },
                    label = { Text("已收获") },
                    selected = showHarvestedTab,
                    onClick = { showHarvestedTab = true }
                )
            }
        }
    ) { padding ->
        if (showHarvestedTab) {
            HarvestedScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
        } else if (plants.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                onAddClick = { showAddDialog = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 统计卡片
                item {
                    StatsCard(plants = plants)
                }

                items(plants, key = { it.id }) { plant ->
                    PlantCard(
                        plant = plant,
                        onHarvest = { viewModel.harvestPlant(plant) },
                        onDelete = { viewModel.deletePlant(plant) }
                    )
                }
            }
        }

        // 添加植物弹窗
        if (showAddDialog) {
            AddPlantDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, emoji, matureAt, note ->
                    viewModel.addPlant(name, emoji, matureAt, note)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun StatsCard(plants: List<Plant>) {
    val matureCount = plants.count { it.isMature }
    val growingCount = plants.size - matureCount

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "生长中", value = "$growingCount", emoji = "🌿")
            StatItem(label = "已成熟", value = "$matureCount", emoji = "🎉")
            StatItem(label = "总计", value = "${plants.size}", emoji = "🌱")
        }
    }
}

@Composable
fun StatItem(label: String, value: String, emoji: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 24.sp)
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun PlantCard(
    plant: Plant,
    onHarvest: () -> Unit,
    onDelete: () -> Unit
) {
    val isMature = plant.isMature
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isMature) 6.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isMature)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 主信息行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(plant.emoji, fontSize = 36.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plant.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        // 使用 tick 触发重新计算倒计时
                        text = remember(tick) { plant.formatRemaining() },
                        fontSize = 13.sp,
                        color = if (isMature)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "记录时间 ${formatDate(plant.plantedAt)} · 预计 ${plant.formatMatureTime()} 收获",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                if (isMature) {
                    Text("🎉", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多操作"
                    )
                }
            }

            // 展开操作区
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (plant.note.isNotBlank()) {
                        Text(
                            text = plant.note,
                            modifier = Modifier.weight(1f),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 成熟按钮
                    if (isMature) {
                        FilledTonalButton(
                            onClick = {
                                onHarvest()
                                expanded = false
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("收获")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // 删除按钮
                    TextButton(
                        onClick = {
                            onDelete()
                            expanded = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除")
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier, onAddClick: () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🌱", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "还没有种植任何植物",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "点击下方按钮开始记录你的种植吧！",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加第一棵植物")
            }
        }
    }
}

@Composable
fun HarvestedScreen(
    viewModel: PlantViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val allPlants by viewModel.allPlants.collectAsState()
    val harvested = allPlants.filter { it.isHarvested }

    if (harvested.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🌾", fontSize = 64.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("还没有收获记录", fontSize = 16.sp, color = Color.Gray)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(harvested, key = { it.id }) { plant ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(plant.emoji, fontSize = 32.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(plant.name, fontWeight = FontWeight.Medium)
                            Text(
                                "种植 ${formatDate(plant.plantedAt)} → 成熟 ${formatDate(plant.matureAt)}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text("✅", fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

// ── 工具函数 ────────────────────────────────────────────────────────────────

private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

fun formatDate(timestamp: Long): String = dateFormat.format(Date(timestamp))
