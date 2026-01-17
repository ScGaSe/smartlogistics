package com.example.smartlogistics.ui.screens

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
import com.example.smartlogistics.utils.HazmatClass
import com.example.smartlogistics.utils.XunfeiSpeechHelper
import com.example.smartlogistics.utils.CameraUtils
import com.example.smartlogistics.viewmodel.MainViewModel
import com.example.smartlogistics.viewmodel.VehicleState
import com.example.smartlogistics.viewmodel.ReportState
import com.example.smartlogistics.viewmodel.CongestionState
import com.example.smartlogistics.network.CongestionResponse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.location.AMapLocation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URLEncoder

// ==================== 车型英文转中文映射 ====================
private fun mapVehicleTypeToCn(vehicleType: String?): String {
    return when (vehicleType?.lowercase()) {
        "truck" -> "货车"
        "bus" -> "客车"
        "car", "sedan" -> "轿车"
        "suv" -> "SUV"
        "van" -> "面包车"
        "pickup" -> "皮卡"
        "motorcycle" -> "摩托车"
        "minibus" -> "小型客车"
        else -> vehicleType ?: "未知"
    }
}

// ==================== 将后端车型映射到货车版前端选项 ====================
// 货车版支持的选项: truck(卡车), van(小型货车)
private fun mapVehicleTypeToTruckOption(vehicleType: String?): String {
    return when (vehicleType?.lowercase()) {
        "truck", "pickup" -> "truck"           // 卡车、皮卡 -> 卡车
        "van", "minibus" -> "van"              // 面包车、小型客车 -> 小型货车
        "bus" -> "truck"                       // 客车 -> 卡车（大型）
        else -> "truck"                        // 默认选择卡车
    }
}

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

            // 统计数据从实际报备记录计算
            item {
                val todayReports = reports.size.toString()
                QuickStatsCard(
                    items = listOf(
                        "今日报备" to todayReports,
                        "待处理" to reports.count { it.status == "pending" }.toString(),
                        "已完成" to reports.count { it.status == "completed" }.toString()
                    ),
                    backgroundColor = TruckOrange
                )
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

    // ========== Repository用于调用后端API ==========
    val repository = remember { com.example.smartlogistics.network.Repository(context) }

    // ========== 相机拍照 Uri（重要！FileProvider 需要）==========
    var photoUri by remember { mutableStateOf<Uri?>(null) }

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
                    // 将Uri转换为临时文件
                    val inputStream = context.contentResolver.openInputStream(it)
                    val tempFile = java.io.File(context.cacheDir, "temp_plate_image.jpg")
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    // 调用后端API识别车牌和车型
                    when (val result = repository.analyzeVehicleImage(tempFile)) {
                        is com.example.smartlogistics.network.NetworkResult.Success -> {
                            val response = result.data
                            val plate = response.licensePlate?.text
                            val detectedVehicleType = response.vehicleType?.vehicleClass
                            withContext(Dispatchers.Main) {
                                isRecognizing = false
                                if (plate != null) {
                                    plateNumber = plate
                                    recognitionResult = "识别成功: $plate"
                                }
                                // 自动填充车型 - 映射到前端选项
                                detectedVehicleType?.let { vt ->
                                    vehicleType = mapVehicleTypeToTruckOption(vt)
                                    if (plate != null) {
                                        recognitionResult = "识别成功: $plate (${mapVehicleTypeToCn(vt)})"
                                    }
                                }
                                if (plate == null && detectedVehicleType == null) {
                                    recognitionResult = "未检测到车牌和车型，请重试"
                                }
                            }
                        }
                        is com.example.smartlogistics.network.NetworkResult.Error -> {
                            withContext(Dispatchers.Main) {
                                isRecognizing = false
                                recognitionResult = "识别失败: ${result.message}"
                            }
                        }
                        is com.example.smartlogistics.network.NetworkResult.Exception -> {
                            withContext(Dispatchers.Main) {
                                isRecognizing = false
                                recognitionResult = "网络错误: ${result.throwable.message}"
                            }
                        }
                        else -> {
                            withContext(Dispatchers.Main) {
                                isRecognizing = false
                            }
                        }
                    }

                    // 清理临时文件
                    tempFile.delete()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isRecognizing = false
                        recognitionResult = "识别出错: ${e.message}"
                    }
                }
            }
        }
    }

    // 2. 相机拍照 Launcher - 使用 TakePicture（需要传入 Uri）
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && photoUri != null) {
            isRecognizing = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 将Uri转换为临时文件
                    val inputStream = context.contentResolver.openInputStream(photoUri!!)
                    val tempFile = java.io.File(context.cacheDir, "temp_camera_plate.jpg")
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    // 调用后端API识别车牌和车型
                    when (val result = repository.analyzeVehicleImage(tempFile)) {
                        is com.example.smartlogistics.network.NetworkResult.Success -> {
                            val response = result.data
                            val plate = response.licensePlate?.text
                            val detectedVehicleType = response.vehicleType?.vehicleClass
                            withContext(Dispatchers.Main) {
                                isRecognizing = false
                                if (plate != null) {
                                    plateNumber = plate
                                    recognitionResult = "识别成功: $plate"
                                }
                                // 自动填充车型 - 映射到前端选项
                                detectedVehicleType?.let { vt ->
                                    vehicleType = mapVehicleTypeToTruckOption(vt)
                                    if (plate != null) {
                                        recognitionResult = "识别成功: $plate (${mapVehicleTypeToCn(vt)})"
                                    }
                                }
                                if (plate == null && detectedVehicleType == null) {
                                    recognitionResult = "未检测到车牌和车型，请重试"
                                }
                            }
                        }
                        is com.example.smartlogistics.network.NetworkResult.Error -> {
                            withContext(Dispatchers.Main) {
                                isRecognizing = false
                                recognitionResult = "识别失败: ${result.message}"
                            }
                        }
                        is com.example.smartlogistics.network.NetworkResult.Exception -> {
                            withContext(Dispatchers.Main) {
                                isRecognizing = false
                                recognitionResult = "网络错误: ${result.throwable.message}"
                            }
                        }
                        else -> {
                            withContext(Dispatchers.Main) {
                                isRecognizing = false
                            }
                        }
                    }

                    // 清理临时文件
                    tempFile.delete()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isRecognizing = false
                        recognitionResult = "识别出错: ${e.message}"
                    }
                }
            }
        } else {
            recognitionResult = "拍照取消或失败"
        }
    }

    // 3. 相机权限 Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限已授予，创建 Uri 并启动相机
            photoUri = CameraUtils.createImageUri(context)
            photoUri?.let { cameraLauncher.launch(it) }
        } else {
            Toast.makeText(context, "需要相机权限才能拍照识别", Toast.LENGTH_SHORT).show()
        }
    }

    // 启动相机的函数
    fun launchCamera() {
        if (CameraUtils.hasCameraPermission(context)) {
            // 已有权限，直接创建 Uri 并启动相机
            photoUri = CameraUtils.createImageUri(context)
            photoUri?.let {
                cameraLauncher.launch(it)
            } ?: run {
                recognitionResult = "无法创建图片文件"
            }
        } else {
            // 请求权限
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
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
                    // 拍照识别按钮 - 调用 launchCamera() 函数
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { launchCamera() },
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

                    // 相册选择按钮
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
                        "truck" to "卡车",
                        "van" to "小型货车"
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
                    plateNumber = plateNumber,
                    vehicleType = vehicleType,
                    brand = "",
                    heightM = heightM.toDoubleOrNull(),
                    weightT = weightT.toDoubleOrNull(),
                    axleCount = axleCount.replace("+", "").toIntOrNull()
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
    val context = LocalContext.current

    // 状态管理
    var isRefreshing by remember { mutableStateOf(false) }
    var lastUpdateTime by remember { mutableStateOf("") }
    var selectedSegment by remember { mutableStateOf<TruckRoadSegment?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var aMapInstance by remember { mutableStateOf<AMap?>(null) }
    var currentLocation by remember { mutableStateOf<AMapLocation?>(null) }

    // 后端数据状态
    var roadSegments by remember { mutableStateOf<List<TruckRoadSegment>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // 闸口名称映射
    val gateNameMap = mapOf(
        "Gate_N1" to "北1号闸口",
        "Gate_N2" to "北2号闸口",
        "Gate_S1" to "南1号闸口",
        "Gate_S2" to "南2号闸口",
        "Gate_E1" to "东1号闸口",
        "Gate_W1" to "西1号闸口"
    )

    // 刷新数据 - 调用真实后端API (/traffic/gates)
    val scope = rememberCoroutineScope()
    fun refreshData() {
        scope.launch {
            isRefreshing = true
            loadError = null
            try {
                // 调用后端闸口排队API
                val response = withContext(Dispatchers.IO) {
                    com.example.smartlogistics.network.RetrofitClient.apiService.getGateQueues()
                }
                if (response.isSuccessful && response.body() != null) {
                    val gateData = response.body()!!
                    // 将闸口数据转换为路段显示
                    roadSegments = gateData.queues?.map { (gateId, queueCount) ->
                        val level = when {
                            queueCount <= 2 -> TruckRoadCongestionLevel.FREE
                            queueCount <= 5 -> TruckRoadCongestionLevel.LIGHT
                            queueCount <= 10 -> TruckRoadCongestionLevel.MODERATE
                            else -> TruckRoadCongestionLevel.SEVERE
                        }
                        val description = when (level) {
                            TruckRoadCongestionLevel.FREE -> "通道畅通，可快速通行"
                            TruckRoadCongestionLevel.LIGHT -> "排队车辆较少，预计等待5分钟"
                            TruckRoadCongestionLevel.MODERATE -> "排队车辆较多，预计等待15分钟"
                            TruckRoadCongestionLevel.SEVERE -> "严重排队，建议选择其他闸口"
                        }
                        TruckRoadSegment(
                            id = gateId,
                            name = gateNameMap[gateId] ?: gateId,
                            distance = "-",
                            estimatedTime = "排队: ${queueCount}辆",
                            congestionLevel = level,
                            description = description,
                            avgSpeed = when (level) {
                                TruckRoadCongestionLevel.FREE -> "快速通行"
                                TruckRoadCongestionLevel.LIGHT -> "正常通行"
                                TruckRoadCongestionLevel.MODERATE -> "缓慢通行"
                                TruckRoadCongestionLevel.SEVERE -> "拥堵严重"
                            },
                            truckRestriction = null
                        )
                    }?.sortedBy { it.congestionLevel.ordinal } ?: emptyList()

                    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    lastUpdateTime = sdf.format(java.util.Date())
                    loadError = null
                } else {
                    loadError = "获取闸口数据失败: ${response.code()}"
                }
            } catch (e: Exception) {
                loadError = e.message ?: "网络请求失败"
            } finally {
                isRefreshing = false
                isLoading = false
            }
        }
    }

    // 初始加载数据
    LaunchedEffect(Unit) {
        refreshData()
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
                        text = if (roadSegments.isNotEmpty()) "共${roadSegments.size}条通道" else "",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                // 路段列表 - 带加载和错误状态
                when {
                    isLoading -> {
                        // 加载中状态
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = TruckOrange)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "正在获取道路数据...",
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    loadError != null -> {
                        // 错误状态
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = loadError ?: "获取数据失败",
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { refreshData() },
                                    colors = ButtonDefaults.buttonColors(containerColor = TruckOrange)
                                ) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                    roadSegments.isEmpty() -> {
                        // 空数据状态
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.Traffic,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "暂无道路数据",
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { refreshData() }) {
                                    Text("点击刷新", color = TruckOrange)
                                }
                            }
                        }
                    }
                    else -> {
                        // 正常显示列表
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
    // ★★★ 所有数据从后端获取 ★★★
    val congestionResponse by viewModel?.congestionData?.collectAsState() ?: remember { mutableStateOf(null) }
    val gateQueues by viewModel?.gateQueues?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }

    var isLoading by remember { mutableStateOf(true) }
    var selectedTimeRange by remember { mutableStateOf("实时") }
    var selectedDataIndex by remember { mutableStateOf(0) }

    // ★★★ 根据时间选择计算API参数 ★★★
    val predictHours = when (selectedTimeRange) {
        "实时" -> 3
        "今天" -> 12
        "明天" -> 24
        "后天" -> 24
        else -> 5
    }

    // ★★★ 时间选择变化时重新调用API ★★★
    LaunchedEffect(selectedTimeRange) {
        isLoading = true
        viewModel?.predictCongestion(roadId = "cargo_main_road", hours = predictHours)
        viewModel?.fetchGateQueues()
        kotlinx.coroutines.delay(800)
        isLoading = false
    }

    // 后端返回的拥堵预测数据转换为UI格式
    val congestionData = remember(congestionResponse) {
        congestionResponse?.data?.predictions?.map { pred ->
            CongestionDataPoint(
                time = pred.time,
                ttiIndex = pred.tti,
                level = getTTILevel(pred.tti)
            )
        } ?: emptyList()
    }

    val serverSuggestion = congestionResponse?.data?.suggestion
    val rawRoadName = congestionResponse?.data?.roadName
    val currentTti = congestionResponse?.data?.currentTti

    // ★★★ 道路名称中文映射 ★★★
    val roadName = when (rawRoadName) {
        "main_road" -> "货运主干道"
        "truck_main_road" -> "货运主干道"
        "cargo_main_road" -> "货运主干道"
        "container_road" -> "集装箱通道"
        "hazmat_road" -> "危化品专用道"
        else -> rawRoadName ?: "货运主干道"
    }

    LaunchedEffect(congestionData) {
        if (congestionData.isNotEmpty()) {
            selectedDataIndex = 0
        }
    }

    DetailScreenTemplate(
        navController = navController,
        title = "拥堵预测",
        backgroundColor = BackgroundPrimary
    ) {
        // 当前道路信息
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TruckOrangeLight)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = roadName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    if (currentTti != null) {
                        Text(text = "当前TTI: ${"%.1f".format(currentTti)}", fontSize = 13.sp, color = TextSecondary)
                    } else {
                        Text(text = "加载中...", fontSize = 13.sp, color = TextSecondary)
                    }
                }
                if (currentTti != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = getTTILevel(currentTti).color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = getTTILevel(currentTti).label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = getTTILevel(currentTti).color
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ★★★ 时间选择器（点击会触发API调用）★★★
        TimeRangeSelector(
            selectedRange = selectedTimeRange,
            onRangeSelected = { newRange ->
                if (newRange != selectedTimeRange) {
                    selectedTimeRange = newRange
                }
            },
            primaryColor = TruckOrange
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TruckOrange)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "正在获取${selectedTimeRange}预测数据...", fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }
        } else if (congestionData.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Rounded.CloudOff, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "暂无${selectedTimeRange}预测数据", fontSize = 15.sp, color = TextSecondary)
                    Text(text = "请检查网络连接或稍后重试", fontSize = 13.sp, color = TextTertiary)
                }
            }
        } else {
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
                        Text(text = "${selectedTimeRange}拥堵趋势预测", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CongestionLevel.values().take(3).forEach { level ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(level.color, CircleShape))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = level.label, fontSize = 10.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TTITrendChart(
                        data = congestionData,
                        selectedIndex = selectedDataIndex,
                        onPointSelected = { selectedDataIndex = it },
                        primaryColor = TruckOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            congestionData.getOrNull(selectedDataIndex)?.let { dataPoint ->
                CongestionDetailCard(dataPoint = dataPoint, primaryColor = TruckOrange)
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (serverSuggestion != null) {
                TipCard(
                    text = serverSuggestion,
                    icon = Icons.Rounded.Lightbulb,
                    backgroundColor = TruckOrangeLight,
                    iconColor = TruckOrange
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ★★★ 闸口排队状态（后端数据）★★★
        Text(text = "闸口排队实时状态", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        if (gateQueues.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(color = TruckOrange, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(text = "暂无闸口数据", fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }
        } else {
            gateQueues.forEach { (gateName, queueCount) ->
                val level = when {
                    queueCount == 0 -> CongestionLevel.FREE
                    queueCount <= 2 -> CongestionLevel.LIGHT
                    queueCount <= 5 -> CongestionLevel.MODERATE
                    else -> CongestionLevel.SEVERE
                }
                val displayName = when (gateName) {
                    "Gate_N1" -> "北1号闸口"
                    "Gate_N2" -> "北2号闸口"
                    "Gate_S1" -> "南1号闸口"
                    "Gate_E1" -> "东1号闸口"
                    else -> gateName
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { navController.navigate("navigation_map") },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Rounded.DoorFront, contentDescription = null, tint = TruckOrange, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = displayName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text(text = "排队车辆: ${queueCount}辆", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(8.dp), color = level.color.copy(alpha = 0.15f)) {
                                Text(text = level.label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = level.color)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==================== 历史数据页面 ====================
@Composable
fun TruckHistoryScreen(navController: NavController, viewModel: MainViewModel? = null) {
    var selectedTab by remember { mutableStateOf(0) } // 默认选中"本周"
    val tabs = listOf("本周", "本月", "全部")

    // 获取当前日期用于筛选
    val currentDate = remember { java.time.LocalDate.now() }

    // 后端数据状态
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // 从后端获取报备历史数据
    val reports by viewModel?.reports?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

    // 初始加载数据
    LaunchedEffect(Unit) {
        isLoading = true
        loadError = null
        try {
            viewModel?.fetchReports()
        } catch (e: Exception) {
            loadError = e.message ?: "获取数据失败"
        } finally {
            isLoading = false
        }
    }

    // 将后端报备数据转换为历史记录格式
    val allHistoryRecords = remember(reports) {
        reports.mapNotNull { report ->
            // 安全处理可能为空的cargoInfo
            val cargoType = try { report.cargoInfo.cargoType } catch (e: Exception) { null }
            val weight = try { report.cargoInfo.weight } catch (e: Exception) { null }

            if (cargoType == null) return@mapNotNull null

            HistoryRecord(
                date = report.createdAt?.substring(0, 10) ?: currentDate.toString(),
                route = "$cargoType → ${report.destinationPoiId}",
                cargoType = cargoType,
                distance = weight ?: 0.0,
                duration = "-",
                status = when (report.status) {
                    "approved" -> "已完成"
                    "pending" -> "待审核"
                    "rejected" -> "已拒绝"
                    else -> report.status ?: "未知"
                }
            )
        }
    }

    // 根据选中的Tab筛选数据
    val filteredRecords = remember(selectedTab, allHistoryRecords) {
        if (allHistoryRecords.isEmpty()) {
            emptyList()
        } else {
            when (selectedTab) {
                0 -> { // 本周（本周一到今天）
                    val startOfWeek = currentDate.with(java.time.DayOfWeek.MONDAY)
                    allHistoryRecords.filter {
                        try {
                            val recordDate = java.time.LocalDate.parse(it.date)
                            recordDate >= startOfWeek && recordDate <= currentDate
                        } catch (e: Exception) {
                            false
                        }
                    }
                }
                1 -> { // 本月（本月1号到今天）
                    val startOfMonth = currentDate.withDayOfMonth(1)
                    allHistoryRecords.filter {
                        try {
                            val recordDate = java.time.LocalDate.parse(it.date)
                            recordDate >= startOfMonth && recordDate <= currentDate
                        } catch (e: Exception) {
                            false
                        }
                    }
                }
                else -> allHistoryRecords // 全部
            }
        }
    }

    // 根据筛选后的数据计算统计
    val stats = remember(filteredRecords) {
        val totalOrders = filteredRecords.size
        val totalDistance = if (filteredRecords.isNotEmpty()) filteredRecords.sumOf { it.distance } else 0.0

        // 计算准时率（已完成的订单）
        val completedCount = filteredRecords.count { it.status == "已完成" }
        val onTimeRate = if (totalOrders > 0) (completedCount * 100 / totalOrders) else 0

        val normalCount = filteredRecords.count { it.cargoType.contains("普通") || it.cargoType.contains("货物") }
        val coldChainCount = filteredRecords.count { it.cargoType.contains("冷链") }
        val hazardousCount = filteredRecords.count { it.cargoType.contains("危") || it.cargoType.contains("化") }
        val containerCount = filteredRecords.count { it.cargoType.contains("集装箱") }

        TruckHistoryStats(
            totalOrders = totalOrders,
            totalDistance = totalDistance.toInt(),
            onTimeRate = onTimeRate,
            normalCount = normalCount,
            coldChainCount = coldChainCount,
            hazardousCount = hazardousCount,
            containerCount = containerCount
        )
    }

    // 统计卡片标题
    val statsTitle = when (selectedTab) {
        0 -> "本周运输统计"
        1 -> "本月运输统计"
        else -> "全部运输统计"
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
                    text = statsTitle,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = "${stats.totalOrders}", label = "运输单数", color = Color.White)
                    StatItem(value = "${stats.totalDistance}", label = "总里程(km)", color = Color.White)
                    StatItem(value = "${stats.onTimeRate}%", label = "准时率", color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = "${stats.normalCount}", label = "普通货物", color = Color.White)
                    StatItem(value = "${stats.coldChainCount}", label = "冷链货物", color = Color.White)
                    StatItem(value = "${stats.hazardousCount}", label = "危险品", color = Color.White)
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

        // 历史记录列表标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "运输记录",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = "共 ${filteredRecords.size} 条",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 列表内容 - 带加载状态
        when {
            isLoading -> {
                // 加载中状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TruckOrange)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在获取历史数据...",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
            loadError != null -> {
                // 错误状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = loadError ?: "获取数据失败",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                isLoading = true
                                loadError = null
                                viewModel?.fetchReports()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TruckOrange)
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
            filteredRecords.isEmpty() -> {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无运输记录",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            else -> {
                filteredRecords.forEach { record ->
                    HistoryRecordCard(
                        record = record,
                        primaryColor = TruckOrange,
                        isHazardous = record.cargoType.contains("危") || record.cargoType.contains("化")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // 加载更多（可选）
        if (filteredRecords.size >= 5) {
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
}

// ==================== 货车历史统计数据类（新增）====================
data class TruckHistoryStats(
    val totalOrders: Int,
    val totalDistance: Int,
    val onTimeRate: Int,
    val normalCount: Int,
    val coldChainCount: Int,
    val hazardousCount: Int,
    val containerCount: Int = 0
)

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CargoReportScreen(navController: NavController, viewModel: MainViewModel? = null) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // 表单状态
    var cargoType by remember { mutableStateOf("普通货物") }
    var isHazardous by remember { mutableStateOf(false) }
    // 危险品类别 - 使用HazmatClass对象
    var selectedHazmatClass by remember { mutableStateOf<HazmatClass?>(null) }
    var hazardClassExpanded by remember { mutableStateOf(false) }  // 下拉菜单展开状态
    var weight by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val reportState by viewModel?.reportState?.collectAsState() ?: remember { mutableStateOf(ReportState.Idle) }
    val vehicles by viewModel?.vehicles?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    var selectedVehicleId by remember { mutableStateOf(-1) }
    val isLoading = reportState is ReportState.Loading
    val coroutineScope = rememberCoroutineScope()
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
                    // 危险品类别 - 从名称匹配HazmatClass对象
                    parsed.hazardClass?.let { hazardName ->
                        selectedHazmatClass = HazmatRecognitionHelper.HAZMAT_CLASSES.values.find {
                            it.name == hazardName || it.englishName == hazardName
                        }
                    }
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

    // ========== Repository用于调用后端API ==========
    val repository = remember { com.example.smartlogistics.network.Repository(context) }

    // ========== 危化品识别相关状态 ==========
    var isHazmatRecognizing by remember { mutableStateOf(false) }
    var hazmatRecognitionResult by remember { mutableStateOf<HazmatRecognitionResult?>(null) }

    // 危化品相机拍照的 Uri
    var hazmatPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // 危化品图片选择器
    val hazmatImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isHazmatRecognizing = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 将Uri转换为临时文件
                    val inputStream = context.contentResolver.openInputStream(it)
                    val tempFile = java.io.File(context.cacheDir, "temp_hazmat_image.jpg")
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    // 调用后端API识别
                    when (val result = repository.analyzeVehicleImage(tempFile)) {
                        is com.example.smartlogistics.network.NetworkResult.Success -> {
                            val response = result.data
                            withContext(Dispatchers.Main) {
                                isHazmatRecognizing = false
                                if (response.hazmat?.detected == true && response.hazmat.labels?.isNotEmpty() == true) {
                                    val labelName = response.hazmat.labels.first()
                                    val hazmatClass = HazmatRecognitionHelper.getClassByCode(labelName)
                                    hazmatRecognitionResult = HazmatRecognitionResult(
                                        hazmatClass = hazmatClass,
                                        confidence = 0.9f,
                                        isHazardous = true,
                                        classIndex = hazmatClass?.code?.toIntOrNull() ?: -1
                                    )
                                    hazmatClass?.let { selectedHazmatClass = it }
                                } else {
                                    hazmatRecognitionResult = HazmatRecognitionResult(
                                        hazmatClass = null,
                                        confidence = 0f,
                                        isHazardous = false,
                                        classIndex = -1
                                    )
                                }
                            }
                        }
                        is com.example.smartlogistics.network.NetworkResult.Error -> {
                            withContext(Dispatchers.Main) {
                                isHazmatRecognizing = false
                                Toast.makeText(context, "识别失败: ${result.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is com.example.smartlogistics.network.NetworkResult.Exception -> {
                            withContext(Dispatchers.Main) {
                                isHazmatRecognizing = false
                                Toast.makeText(context, "网络错误: ${result.throwable.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        else -> {
                            withContext(Dispatchers.Main) {
                                isHazmatRecognizing = false
                            }
                        }
                    }
                    tempFile.delete()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isHazmatRecognizing = false
                    }
                }
            }
        }
    }

    // 危化品相机拍照 - 使用 TakePicture
    val hazmatCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && hazmatPhotoUri != null) {
            isHazmatRecognizing = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 将Uri转换为临时文件
                    val inputStream = context.contentResolver.openInputStream(hazmatPhotoUri!!)
                    val tempFile = java.io.File(context.cacheDir, "temp_hazmat_camera.jpg")
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    // 调用后端API识别
                    when (val result = repository.analyzeVehicleImage(tempFile)) {
                        is com.example.smartlogistics.network.NetworkResult.Success -> {
                            val response = result.data
                            withContext(Dispatchers.Main) {
                                isHazmatRecognizing = false
                                if (response.hazmat?.detected == true && response.hazmat.labels?.isNotEmpty() == true) {
                                    val labelName = response.hazmat.labels.first()
                                    val hazmatClass = HazmatRecognitionHelper.getClassByCode(labelName)
                                    hazmatRecognitionResult = HazmatRecognitionResult(
                                        hazmatClass = hazmatClass,
                                        confidence = 0.9f,
                                        isHazardous = true,
                                        classIndex = hazmatClass?.code?.toIntOrNull() ?: -1
                                    )
                                    hazmatClass?.let { selectedHazmatClass = it }
                                } else {
                                    hazmatRecognitionResult = HazmatRecognitionResult(
                                        hazmatClass = null,
                                        confidence = 0f,
                                        isHazardous = false,
                                        classIndex = -1
                                    )
                                }
                            }
                        }
                        is com.example.smartlogistics.network.NetworkResult.Error -> {
                            withContext(Dispatchers.Main) {
                                isHazmatRecognizing = false
                                Toast.makeText(context, "识别失败: ${result.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is com.example.smartlogistics.network.NetworkResult.Exception -> {
                            withContext(Dispatchers.Main) {
                                isHazmatRecognizing = false
                                Toast.makeText(context, "网络错误: ${result.throwable.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        else -> {
                            withContext(Dispatchers.Main) {
                                isHazmatRecognizing = false
                            }
                        }
                    }
                    tempFile.delete()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isHazmatRecognizing = false
                    }
                }
            }
        }
    }

    // 危化品相机权限
    val hazmatCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hazmatPhotoUri = CameraUtils.createImageUri(context)
            hazmatPhotoUri?.let { hazmatCameraLauncher.launch(it) }
        }
    }

    // 启动危化品相机的函数
    fun launchHazmatCamera() {
        if (CameraUtils.hasCameraPermission(context)) {
            hazmatPhotoUri = CameraUtils.createImageUri(context)
            hazmatPhotoUri?.let { hazmatCameraLauncher.launch(it) }
        } else {
            hazmatCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // 在 DisposableEffect 中添加清理
    DisposableEffect(Unit) {
        onDispose {
            speechHelper.destroy()
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
                                onClick = { selectedVehicleId = vehicle.vehicleId },
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
                            onClick = {
                                // 先清除焦点（关闭软键盘）
                                focusManager.clearFocus()
                                // 更新货物类型
                                cargoType = type
                                isHazardous = type == "危险品"
                                // 如果选择非危险品，清空危险品相关状态
                                if (type != "危险品") {
                                    selectedHazmatClass = null
                                    hazmatRecognitionResult = null
                                }
                            },
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
                                    onClick = { launchHazmatCamera() },
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

                    // 危险品类别下拉选择
                    Text(
                        text = "危化品类别",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = hazardClassExpanded,
                        onExpandedChange = { hazardClassExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedHazmatClass?.let { "${it.icon} ${it.name}" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("请选择危化品类别", color = TextTertiary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = if (selectedHazmatClass != null) Color(selectedHazmatClass!!.colorInt) else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = hazardClassExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ErrorRed,
                                unfocusedBorderColor = BorderLight,
                                focusedLabelColor = ErrorRed
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = hazardClassExpanded,
                            onDismissRequest = { hazardClassExpanded = false }
                        ) {
                            // 遍历所有危险品类别
                            HazmatRecognitionHelper.HAZMAT_CLASSES.values.forEach { hazmatClass ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            // 危化品颜色标识
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
                                                        Color(hazmatClass.colorInt),
                                                        RoundedCornerShape(6.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = hazmatClass.code,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "${hazmatClass.icon} ${hazmatClass.name}",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = hazmatClass.englishName,
                                                    fontSize = 11.sp,
                                                    color = TextTertiary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedHazmatClass = hazmatClass
                                        hazardClassExpanded = false
                                        // 同步更新识别结果显示
                                        hazmatRecognitionResult = HazmatRecognitionResult(
                                            hazmatClass = hazmatClass,
                                            confidence = 1.0f,
                                            isHazardous = true,
                                            classIndex = hazmatClass.code.toIntOrNull() ?: -1
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
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
                    selectedHazmatClass?.name,  // 发送危险品中文名称
                    weight.toDoubleOrNull(),
                    description.ifBlank { null }
                )
            },
            isLoading = isLoading,
            enabled = selectedVehicleId != -1 && destination.isNotBlank(),
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
            // 尝试提取危化品类别 - 匹配HazmatRecognitionHelper中的13类
            val hazardPatterns = mapOf(
                "有毒" to "有毒物",
                "毒" to "有毒物",
                "氧气" to "氧气",
                "易燃" to "易燃气体/液体",
                "易燃固" to "易燃固体",
                "腐蚀" to "腐蚀性物质",
                "非易燃" to "非易燃气体",
                "过氧化" to "有机过氧化物",
                "爆炸" to "爆炸物",
                "放射" to "放射性物质",
                "吸入" to "吸入危害物",
                "自燃" to "自燃物质",
                "感染" to "感染性物质",
                "传染" to "感染性物质"
            )
            hazardPatterns.forEach { (keyword, className) ->
                if (lowerText.contains(keyword)) {
                    hazardClass = className
                    return@forEach
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