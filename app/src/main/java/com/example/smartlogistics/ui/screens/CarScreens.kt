package com.example.smartlogistics.ui.screens

import CongestionDetailCard
import TTITrendChart
import TimeRangeSelector
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.smartlogistics.ui.components.*
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.MainViewModel
import com.example.smartlogistics.viewmodel.VehicleState
import com.example.smartlogistics.viewmodel.TripState
import java.net.URLEncoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Bitmap
import kotlinx.coroutines.*
import com.example.smartlogistics.utils.TFLiteHelper
import generateMockCongestionData

// ==================== 私家车主主页 ====================
@Composable
fun CarHomeScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val menuItems = listOf(
        MenuItem("车辆绑定", Icons.Rounded.DirectionsCar, "car_bind"),
        MenuItem("路线规划", Icons.Rounded.Route, "car_route"),
        MenuItem("道路实况", Icons.Rounded.Explore, "car_road"),
        MenuItem("拥堵预测", Icons.Rounded.Timeline, "car_congestion"),
        MenuItem("历史数据", Icons.Rounded.History, "car_history"),
        MenuItem("我的行程", Icons.Rounded.FlightTakeoff, "my_trips")
    )
    
    val vehicles by viewModel?.vehicles?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val trips by viewModel?.trips?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    
    LaunchedEffect(Unit) {
        viewModel?.fetchVehicles()
        viewModel?.fetchTrips()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
    ) {
        // 头部 (带AI语音按钮)
        DashboardHeader(
            title = "智行生活版",
            subtitle = "美好出行 · 从这里开始",
            searchHint = "去哪儿玩? 找餐厅、停车场...",
            primaryColor = CarGreen,
            gradientBrush = Brush.linearGradient(
                colors = listOf(CarGreen, CarGreenDark)
            ),
            onSearchClick = { navController.navigate("navigation_map") },
            onAiClick = { navController.navigate("ai_chat") }
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 行程提醒卡片
            val activeTrip = trips.firstOrNull()
            if (activeTrip != null && activeTrip.tripNumber.isNotBlank() && !activeTrip.tripNumber.contains("string", ignoreCase = true)) {
                item {
                    TripReminderCard(
                        tripType = activeTrip.tripType,
                        tripNumber = activeTrip.tripNumber,
                        status = activeTrip.status ?: "On Time",
                        onClick = { navController.navigate("my_trips") }
                    )
                }
            }
            
            // 快捷统计 (移除停车费用)
            item {
                QuickStatsCard(
                    items = listOf(
                        "本月行程" to "28",
                        "总里程" to "486km",
                        "导航次数" to "15"
                    ),
                    backgroundColor = CarGreen
                )
            }
            
            // 功能网格标题
            item {
                Text(
                    text = "常用功能",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            
            // 功能网格
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    menuItems.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    FeatureCard(
                                        title = item.title,
                                        icon = item.icon,
                                        primaryColor = CarGreen,
                                        onClick = { navController.navigate(item.route) }
                                    )
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            
            // 附近停车场
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "附近停车场",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(3) { index ->
                        NearbyParkingCard(
                            name = listOf("万达广场停车场", "银泰商场B2", "市民中心P1")[index],
                            distance = listOf("500m", "1.2km", "2.0km")[index],
                            availableSpots = listOf(45, 12, 86)[index]
                        )
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ==================== 行程提醒卡片 ====================
@Composable
private fun TripReminderCard(
    tripType: String,
    tripNumber: String,
    status: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CarGreen)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (tripType == "flight") Icons.Rounded.Flight else Icons.Rounded.Train,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = tripNumber, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(text = if (tripType == "flight") "航班" else "火车", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
            StatusBadge(
                text = when(status) { "On Time" -> "准点"; "Delayed" -> "延误"; else -> status },
                backgroundColor = when(status) { "On Time" -> Color.White; "Delayed" -> WarningYellow; else -> Color.White },
                textColor = when(status) { "On Time" -> CarGreen; "Delayed" -> Color.White; else -> TextPrimary }
            )
        }
    }
}

@Composable
private fun StatusBadge(text: String, backgroundColor: Color, textColor: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = backgroundColor) {
        Text(text = text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ==================== 车辆绑定页面 ====================
@Composable
fun CarBindScreen(navController: NavController, viewModel: MainViewModel? = null) {
    var plateNumber by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("sedan") }
    val vehicleState by viewModel?.vehicleState?.collectAsState() ?: remember { mutableStateOf(VehicleState.Idle) }
    val vehicles by viewModel?.vehicles?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isLoading = vehicleState is VehicleState.Loading

    // 车牌识别相关状态
    var showImagePicker by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var isRecognizing by remember { mutableStateOf(false) }
    var recognitionResult by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val tfliteHelper = remember { TFLiteHelper(context) }

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isRecognizing = true
            // 在协程中执行识别
            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = tfliteHelper.loadImageFromUri(it)
                val result = bitmap?.let { bmp -> tfliteHelper.recognizePlate(bmp) }

                withContext(Dispatchers.Main) {
                    isRecognizing = false
                    result?.let { plate ->
                        plateNumber = plate
                        recognitionResult = "识别成功: $plate"
                    }
                }
            }
        }
    }

    // 相机拍照
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            isRecognizing = true
            CoroutineScope(Dispatchers.IO).launch {
                val result = tfliteHelper.recognizePlate(it)

                withContext(Dispatchers.Main) {
                    isRecognizing = false
                    plateNumber = result
                    recognitionResult = "识别成功: $result"
                }
            }
        }
    }

    // 相机权限请求
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            recognitionResult = "需要相机权限才能拍照识别"
        }
    }

    LaunchedEffect(vehicleState) {
        if (vehicleState is VehicleState.BindSuccess) {
            plateNumber = ""
            recognitionResult = null
            viewModel?.resetVehicleState()
        }
    }

    DetailScreenTemplate(navController = navController, title = "车辆绑定", backgroundColor = BackgroundPrimary) {
        // 过滤无效车辆数据
        val validVehicles = vehicles.filter {
            it.plateNumber.isNotBlank() && !it.plateNumber.contains("string", ignoreCase = true)
        }

        if (validVehicles.isNotEmpty()) {
            Text(text = "已绑定车辆", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            validVehicles.forEach { vehicle ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).background(CarGreenLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Rounded.DirectionsCar, contentDescription = null, tint = CarGreen, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = vehicle.plateNumber, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = vehicle.vehicleType, fontSize = 14.sp, color = TextSecondary)
                        }
                        IconButton(onClick = { vehicle.vehicleId?.let { viewModel?.unbindVehicle(it) } }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "删除", tint = ErrorRed)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(text = "添加新车辆", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                // AI识别按钮组
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 拍照识别
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                when (PackageManager.PERMISSION_GRANTED) {
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.CAMERA
                                    ) -> {
                                        cameraLauncher.launch(null)
                                    }
                                    else -> {
                                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                    }
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CarGreen.copy(alpha = 0.1f))
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
                                tint = CarGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "拍照识别",
                                fontSize = 15.sp,
                                color = CarGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 相册选择
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CarGreen.copy(alpha = 0.1f))
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
                                tint = CarGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "相册选择",
                                fontSize = 15.sp,
                                color = CarGreen,
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
                            .background(CarGreen.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = CarGreen,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "正在识别车牌...",
                            fontSize = 13.sp,
                            color = CarGreen
                        )
                    }
                }

                // 识别结果显示
                recognitionResult?.let { result ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CarGreen.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = CarGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = result,
                            fontSize = 13.sp,
                            color = CarGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "或手动输入", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(16.dp))
                StyledTextField(value = plateNumber, onValueChange = { plateNumber = it.uppercase() }, label = "车牌号", leadingIcon = Icons.Rounded.Pin)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "车辆类型", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("sedan" to "轿车", "suv" to "SUV", "mpv" to "MPV").forEach { (type, label) ->
                        FilterChip(selected = vehicleType == type, onClick = { vehicleType = type }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CarGreen.copy(alpha = 0.2f), selectedLabelColor = CarGreen))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(text = "绑定车辆", onClick = { viewModel?.bindVehicle(plateNumber, vehicleType) }, isLoading = isLoading, enabled = plateNumber.isNotBlank(), backgroundColor = CarGreen, icon = Icons.Rounded.Add)

        // ==================== 寻车助手模块 (保持原样) ====================
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "寻车助手", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "记录停车位置", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clickable { /* TODO: 保存GPS位置 */ },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CarGreen.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = CarGreen,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "标记位置", fontSize = 13.sp, color = CarGreen, fontWeight = FontWeight.Medium)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clickable { /* TODO: 打开相机拍照 */ },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CarGreen.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                tint = CarGreen,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "拍照记录", fontSize = 13.sp, color = CarGreen, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = BorderLight)
                Spacer(modifier = Modifier.height(20.dp))

                Text(text = "找车", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clickable { navController.navigate("navigation_map") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B82F6).copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Navigation,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "导航找车", fontSize = 13.sp, color = Color(0xFF3B82F6), fontWeight = FontWeight.Medium)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clickable { /* TODO: 查看停车照片 */ },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B82F6).copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Photo,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "查看照片", fontSize = 13.sp, color = Color(0xFF3B82F6), fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BackgroundSecondary, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "停车后记录位置,方便您快速找到爱车",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            tfliteHelper.close()
        }
    }
}

