package com.example.smartlogistics.ui.screens

import CongestionDetailCard
import RoadCongestionList
import TTITrendChart
import TimeRangeSelector
import android.Manifest
import android.R.attr.text
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.smartlogistics.ui.components.*
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.utils.HazmatRecognitionHelper
import com.example.smartlogistics.utils.HazmatRecognitionResult
import com.example.smartlogistics.utils.TFLiteHelper
import com.example.smartlogistics.utils.XunfeiSpeechHelper
import com.example.smartlogistics.viewmodel.MainViewModel
import com.example.smartlogistics.viewmodel.VehicleState
import com.example.smartlogistics.viewmodel.ReportState
import generateMockCongestionData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.location.AMapLocation
import kotlinx.coroutines.withContext
import java.net.URLEncoder

// ==================== 货运司机主页 ====================
@Composable
fun TruckHomeScreen(navController: NavController, viewModel: MainViewModel? = null) {
    val menuItems = listOf(
        MenuItem("车辆绑定", Icons.Rounded.LocalShipping, "truck_bind"),
        MenuItem("路线规划", Icons.Rounded.Route, "truck_route"),
        MenuItem("道路实况", Icons.Rounded.Traffic, "truck_road"),
        MenuItem("拥堵预测", Icons.Rounded.QueryStats, "truck_congestion"),
        MenuItem("历史数据", Icons.Rounded.History, "truck_history"),
        MenuItem("货物报备", Icons.Rounded.Assignment, "cargo_report")
    )
    
    val vehicles by viewModel?.vehicles?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val reports by viewModel?.reports?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    
    LaunchedEffect(Unit) {
        viewModel?.fetchVehicles()
        viewModel?.fetchReports()
    }
    
    Column(modifier = Modifier.fillMaxSize().background(BackgroundPrimary)) {
        DashboardHeader(
            title = "智运货车版",
            subtitle = "专业物流 · 高效运输",
            searchHint = "搜索仓库、货站、限行路段...",
            primaryColor = TruckOrange,
            gradientBrush = Brush.linearGradient(colors = listOf(TruckOrange, TruckOrangeDark)),
            onSearchClick = { navController.navigate("navigation_map") },
            onAiClick = { navController.navigate("ai_chat") }
        )
        
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val pendingReport = reports.firstOrNull { it.status == "pending" }
            if (pendingReport != null) {
                item {
                    PendingTaskCard(title = "待处理报备", description = "您有1条货物报备待确认", onClick = { navController.navigate("cargo_report") })
                }
            }
            
            item {
                QuickStatsCard(items = listOf("今日运单" to "12", "总里程" to "2,486km", "准时率" to "98%"), backgroundColor = TruckOrange)
            }
            
            item {
                Text(text = "常用功能", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    menuItems.chunked(2).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    FeatureCard(title = item.title, icon = item.icon, primaryColor = TruckOrange, badge = if (item.route == "cargo_report" && pendingReport != null) "1" else null, onClick = { navController.navigate(item.route) })
                                }
                            }
                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "最近报备", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            
            if (reports.isEmpty()) {
                item {
                    EmptyState(icon = Icons.Rounded.Assignment, title = "暂无报备记录", subtitle = "完成首次货物报备后将显示在这里", actionText = "去报备", onAction = { navController.navigate("cargo_report") })
                }
            } else {
                items(reports.take(3)) { report -> RecentReportCard(report = report) }
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun PendingTaskCard(title: String, description: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = WarningYellowLight)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Rounded.Warning, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(text = description, fontSize = 13.sp, color = TextSecondary)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun RecentReportCard(report: com.example.smartlogistics.network.CargoReport) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(if (report.cargoInfo.isHazardous) ErrorRedLight else TruckOrangeLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(imageVector = if (report.cargoInfo.isHazardous) Icons.Rounded.Warning else Icons.Rounded.Inventory, contentDescription = null, tint = if (report.cargoInfo.isHazardous) ErrorRed else TruckOrange, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = report.cargoInfo.cargoType, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(text = "目的地: ${report.destinationPoiId}", fontSize = 13.sp, color = TextSecondary)
            }
            StatusChip(text = when(report.status) { "pending" -> "待确认"; "confirmed" -> "已确认"; "completed" -> "已完成"; else -> report.status ?: "未知" }, color = when(report.status) { "pending" -> WarningYellow; "confirmed" -> InfoBlue; "completed" -> SuccessGreen; else -> TextSecondary })
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(text = text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

// ==================== 路况图例项 (私有) ====================
@Composable
private fun TruckTrafficLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(16.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
    }
}

// ==================== 车辆绑定页面 ====================
@Composable
fun TruckBindScreen(navController: NavController, viewModel: MainViewModel? = null) {
    val context = LocalContext.current

    // ========== 基础状态 ==========
    var plateNumber by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("truck") }
    var heightM by remember { mutableStateOf("") }
    var weightT by remember { mutableStateOf("") }
    var axleCount by remember { mutableStateOf("2") }
    val vehicleState by viewModel?.vehicleState?.collectAsState() ?: remember { mutableStateOf(VehicleState.Idle) }
    val vehicles by viewModel?.vehicles?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isLoading = vehicleState is VehicleState.Loading

    // ========== 车牌识别状态 ==========
    var isRecognizing by remember { mutableStateOf(false) }
    var recognitionResult by remember { mutableStateOf<String?>(null) }
    val tfliteHelper = remember { TFLiteHelper(context) }
    
    // ========== 生命周期安全的协程作用域 ==========
    val coroutineScope = rememberCoroutineScope()

    // =====================================================
    // 关键！！！所有 Launcher 必须在这里声明（DetailScreenTemplate 之前）
    // =====================================================

    // 1. 图片选择器 Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isRecognizing = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = tfliteHelper.loadImageFromUri(it)
                    val result = bitmap?.let { bmp -> tfliteHelper.recognizePlate(bmp) }
                    withContext(Dispatchers.Main) {
                        isRecognizing = false
                        result?.let { plate ->
                            plateNumber = plate
                            recognitionResult = "识别成功: $plate"
                        } ?: run {
                            recognitionResult = "识别失败，请重试"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isRecognizing = false
                        recognitionResult = "识别出错: ${e.message}"
                    }
                }
            }
        }
    }

    // 2. 相机拍照 Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            isRecognizing = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val result = tfliteHelper.recognizePlate(it)
                    withContext(Dispatchers.Main) {
                        isRecognizing = false
                        plateNumber = result
                        recognitionResult = "识别成功: $result"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isRecognizing = false
                        recognitionResult = "识别出错: ${e.message}"
                    }
                }
            }
        }
    }

    // 3. 相机权限 Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "需要相机权限才能拍照识别", Toast.LENGTH_SHORT).show()
        }
    }

    // =====================================================
    // Launcher 声明结束
    // =====================================================

    // 副作用
    LaunchedEffect(vehicleState) {
        if (vehicleState is VehicleState.BindSuccess) {
            plateNumber = ""
            heightM = ""
            weightT = ""
            recognitionResult = null
            viewModel?.resetVehicleState()
        }
    }

    DisposableEffect(Unit) {
        onDispose { tfliteHelper.close() }
    }

    // =====================================================
    // UI - DetailScreenTemplate 在这里开始
    // =====================================================
    DetailScreenTemplate(
        navController = navController,
        title = "车辆绑定",
        backgroundColor = BackgroundPrimary
    ) {
        val validVehicles = vehicles.filter {
            it.plateNumber.isNotBlank() && !it.plateNumber.contains("string", ignoreCase = true)
        }

        // ==================== 已绑定车辆列表 ====================
        if (validVehicles.isNotEmpty()) {
            Text(
                text = "已绑定车辆",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            validVehicles.forEach { vehicle ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(TruckOrangeLight, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocalShipping,
                                contentDescription = null,
                                tint = TruckOrange,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = vehicle.plateNumber,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = vehicle.vehicleType,
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                        IconButton(onClick = { vehicle.vehicleId?.let { viewModel?.unbindVehicle(it) } }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = ErrorRed
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ==================== 添加货运车辆 ====================
        Text(
            text = "添加货运车辆",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {

                // ========== AI智能识别车牌区域 ==========
                Text(
                    text = "智能识别车牌",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 拍照识别按钮 - 只调用 launch()，不声明 launcher
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                // 检查权限
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    cameraLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = TruckOrange.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                tint = TruckOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "拍照识别",
                                fontSize = 15.sp,
                                color = TruckOrange,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 相册选择按钮 - 只调用 launch()，不声明 launcher
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                imagePickerLauncher.launch("image/*")
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = TruckOrange.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Photo,
                                contentDescription = null,
                                tint = TruckOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "相册选择",
                                fontSize = 15.sp,
                                color = TruckOrange,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 识别状态显示
                if (isRecognizing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TruckOrange.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TruckOrange,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "正在识别车牌...", fontSize = 13.sp, color = TruckOrange)
                    }
                }

                // 识别结果显示
                recognitionResult?.let { result ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TruckOrange.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = TruckOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = result,
                            fontSize = 13.sp,
                            color = TruckOrange,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ========== 手动输入区域 ==========
                Text(
                    text = "或手动输入",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))

                StyledTextField(
                    value = plateNumber,
                    onValueChange = { plateNumber = it.uppercase() },
                    label = "车牌号",
                    leadingIcon = Icons.Rounded.Pin
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ========== 车辆类型选择 ==========
                Text(text = "车辆类型", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "truck" to "普通货车",
                        "hazmat" to "危化品车",
                        "refrigerated" to "冷链车"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = vehicleType == type,
                            onClick = { vehicleType = type },
                            label = { Text(label, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TruckOrange.copy(alpha = 0.2f),
                                selectedLabelColor = TruckOrange
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ========== 车辆参数 ==========
                Text(text = "车辆参数", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = heightM,
                        onValueChange = { heightM = it },
                        label = { Text("车高(米)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TruckOrange,
                            unfocusedBorderColor = BorderLight
                        )
                    )
                    OutlinedTextField(
                        value = weightT,
                        onValueChange = { weightT = it },
                        label = { Text("载重(吨)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TruckOrange,
                            unfocusedBorderColor = BorderLight
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ========== 轴数选择 ==========
                Text(text = "轴数", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("2", "3", "4", "5", "6+").forEach { count ->
                        FilterChip(
                            selected = axleCount == count,
                            onClick = { axleCount = count },
                            label = { Text("$count 轴") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TruckOrange.copy(alpha = 0.2f),
                                selectedLabelColor = TruckOrange
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ========== 绑定按钮 ==========
        PrimaryButton(
            text = "绑定车辆",
            onClick = {
                viewModel?.bindVehicle(
                    plateNumber,
                    vehicleType,
                    heightM.toDoubleOrNull(),
                    weightT.toDoubleOrNull(),
                    axleCount.replace("+", "").toIntOrNull()
                )
            },
            isLoading = isLoading,
            enabled = plateNumber.isNotBlank(),
            backgroundColor = TruckOrange,
            icon = Icons.Rounded.Add
        )
    }
}

// ==================== 路线规划页面 ====================
@Composable
fun TruckRouteScreen(navController: NavController, viewModel: MainViewModel? = null) {
    var destination by remember { mutableStateOf("") }
    
    DetailScreenTemplate(navController = navController, title = "路线规划", backgroundColor = BackgroundPrimary) {
        // 路线输入卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 起点
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(TruckOrange, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "我的位置", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    // 定位刷新按钮
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "定位",
                        tint = TruckOrange,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { /* 刷新定位 */ }
                    )
                }
                
                // 连接线
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp, top = 8.dp, bottom = 8.dp)
                        .width(2.dp)
                        .height(20.dp)
                        .background(BorderLight)
                )
                
                // 终点输入
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(ErrorRed, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it },
                        placeholder = { Text("输入仓库、货站名称", color = TextTertiary) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TruckOrange,
                            unfocusedBorderColor = BorderLight
                        ),
                        singleLine = true,
                        trailingIcon = {
                            // 语音输入按钮
                            IconButton(
                                onClick = { navController.navigate("ai_chat") }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "语音输入",
                                    tint = TruckOrange,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // 常用目的地
        Text(
            text = "常用目的地",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // 常用目的地列表
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TruckQuickDestinationItem(
                icon = Icons.Rounded.Warehouse,
                title = "1号仓库",
                subtitle = "北京市朝阳区物流园区",
                onClick = { 
                    val encodedDest = Uri.encode("北京市朝阳区物流园区 1号仓库")
                    navController.navigate("navigation_map?destination=$encodedDest")
                }
            )
            TruckQuickDestinationItem(
                icon = Icons.Rounded.LocalShipping,
                title = "3号货站",
                subtitle = "北京市大兴区货运中心",
                onClick = { 
                    val encodedDest = Uri.encode("北京市大兴区货运中心 3号货站")
                    navController.navigate("navigation_map?destination=$encodedDest")
                }
            )
            TruckQuickDestinationItem(
                icon = Icons.Rounded.Factory,
                title = "集装箱堆场",
                subtitle = "天津港保税区",
                onClick = { 
                    val encodedDest = Uri.encode("天津港保税区 集装箱堆场")
                    navController.navigate("navigation_map?destination=$encodedDest")
                }
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // 提示卡片
        TipCard(
            text = "系统将自动避开限高、限重路段，并考虑危化品车辆通行限制。",
            icon = Icons.Rounded.Info,
            backgroundColor = TruckOrangeLight,
            iconColor = TruckOrange
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 开始导航按钮
        PrimaryButton(
            text = "开始导航",
            onClick = { 
                val encodedDest = Uri.encode(destination)
                navController.navigate("navigation_map?destination=$encodedDest") 
            },
            enabled = destination.isNotBlank(),
            backgroundColor = TruckOrange,
            icon = Icons.Rounded.Navigation
        )
    }
}

// 货运快捷目的地项
@Composable
private fun TruckQuickDestinationItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(TruckOrangeLight, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TruckOrange,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ==================== 道路实况页面 ====================

// 货车路段数据模型
data class TruckRoadSegment(
    val id: String,
    val name: String,
    val distance: String,
    val estimatedTime: String,
    val congestionLevel: TruckRoadCongestionLevel,
    val description: String,
    val avgSpeed: String,
    val truckRestriction: String? = null // 货车限制信息
)

// 货车拥堵等级枚举
enum class TruckRoadCongestionLevel(val label: String, val color: Color, val textColor: Color) {
    FREE("畅通", CongestionFree, CongestionFree),
    LIGHT("缓行", CongestionLight, Color(0xFFB8860B)),
    MODERATE("拥堵", CongestionModerate, CongestionModerate),
    SEVERE("严重", CongestionSevere, CongestionSevere)
}

@Composable
fun TruckRoadScreen(navController: NavController, viewModel: MainViewModel? = null) {
    // 状态管理
    var isRefreshing by remember { mutableStateOf(false) }
    var lastUpdateTime by remember { mutableStateOf("刚刚更新") }
    var selectedSegment by remember { mutableStateOf<TruckRoadSegment?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var aMapInstance by remember { mutableStateOf<AMap?>(null) }
    var currentLocation by remember { mutableStateOf<AMapLocation?>(null) }
    
    // 模拟货运路段数据（后端接入后替换为真实数据）
    val roadSegments = remember {
        listOf(
            TruckRoadSegment("1", "北门闸口通道", "2.5km", "约6分钟", TruckRoadCongestionLevel.FREE, "道路通畅，货车可正常通行", "50km/h", "限高4.5米"),
            TruckRoadSegment("2", "3号仓库入口", "1.2km", "约5分钟", TruckRoadCongestionLevel.LIGHT, "等待装卸货车较多，请耐心排队", "25km/h", "限载30吨"),
            TruckRoadSegment("3", "危化品专用道", "3.8km", "约8分钟", TruckRoadCongestionLevel.FREE, "专用通道畅通，请持证通行", "45km/h", "需危化品通行证"),
            TruckRoadSegment("4", "集装箱堆场通道", "1.5km", "约12分钟", TruckRoadCongestionLevel.SEVERE, "集卡排队严重，建议绕行南门", "10km/h", "仅限集装箱车辆"),
            TruckRoadSegment("5", "南门出口通道", "2.0km", "约5分钟", TruckRoadCongestionLevel.LIGHT, "出场车辆较多，注意前方减速", "30km/h"),
            TruckRoadSegment("6", "冷链物流通道", "1.8km", "约4分钟", TruckRoadCongestionLevel.FREE, "冷链专用道畅通", "40km/h", "冷链车辆优先"),
            TruckRoadSegment("7", "园区环路主干道", "4.5km", "约10分钟", TruckRoadCongestionLevel.MODERATE, "高峰期车流量大，注意货车限速", "25km/h", "限速40km/h")
        )
    }
    
    // 刷新数据
    val scope = rememberCoroutineScope()
    fun refreshData() {
        scope.launch {
            isRefreshing = true
            delay(1500) // 模拟网络请求
            lastUpdateTime = "刚刚更新"
            isRefreshing = false
        }
    }
    
    // 定位到当前位置
    fun locateToCurrentPosition() {
        currentLocation?.let { location ->
            aMapInstance?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    16f
                )
            )
        }
    }

    Scaffold(
        topBar = {
            // 自定义顶部栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 返回按钮
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary
                        )
                    }
                    
                    // 标题
                    Text(
                        text = "道路实况",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    
                    // 刷新按钮
                    IconButton(
                        onClick = { refreshData() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = TruckOrange,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新",
                                tint = TruckOrange
                            )
                        }
                    }
                }
            }
        },
        containerColor = BackgroundPrimary
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 地图区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    // 高德地图
                    AMapView(
                        modifier = Modifier.fillMaxSize(),
                        showTraffic = true,
                        showMyLocation = true,
                        onMapReady = { map ->
                            aMapInstance = map
                        },
                        onLocationChanged = { location ->
                            currentLocation = location
                            // 首次定位移动到当前位置
                            if (currentLocation == null) {
                                aMapInstance?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(location.latitude, location.longitude),
                                        15f
                                    )
                                )
                            }
                        }
                    )
                    
                    // 定位按钮
                    FloatingActionButton(
                        onClick = { locateToCurrentPosition() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(44.dp),
                        containerColor = Color.White,
                        contentColor = TruckOrange,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = "定位",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    
                    // 更新时间标签
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        color = Color.White.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = lastUpdateTime,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                // 路况图例
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TruckTrafficLegendItem(color = CongestionFree, label = "畅通")
                        TruckTrafficLegendItem(color = CongestionLight, label = "缓行")
                        TruckTrafficLegendItem(color = CongestionModerate, label = "拥堵")
                        TruckTrafficLegendItem(color = CongestionSevere, label = "严重")
                    }
                }
                
                // 路段列表标题
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "货运通道",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "共${roadSegments.size}条通道",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
                
                // 路段列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(roadSegments) { segment ->
                        TruckRoadSegmentCard(
                            segment = segment,
                            onClick = {
                                selectedSegment = segment
                                showDetailDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
    
    // 路段详情弹窗
    if (showDetailDialog && selectedSegment != null) {
        TruckRoadSegmentDetailDialog(
            segment = selectedSegment!!,
            onDismiss = { showDetailDialog = false },
            onNavigate = { segment ->
                // 跳转到导航页面，传入目的地名称
                val encodedDest = Uri.encode(segment.name)
                navController.navigate("navigation_map?destination=$encodedDest")
                showDetailDialog = false
            }
        )
    }
}

// 货车路段卡片组件
@Composable
private fun TruckRoadSegmentCard(
    segment: TruckRoadSegment,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拥堵等级指示器
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .background(
                        color = segment.congestionLevel.color,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 路段信息
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = segment.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    // 货车限制标签
                    segment.truckRestriction?.let { restriction ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = TruckOrangeLight
                        ) {
                            Text(
                                text = restriction,
                                fontSize = 10.sp,
                                color = TruckOrange,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Route,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = segment.distance,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = segment.estimatedTime,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
            
            // 拥堵状态标签
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = segment.congestionLevel.color.copy(alpha = 0.15f)
            ) {
                Text(
                    text = segment.congestionLevel.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = segment.congestionLevel.textColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 箭头指示
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "查看详情",
                tint = TextTertiary
            )
        }
    }
}

// 货车路段详情弹窗
@Composable
private fun TruckRoadSegmentDetailDialog(
    segment: TruckRoadSegment,
    onDismiss: () -> Unit,
    onNavigate: (TruckRoadSegment) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "通道详情",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 路段名称和状态
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = segment.congestionLevel.color,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = segment.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 详情信息
                TruckDetailInfoRow(label = "当前状态", value = segment.congestionLevel.label, valueColor = segment.congestionLevel.textColor)
                TruckDetailInfoRow(label = "通道长度", value = segment.distance)
                TruckDetailInfoRow(label = "预计用时", value = segment.estimatedTime)
                TruckDetailInfoRow(label = "平均车速", value = segment.avgSpeed)
                segment.truckRestriction?.let {
                    TruckDetailInfoRow(label = "通行限制", value = it, valueColor = TruckOrange)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 路况描述
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = BackgroundPrimary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = TruckOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = segment.description,
                            fontSize = 14.sp,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 导航按钮
                Button(
                    onClick = { onNavigate(segment) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TruckOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "开始导航",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// 货车详情信息行
@Composable
private fun TruckDetailInfoRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = TextSecondary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

// ==================== 拥堵预测页面 ====================
@Composable
fun TruckCongestionScreen(navController: NavController, viewModel: MainViewModel? = null) {
    // 数据状态
    val congestionData = remember { generateMockCongestionData() }
    var selectedTimeRange by remember { mutableStateOf("今天") }
    var selectedDataIndex by remember { mutableStateOf(10) } // 默认选中16:00

    // 模拟货运主干道数据
    val truckRoads = remember {
        listOf(
            Triple("北门闸口通道", "2.5km", CongestionLevel.MODERATE),
            Triple("3号仓库入口", "1.2km", CongestionLevel.LIGHT),
            Triple("危化品专用道", "3.8km", CongestionLevel.FREE),
            Triple("集装箱堆场通道", "1.5km", CongestionLevel.SEVERE),
            Triple("南门出口通道", "2.0km", CongestionLevel.LIGHT)
        )
    }

    DetailScreenTemplate(
        navController = navController,
        title = "拥堵预测",
        backgroundColor = BackgroundPrimary
    ) {
        // 时间选择器
        TimeRangeSelector(
            selectedRange = selectedTimeRange,
            onRangeSelected = { selectedTimeRange = it },
            primaryColor = TruckOrange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 图表卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "货运主干道拥堵趋势",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    // 图例
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CongestionLevel.values().take(3).forEach { level ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(level.color, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = level.label,
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // TTI趋势图
                TTITrendChart(
                    data = congestionData,
                    selectedIndex = selectedDataIndex,
                    onPointSelected = { selectedDataIndex = it },
                    primaryColor = TruckOrange
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 选中时间点详情
        CongestionDetailCard(
            dataPoint = congestionData[selectedDataIndex],
            primaryColor = TruckOrange
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 智能建议
        val selectedData = congestionData[selectedDataIndex]
        val suggestion = when (selectedData.level) {
            CongestionLevel.FREE -> "当前时段路况畅通，建议此时出行。"
            CongestionLevel.LIGHT -> "轻微缓行，预计延误5-10分钟。"
            CongestionLevel.MODERATE -> "建议避开此时段，或选择备用路线。"
            CongestionLevel.SEVERE -> "严重拥堵！建议推迟1-2小时出发。"
        }

        TipCard(
            text = suggestion,
            icon = Icons.Rounded.Lightbulb,
            backgroundColor = TruckOrangeLight,
            iconColor = TruckOrange
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 各路段拥堵状态
        Text(
            text = "货运通道实时状态",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        RoadCongestionList(
            roads = truckRoads,
            primaryColor = TruckOrange
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 开始导航按钮
        PrimaryButton(
            text = "规划最优路线",
            onClick = { navController.navigate("navigation_map") },
            backgroundColor = TruckOrange,
            icon = Icons.Rounded.Navigation
        )
    }
}

// ==================== 历史数据页面 ====================
@Composable
fun TruckHistoryScreen(navController: NavController, viewModel: MainViewModel? = null) {
    // 模拟历史数据
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("本周", "本月", "全部")

    val historyRecords = remember {
        listOf(
            HistoryRecord("2024-12-15", "3号仓库 → 北门出口", "普通货物", 15.5, "32分钟", "已完成"),
            HistoryRecord("2024-12-14", "危化品区 → 南门闸口", "危险品", 8.2, "28分钟", "已完成"),
            HistoryRecord("2024-12-14", "集装箱堆场 → 2号仓库", "集装箱", 12.0, "45分钟", "已完成"),
            HistoryRecord("2024-12-13", "冷链区 → 北门出口", "冷链货物", 18.3, "38分钟", "已完成"),
            HistoryRecord("2024-12-12", "1号仓库 → 3号仓库", "普通货物", 5.5, "15分钟", "已完成"),
            HistoryRecord("2024-12-11", "南门入口 → 危化品区", "危险品", 10.8, "35分钟", "已完成")
        )
    }

    DetailScreenTemplate(
        navController = navController,
        title = "历史数据",
        backgroundColor = BackgroundPrimary
    ) {
        // 统计卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = TruckOrange)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "本月运输统计",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = "156", label = "运输单数", color = Color.White)
                    StatItem(value = "2,486", label = "总里程(km)", color = Color.White)
                    StatItem(value = "98%", label = "准时率", color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = "45", label = "普通货物", color = Color.White)
                    StatItem(value = "28", label = "冷链货物", color = Color.White)
                    StatItem(value = "12", label = "危险品", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tab 选择器
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                FilterChip(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    label = {
                        Text(
                            text = tab,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TruckOrange.copy(alpha = 0.15f),
                        selectedLabelColor = TruckOrange
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 历史记录列表
        Text(
            text = "运输记录",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        historyRecords.forEach { record ->
            HistoryRecordCard(
                record = record,
                primaryColor = TruckOrange,
                isHazardous = record.cargoType == "危险品"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 加载更多
        TextButton(
            onClick = { /* TODO: 加载更多 */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "加载更多记录",
                color = TruckOrange
            )
        }
    }
}

// ==================== 历史记录数据模型 ====================
data class HistoryRecord(
    val date: String,
    val route: String,
    val cargoType: String,
    val distance: Double,
    val duration: String,
    val status: String
)

// ==================== 统计项组件 ====================
@Composable
private fun StatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = color.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
    }
}

// ==================== 历史记录卡片 ====================
@Composable
private fun HistoryRecordCard(
    record: HistoryRecord,
    primaryColor: Color,
    isHazardous: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isHazardous) ErrorRedLight else primaryColor.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isHazardous) Icons.Rounded.Warning else Icons.Rounded.LocalShipping,
                    contentDescription = null,
                    tint = if (isHazardous) ErrorRed else primaryColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.route,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.date,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(TextTertiary, CircleShape)
                    )

                    Text(
                        text = record.cargoType,
                        fontSize = 12.sp,
                        color = if (isHazardous) ErrorRed else TextSecondary
                    )
                }
            }

            // 距离和时间
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${record.distance}km",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor
                )
                Text(
                    text = record.duration,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

// ==================== 货物报备页面 ====================
@Composable
fun CargoReportScreen(navController: NavController, viewModel: MainViewModel? = null) {
    val context = LocalContext.current

    // 表单状态
    var cargoType by remember { mutableStateOf("普通货物") }
    var isHazardous by remember { mutableStateOf(false) }
    var hazardClass by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val reportState by viewModel?.reportState?.collectAsState() ?: remember { mutableStateOf(ReportState.Idle) }
    val vehicles by viewModel?.vehicles?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    var selectedVehicleId by remember { mutableStateOf("") }
    val isLoading = reportState is ReportState.Loading

    // ========== 语音识别相关状态 ==========
    val speechHelper = remember { XunfeiSpeechHelper() }
    val speechState by speechHelper.state.collectAsState()
    val volumeLevel by speechHelper.volumeLevel.collectAsState()
    val partialText by speechHelper.partialText.collectAsState()
    var showVoiceDialog by remember { mutableStateOf(false) }

    // 权限处理
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasRecordPermission = isGranted
            if (isGranted) {
                showVoiceDialog = true
                speechHelper.startListening()
            } else {
                Toast.makeText(context, "需要录音权限才能使用语音功能", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // 处理语音识别结果 - 智能解析并填充表单
    LaunchedEffect(speechState) {
        when (speechState) {
            is XunfeiSpeechHelper.SpeechState.Result -> {
                val text = (speechState as XunfeiSpeechHelper.SpeechState.Result).text
                if (text.isNotBlank()) {
                    // 智能解析语音内容
                    val parsed = parseVoiceContent(text)

                    // 填充解析结果到表单
                    parsed.cargoType?.let {
                        cargoType = it
                        isHazardous = it == "危险品"
                    }
                    parsed.weight?.let { weight = it }
                    parsed.destination?.let { destination = it }
                    parsed.hazardClass?.let { hazardClass = it }
                    parsed.description?.let { description = it }

                    // 显示解析结果提示
                    val filledFields = mutableListOf<String>()
                    if (parsed.cargoType != null) filledFields.add("货物类型")
                    if (parsed.weight != null) filledFields.add("重量")
                    if (parsed.destination != null) filledFields.add("目的地")

                    if (filledFields.isNotEmpty()) {
                        Toast.makeText(
                            context,
                            "已识别: ${filledFields.joinToString("、")}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                showVoiceDialog = false
                speechHelper.resetState()
            }
            is XunfeiSpeechHelper.SpeechState.Error -> {
                val error = (speechState as XunfeiSpeechHelper.SpeechState.Error).message
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                showVoiceDialog = false
                speechHelper.resetState()
            }
            else -> {}
        }
    }
    // ========== 语音识别相关状态结束 ==========

    // ========== 危化品识别相关状态 ==========
    val hazmatHelper = remember { HazmatRecognitionHelper(context) }
    var isHazmatRecognizing by remember { mutableStateOf(false) }
    var hazmatRecognitionResult by remember { mutableStateOf<HazmatRecognitionResult?>(null) }

    // 危化品图片选择器
    val hazmatImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isHazmatRecognizing = true
            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = hazmatHelper.loadImageFromUri(it)
                val result = bitmap?.let { bmp -> hazmatHelper.recognizeHazmat(bmp) }

                withContext(Dispatchers.Main) {
                    isHazmatRecognizing = false
                    result?.let { res ->
                        hazmatRecognitionResult = res
                        res.hazmatClass?.let { hazardClass = it.name }
                    }
                }
            }
        }
    }

    // 危化品相机拍照
    val hazmatCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            isHazmatRecognizing = true
            CoroutineScope(Dispatchers.IO).launch {
                val result = hazmatHelper.recognizeHazmat(it)

                withContext(Dispatchers.Main) {
                    isHazmatRecognizing = false
                    hazmatRecognitionResult = result
                    result.hazmatClass?.let { hazardClass = it.name }
                }
            }
        }
    }

    // 危化品相机权限
    val hazmatCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hazmatCameraLauncher.launch(null)
        }
    }

    // 在 DisposableEffect 中添加清理
    DisposableEffect(Unit) {
        onDispose {
            speechHelper.destroy()
            hazmatHelper.close()  // 添加这行
        }
    }
    // ========== 危化品识别相关状态结束 ==========

    LaunchedEffect(reportState) {
        if (reportState is ReportState.SubmitSuccess) {
            navController.popBackStack()
        }
    }

    DetailScreenTemplate(navController = navController, title = "货物报备", backgroundColor = BackgroundPrimary) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 顶部标题行：选择车辆 + 语音填写按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "选择车辆", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)

                    // 语音填写按钮 - 改为触发语音识别
                    Surface(
                        modifier = Modifier.clickable {
                            if (hasRecordPermission) {
                                showVoiceDialog = true
                                speechHelper.startListening()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = TruckOrange.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = TruckOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "语音填写",
                                fontSize = 12.sp,
                                color = TruckOrange,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 过滤无效车辆数据
                val validVehicles = vehicles.filter {
                    it.plateNumber.isNotBlank() && !it.plateNumber.contains("string", ignoreCase = true)
                }

                if (validVehicles.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth().clickable { navController.navigate("truck_bind") }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Rounded.Add, contentDescription = null, tint = TruckOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "请先绑定车辆", color = TruckOrange, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(validVehicles) { vehicle ->
                            FilterChip(
                                selected = selectedVehicleId == vehicle.vehicleId,
                                onClick = { selectedVehicleId = vehicle.vehicleId ?: "" },
                                label = { Text(vehicle.plateNumber) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TruckOrange.copy(alpha = 0.2f),
                                    selectedLabelColor = TruckOrange
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "货物类型", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("普通货物", "冷链货物", "危险品").forEach { type ->
                        FilterChip(
                            selected = cargoType == type,
                            onClick = { cargoType = type; isHazardous = type == "危险品" },
                            label = { Text(type) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (type == "危险品") ErrorRed.copy(alpha = 0.2f) else TruckOrange.copy(alpha = 0.2f),
                                selectedLabelColor = if (type == "危险品") ErrorRed else TruckOrange
                            )
                        )
                    }
                }

                if (isHazardous) {
                    Spacer(modifier = Modifier.height(16.dp))

                    TipCard(
                        text = "危化品运输需要特殊审批，请先识别或填写危化品类别。",
                        icon = Icons.Rounded.Warning,
                        backgroundColor = ErrorRedLight,
                        iconColor = ErrorRed
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ========== 危化品智能识别卡片（简化版，不含 launcher） ==========
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // 标题行
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "智能识别危化品标识",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )

                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 拍照/相册按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 拍照识别
                                OutlinedButton(
                                    onClick = {
                                        when (PackageManager.PERMISSION_GRANTED) {
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.CAMERA
                                            ) -> hazmatCameraLauncher.launch(null)
                                            else -> hazmatCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ErrorRed)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CameraAlt,
                                        contentDescription = null,
                                        tint = ErrorRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("拍照识别", color = ErrorRed, fontSize = 13.sp)
                                }

                                // 相册选择
                                OutlinedButton(
                                    onClick = { hazmatImagePickerLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ErrorRed)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Photo,
                                        contentDescription = null,
                                        tint = ErrorRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("相册选择", color = ErrorRed, fontSize = 13.sp)
                                }
                            }

                            // 识别中状态
                            if (isHazmatRecognizing) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ErrorRed.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = ErrorRed,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("正在识别危化品标识...", fontSize = 13.sp, color = ErrorRed)
                                }
                            }

                            // 识别结果显示
                            hazmatRecognitionResult?.let { result ->
                                result.hazmatClass?.let { hazmatClass ->
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(hazmatClass.colorInt).copy(alpha = 0.15f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 危化品类别图标
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(Color(hazmatClass.colorInt), RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = hazmatClass.code,
                                                    color = Color.White,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = hazmatClass.name,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "置信度: ${(result.confidence * 100).toInt()}%",
                                                    fontSize = 12.sp,
                                                    color = TextSecondary
                                                )
                                            }

                                            // 已识别标识
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(hazmatClass.colorInt),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 手动输入（识别后可修改）
                    StyledTextField(
                        value = hazardClass,
                        onValueChange = { hazardClass = it },
                        label = "危化品类别（可手动修改）",
                        leadingIcon = Icons.Rounded.Warning
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                StyledTextField(value = weight, onValueChange = { weight = it }, label = "货物重量 (吨)", leadingIcon = Icons.Rounded.Scale, keyboardType = KeyboardType.Decimal)
                Spacer(modifier = Modifier.height(16.dp))
                StyledTextField(value = destination, onValueChange = { destination = it }, label = "目的地", leadingIcon = Icons.Rounded.LocationOn)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 语音提示卡片
        TipCard(
            text = "点击「语音填写」，说出货物信息，例如：10吨普通货物送到3号仓库",
            icon = Icons.Rounded.Lightbulb,
            backgroundColor = TruckOrangeLight,
            iconColor = TruckOrange
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 提交报备按钮
        PrimaryButton(
            text = "提交报备",
            onClick = {
                viewModel?.submitReport(
                    selectedVehicleId,
                    destination,
                    cargoType,
                    isHazardous,
                    hazardClass.ifBlank { null },
                    weight.toDoubleOrNull(),
                    description.ifBlank { null }
                )
            },
            isLoading = isLoading,
            enabled = selectedVehicleId.isNotBlank() && destination.isNotBlank(),
            backgroundColor = TruckOrange,
            icon = Icons.Rounded.Send
        )
    }

    // 语音识别弹窗
    if (showVoiceDialog) {
        CargoVoiceDialog(
            partialText = partialText,
            volumeLevel = volumeLevel,
            isListening = speechState is XunfeiSpeechHelper.SpeechState.Listening,
            isProcessing = speechState is XunfeiSpeechHelper.SpeechState.Processing,
            onFinish = { speechHelper.stopListening() },
            onCancel = {
                speechHelper.cancel()
                showVoiceDialog = false
            }
        )
    }
}

// ==================== 语音内容解析结果 ====================
private data class ParsedCargoInfo(
    val cargoType: String? = null,
    val weight: String? = null,
    val destination: String? = null,
    val hazardClass: String? = null,
    val description: String? = null
)

// ==================== 智能解析语音内容 ====================
private fun parseVoiceContent(text: String): ParsedCargoInfo {
    var cargoType: String? = null
    var weight: String? = null
    var destination: String? = null
    var hazardClass: String? = null
    var description: String? = null

    val lowerText = text.lowercase()

    // 解析货物类型
    when {
        lowerText.contains("危险") || lowerText.contains("危化") || lowerText.contains("化学") -> {
            cargoType = "危险品"
            // 尝试提取危化品类别
            val hazardPatterns = listOf(
                "易燃", "易爆", "腐蚀", "有毒", "放射",
                "氧化", "压缩气体", "液化气体"
            )
            hazardPatterns.forEach { pattern ->
                if (lowerText.contains(pattern)) {
                    hazardClass = pattern
                }
            }
        }
        lowerText.contains("冷链") || lowerText.contains("冷藏") ||
                lowerText.contains("生鲜") || lowerText.contains("冷冻") -> {
            cargoType = "冷链货物"
        }
        lowerText.contains("普通") || lowerText.contains("一般") -> {
            cargoType = "普通货物"
        }
    }

    // 解析重量 - 支持多种表达方式
    val weightPatterns = listOf(
        Regex("(\\d+(?:\\.\\d+)?)\\s*吨"),
        Regex("(\\d+(?:\\.\\d+)?)\\s*t", RegexOption.IGNORE_CASE),
        Regex("(\\d+(?:\\.\\d+)?)\\s*公斤").let { regex ->
            regex.find(text)?.let { match ->
                val kg = match.groupValues[1].toDoubleOrNull() ?: 0.0
                return@let Regex("").also { weight = String.format("%.2f", kg / 1000) }
            }
            null
        }
    )

    if (weight == null) {
        weightPatterns.filterNotNull().forEach { pattern ->
            pattern.find(text)?.let { match ->
                weight = match.groupValues[1]
                return@forEach
            }
        }
    }

    // 解析目的地
    val destinationPatterns = listOf(
        Regex("(?:送到|运到|去|到达?|目的地[是为]?)\\s*([\\u4e00-\\u9fa5A-Za-z0-9]+(?:号)?(?:仓库|货站|门|闸口|停车场|园区)?)"),
        Regex("([\\u4e00-\\u9fa5]+(?:号)?仓库)"),
        Regex("([\\u4e00-\\u9fa5]+货站)"),
        Regex("([A-Za-z]?\\d+号?)\\s*(?:仓|库|门|闸)")
    )

    destinationPatterns.forEach { pattern ->
        pattern.find(text)?.let { match ->
            val dest = match.groupValues[1].trim()
            if (dest.isNotBlank() && dest.length >= 2) {
                destination = dest
                return@forEach
            }
        }
    }

    // 如果没有匹配到特定格式，尝试提取包含"仓库"、"货站"等关键词的部分
    if (destination == null) {
        val keywords = listOf("仓库", "货站", "号门", "闸口", "停车场", "园区")
        keywords.forEach { keyword ->
            if (text.contains(keyword)) {
                val index = text.indexOf(keyword)
                val start = maxOf(0, index - 5)
                val end = minOf(text.length, index + keyword.length)
                destination = text.substring(start, end).trim()
                return@forEach
            }
        }
    }

    return ParsedCargoInfo(
        cargoType = cargoType,
        weight = weight,
        destination = destination,
        hazardClass = hazardClass,
        description = description
    )
}

// ==================== 货物报备语音弹窗 ====================
@Composable
private fun CargoVoiceDialog(
    partialText: String,
    volumeLevel: Float,
    isListening: Boolean,
    isProcessing: Boolean,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题
                Text(
                    text = "语音填写报备信息",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "请说出货物类型、重量、目的地",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 麦克风图标区域
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 外圈 - 根据音量变化
                    if (isListening) {
                        val animatedSize by animateFloatAsState(
                            targetValue = 80f + volumeLevel * 20f,
                            animationSpec = tween(100),
                            label = "size"
                        )
                        Box(
                            modifier = Modifier
                                .size(animatedSize.dp)
                                .background(
                                    TruckOrange.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        )
                    }

                    // 麦克风图标
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(TruckOrange, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 音量指示条
                if (isListening) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(24.dp)
                    ) {
                        repeat(5) { index ->
                            val baseHeight = when (index) {
                                2 -> 20f
                                1, 3 -> 14f
                                else -> 8f
                            }
                            val height = baseHeight * (0.4f + volumeLevel * 0.6f)

                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(height.dp)
                                    .background(
                                        TruckOrange.copy(alpha = 0.6f + volumeLevel * 0.4f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 实时识别文字显示区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 120.dp)
                        .background(
                            Color(0xFFF5F5F5),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (partialText.isNotBlank()) {
                        Text(
                            text = partialText,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when {
                                    isProcessing -> "识别中..."
                                    isListening -> "请说话..."
                                    else -> "准备中..."
                                },
                                fontSize = 14.sp,
                                color = TextTertiary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "例如：10吨普通货物送到3号仓库",
                                fontSize = 12.sp,
                                color = TextTertiary.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 取消按钮
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Text("取消", fontSize = 14.sp)
                    }

                    // 完成按钮
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TruckOrange
                        ),
                        enabled = isListening
                    ) {
                        Text("完成", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}