// ==================== 路线规划页面 ====================
@Composable
fun CarRouteScreen(navController: NavController, viewModel: MainViewModel? = null) {
    var destination by remember { mutableStateOf("") }
    var showParkingRecommendation by remember { mutableStateOf(false) }

    // 模拟停车场推荐数据
    val recommendedParkingLots = remember {
        listOf(
            RecommendedParking("P1停车场", "距目的地200m", 45, 200, "¥5/h", true),
            RecommendedParking("P2地下停车场", "距目的地350m", 12, 150, "¥6/h", false),
            RecommendedParking("路边停车位", "距目的地100m", 3, 20, "¥8/h", false)
        )
    }

    DetailScreenTemplate(
        navController = navController,
        title = "路线规划",
        backgroundColor = BackgroundPrimary
    ) {
        // AI语音入口
        AiEntryCard(
            title = "语音导航",
            subtitle = "说出目的地，智能规划路线",
            primaryColor = CarGreen,
            onClick = { navController.navigate("ai_chat") }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 路线输入卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
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
                            .background(CarGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "我的位置", fontSize = 15.sp, color = TextPrimary)
                }

                // 连接线
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp, top = 4.dp, bottom = 4.dp)
                        .width(2.dp)
                        .height(24.dp)
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
                        onValueChange = {
                            destination = it
                            // 输入目的地后显示停车场推荐
                            showParkingRecommendation = it.isNotBlank()
                        },
                        placeholder = { Text("输入目的地", color = TextTertiary) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CarGreen,
                            unfocusedBorderColor = BorderLight
                        ),
                        singleLine = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 快捷目的地
        Text(
            text = "快捷目的地",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))

        listOf(
            Icons.Rounded.Home to "家",
            Icons.Rounded.Work to "公司",
            Icons.Rounded.Flight to "机场"
        ).forEach { (icon, name) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable {
                        destination = name
                        showParkingRecommendation = true
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CarGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = name, fontSize = 15.sp, color = TextPrimary)
                }
            }
        }

        // ==================== 停车场智能推荐 ====================
        if (showParkingRecommendation && destination.isNotBlank()) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "推荐停车场",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Text(
                    text = "基于预测空位",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            recommendedParkingLots.forEach { parking ->
                ParkingRecommendationCard(
                    parking = parking,
                    onSelect = {
                        // 选择停车场后开始导航
                        navController.navigate("navigation_map")
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 智能建议
            Spacer(modifier = Modifier.height(8.dp))
            TipCard(
                text = "P1停车场预计30分钟后车位紧张，建议尽快出发",
                icon = Icons.Rounded.Lightbulb,
                backgroundColor = CarGreenLight,
                iconColor = CarGreen
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 导航按钮
        PrimaryButton(
            text = "开始导航",
            onClick = { navController.navigate("navigation_map") },
            enabled = destination.isNotBlank(),
            backgroundColor = CarGreen,
            icon = Icons.Rounded.Navigation
        )
    }
}

// ==================== 停车场推荐数据类 ====================
data class RecommendedParking(
    val name: String,
    val distance: String,
    val availableSpots: Int,
    val totalSpots: Int,
    val price: String,
    val isRecommended: Boolean
)

// ==================== 停车场推荐卡片组件 ====================
@Composable
private fun ParkingRecommendationCard(
    parking: RecommendedParking,
    onSelect: () -> Unit
) {
    val availabilityPercent = parking.availableSpots.toFloat() / parking.totalSpots
    val availabilityColor = when {
        availabilityPercent > 0.3f -> Color(0xFF22C55E)  // 充足 - 绿色
        availabilityPercent > 0.1f -> Color(0xFFFBBF24)  // 适中 - 黄色
        availabilityPercent > 0f -> Color(0xFFF97316)   // 紧张 - 橙色
        else -> Color(0xFFEF4444)                        // 已满 - 红色
    }

    val availabilityLabel = when {
        availabilityPercent > 0.3f -> "充足"
        availabilityPercent > 0.1f -> "适中"
        availabilityPercent > 0f -> "紧张"
        else -> "已满"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (parking.isRecommended) CarGreen.copy(alpha = 0.05f) else Color.White
        ),
        border = if (parking.isRecommended) BorderStroke(1.5.dp, CarGreen) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 停车场图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (parking.isRecommended) CarGreen.copy(alpha = 0.15f)
                        else BackgroundSecondary,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalParking,
                    contentDescription = null,
                    tint = if (parking.isRecommended) CarGreen else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 停车场信息
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = parking.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    if (parking.isRecommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CarGreen
                        ) {
                            Text(
                                text = "推荐",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = parking.distance,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = parking.price,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // 空位信息
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(availabilityColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = availabilityLabel,
                        fontSize = 13.sp,
                        color = availabilityColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${parking.availableSpots}/${parking.totalSpots}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

// ==================== 道路实况页面 ====================
@Composable
fun CarRoadScreen(navController: NavController, viewModel: MainViewModel? = null) {
    DetailScreenTemplate(navController = navController, title = "道路实况", backgroundColor = BackgroundPrimary) {
        Card(modifier = Modifier.fillMaxWidth().height(300.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Rounded.Map, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "地图加载中...", color = TextSecondary)
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "路况图例", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CarTrafficLegendItem(color = CongestionFree, label = "畅通")
            CarTrafficLegendItem(color = CongestionLight, label = "缓行")
            CarTrafficLegendItem(color = CongestionModerate, label = "拥堵")
            CarTrafficLegendItem(color = CongestionSevere, label = "严重")
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "客运服务点", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        listOf("T1航站楼" to "人流量适中", "T2航站楼" to "人流量较大", "高铁站" to "人流量正常").forEach { (name, status) ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = name, fontSize = 15.sp, color = TextPrimary)
                    Text(text = status, fontSize = 13.sp, color = CarGreen)
                }
            }
        }
    }
}

// ==================== 路况图例项 (改名避免冲突) ====================
@Composable
private fun CarTrafficLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(16.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
    }
}

// ==================== 拥堵预测页面 ====================
@Composable
fun CarCongestionScreen(navController: NavController, viewModel: MainViewModel? = null) {
    // 数据状态
    val congestionData = remember { generateMockCongestionData() }
    var selectedTimeRange by remember { mutableStateOf("今天") }
    var selectedDataIndex by remember { mutableStateOf(10) } // 默认选中16:00

    // 模拟停车场入口数据
    val parkingEntrances = remember {
        listOf(
            Triple("P1停车场入口", "300m", CongestionLevel.FREE),
            Triple("P2停车场入口", "500m", CongestionLevel.LIGHT),
            Triple("P3停车场入口", "800m", CongestionLevel.MODERATE),
            Triple("航站楼落客区", "200m", CongestionLevel.SEVERE),
            Triple("高铁站停车场", "1.2km", CongestionLevel.LIGHT)
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
            primaryColor = CarGreen
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
                        text = "道路拥堵趋势预测",
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
                    primaryColor = CarGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 选中时间点详情
        CongestionDetailCard(
            dataPoint = congestionData[selectedDataIndex],
            primaryColor = CarGreen
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 智能建议
        val selectedData = congestionData[selectedDataIndex]
        val suggestion = when (selectedData.level) {
            CongestionLevel.FREE -> "当前时段路况良好，适合出行！"
            CongestionLevel.LIGHT -> "轻微缓行，预计延误5-10分钟。"
            CongestionLevel.MODERATE -> "建议提前15分钟出发，或选择备用路线。"
            CongestionLevel.SEVERE -> "严重拥堵！建议改乘公共交通或延后出行。"
        }

        TipCard(
            text = suggestion,
            icon = Icons.Rounded.Lightbulb,
            backgroundColor = CarGreenLight,
            iconColor = CarGreen
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 停车场入口状态
        Text(
            text = "停车场入口实时状态",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        parkingEntrances.forEach { (name, distance, level) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { navController.navigate("navigation_map") },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LocalParking,
                            contentDescription = null,
                            tint = CarGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                text = distance,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = level.color.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = level.label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = level.color
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 开始导航按钮
        PrimaryButton(
            text = "规划最优路线",
            onClick = { navController.navigate("navigation_map") },
            backgroundColor = CarGreen,
            icon = Icons.Rounded.Navigation
        )
    }
}

// ==================== 历史数据页面 ====================
@Composable
fun CarHistoryScreen(navController: NavController, viewModel: MainViewModel? = null) {
    // 模拟历史数据
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("本周", "本月", "全部")

    val historyRecords = remember {
        listOf(
            CarHistoryRecord("2024-12-15", "家 → T2航站楼", 28.5, "42分钟", "接人"),
            CarHistoryRecord("2024-12-14", "T1航站楼 → 万达广场", 15.2, "25分钟", "日常"),
            CarHistoryRecord("2024-12-13", "公司 → 家", 18.0, "35分钟", "通勤"),
            CarHistoryRecord("2024-12-12", "家 → 高铁站", 22.3, "38分钟", "送人"),
            CarHistoryRecord("2024-12-11", "银泰商场 → 家", 12.5, "20分钟", "日常"),
            CarHistoryRecord("2024-12-10", "家 → 公司", 18.0, "32分钟", "通勤"),
            CarHistoryRecord("2024-12-09", "机场高速 → 市区", 35.0, "55分钟", "日常")
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
            colors = CardDefaults.cardColors(containerColor = CarGreen)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "本月出行统计",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CarStatItem(value = "28", label = "出行次数")
                    CarStatItem(value = "486", label = "总里程(km)")
                    CarStatItem(value = "15h", label = "行驶时长")
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                Spacer(modifier = Modifier.height(16.dp))

                // 出行类型统计
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CarStatItem(value = "12", label = "通勤")
                    CarStatItem(value = "8", label = "日常出行")
                    CarStatItem(value = "5", label = "接送人")
                    CarStatItem(value = "3", label = "其他")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 出行趋势（简化图表）
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "本周出行趋势",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 简单的柱状图
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val weekData = listOf(45, 30, 60, 25, 80, 55, 40)
                    val days = listOf("一", "二", "三", "四", "五", "六", "日")

                    weekData.forEachIndexed { index, value ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height((value * 0.8).dp)
                                    .background(
                                        CarGreen.copy(alpha = 0.7f + index * 0.04f),
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = days[index],
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
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
                        selectedContainerColor = CarGreen.copy(alpha = 0.15f),
                        selectedLabelColor = CarGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 历史记录列表
        Text(
            text = "出行记录",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        historyRecords.forEach { record ->
            CarHistoryRecordCard(
                record = record,
                primaryColor = CarGreen
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
                color = CarGreen
            )
        }
    }
}

// ==================== 私家车历史记录数据模型 ====================
data class CarHistoryRecord(
    val date: String,
    val route: String,
    val distance: Double,
    val duration: String,
    val tripType: String
)

// ==================== 统计项组件（私家车版） ====================
@Composable
private fun CarStatItem(
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp
        )
    }
}

// ==================== 历史记录卡片（私家车版） ====================
@Composable
private fun CarHistoryRecordCard(
    record: CarHistoryRecord,
    primaryColor: Color
) {
    val tripIcon = when (record.tripType) {
        "通勤" -> Icons.Rounded.Work
        "接人", "送人" -> Icons.Rounded.PersonPinCircle
        else -> Icons.Rounded.Route
    }

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
                        primaryColor.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tripIcon,
                    contentDescription = null,
                    tint = primaryColor,
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

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = primaryColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = record.tripType,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            color = primaryColor
                        )
                    }
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

// ==================== 我的行程页面 ====================
@Composable
fun MyTripsScreen(navController: NavController, viewModel: MainViewModel? = null) {
    var tripType by remember { mutableStateOf("flight") }
    var tripNumber by remember { mutableStateOf("") }
    var tripDate by remember { mutableStateOf("") }
    val tripState by viewModel?.tripState?.collectAsState() ?: remember { mutableStateOf(TripState.Idle) }
    val trips by viewModel?.trips?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isLoading = tripState is TripState.Loading
    
    DetailScreenTemplate(navController = navController, title = "我的行程", backgroundColor = BackgroundPrimary) {
        // ==================== 接人/送人模式 ====================
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonPinCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "接人/送人模式",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "实时分享您的位置给亲友",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 开始共享位置按钮
                var isSharing by remember { mutableStateOf(false) }
                var shareLink by remember { mutableStateOf("") }
                
                if (!isSharing) {
                    Button(
                        onClick = { 
                            isSharing = true
                            shareLink = "https://share.smartlogistics.com/location/${System.currentTimeMillis()}"
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Brush.linearGradient(
                                colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                            ).let { Color(0xFF667EEA) }
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "开始共享位置", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    // 正在共享状态
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF667EEA).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF22C55E), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在共享位置...",
                                fontSize = 14.sp,
                                color = Color(0xFF667EEA),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 复制链接按钮
                            OutlinedButton(
                                onClick = { /* TODO: 复制到剪贴板 */ },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF667EEA))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = Color(0xFF667EEA),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "复制链接", fontSize = 14.sp, color = Color(0xFF667EEA))
                            }
                            
                            // 停止共享按钮
                            Button(
                                onClick = { isSharing = false },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "停止共享", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // ==================== 已关联行程 ====================
        val validTrips = trips.filter { it.tripNumber.isNotBlank() && !it.tripNumber.contains("string", ignoreCase = true) }
        if (validTrips.isNotEmpty()) {
            Text(text = "已关联行程", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            validTrips.forEach { trip ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CarGreen)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = if (trip.tripType == "flight") Icons.Rounded.Flight else Icons.Rounded.Train, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = trip.tripNumber, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            StatusBadge(text = trip.status ?: "准点", backgroundColor = Color.White, textColor = CarGreen)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "日期: ${trip.tripDate}", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
        
        Text(text = "添加新行程", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("flight" to "航班" to Icons.Rounded.Flight, "train" to "火车" to Icons.Rounded.Train).forEach { (typeLabel, icon) ->
                val (type, label) = typeLabel
                Card(modifier = Modifier.weight(1f).height(80.dp).clickable { tripType = type }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (tripType == type) CarGreen.copy(alpha = 0.1f) else Color.White), border = if (tripType == type) BorderStroke(2.dp, CarGreen) else null) {
                    Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = if (tripType == type) CarGreen else TextSecondary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, fontSize = 16.sp, fontWeight = if (tripType == type) FontWeight.SemiBold else FontWeight.Normal, color = if (tripType == type) CarGreen else TextSecondary)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                StyledTextField(value = tripNumber, onValueChange = { tripNumber = it.uppercase() }, label = if (tripType == "flight") "航班号 (如 MU5521)" else "车次号 (如 G1234)", leadingIcon = if (tripType == "flight") Icons.Rounded.Flight else Icons.Rounded.Train)
                Spacer(modifier = Modifier.height(16.dp))
                StyledTextField(value = tripDate, onValueChange = { tripDate = it }, label = "出发日期 (如 2024-12-06)", leadingIcon = Icons.Rounded.CalendarToday)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryButton(text = "关联行程", onClick = { viewModel?.createTrip(tripType, tripNumber, tripDate) }, isLoading = isLoading, enabled = tripNumber.isNotBlank() && tripDate.isNotBlank(), backgroundColor = CarGreen, icon = Icons.Rounded.Add)
    }
}
