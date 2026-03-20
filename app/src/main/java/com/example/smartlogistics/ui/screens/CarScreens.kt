package com.example.smartlogistics.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.example.smartlogistics.ui.components.*
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.utils.CameraUtils
import com.example.smartlogistics.utils.ParkingManager
import com.example.smartlogistics.utils.SettingsManager
import com.example.smartlogistics.viewmodel.MainViewModel
import com.example.smartlogistics.viewmodel.TripState
import com.example.smartlogistics.viewmodel.VehicleState
import kotlinx.coroutines.*
import java.io.File
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

// ==================== 将后端车型映射到私家车版前端选项 ====================
// 私家车版支持的选项: sedan(轿车), suv(SUV), bus(客车), minibus(小型客车)
private fun mapVehicleTypeToCarOption(vehicleType: String?): String {
    return when (vehicleType?.lowercase()) {
        "sedan", "car" -> "sedan"              // 轿车 -> 轿车
        "suv" -> "suv"                         // SUV -> SUV
        "bus" -> "bus"                         // 客车 -> 客车
        "minibus", "van" -> "minibus"          // 小型客车、面包车 -> 小型客车
        "truck", "pickup" -> "bus"             // 货车、皮卡 -> 客车（大型）
        else -> "sedan"                        // 默认选择轿车
    }
}

// ==================== 行程OCR识别结果数据类 ====================
data class TripOcrResult(
    val tripType: String,           // flight / train
    val tripNumber: String,         // 航班号/车次
    val tripDate: String,           // 出发日期
    val departureCity: String? = null,  // 出发城市
    val arrivalCity: String? = null,    // 到达城市
    val departureTime: String? = null,  // 出发时间
    val passengerName: String? = null,  // 乘客姓名
    val seatInfo: String? = null,       // 座位信息
    val confidence: Float = 0.95f       // 识别置信度
)

// ==================== 私家车主主页 ====================
@Composable
fun CarHomeScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current

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

    // 初始化定位并搜索交通枢纽
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

            // 快捷统计 - 从实际行程数据计算
            item {
                val totalTrips = trips.size.toString()
                val vehicleCount = vehicles.size.toString()
                QuickStatsCard(
                    items = listOf(
                        "我的行程" to totalTrips,
                        "绑定车辆" to vehicleCount,
                        "活跃行程" to trips.count { it.status == "active" || it.status == "scheduled" }.toString()
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

            // 功能网格（保持原样）
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


// ==================== 🚗 智能停车助手 ====================
// ==================== 在 CarBindScreen 函数之前添加数据类 ====================

data class ParkingRecord(
    val id: Long = System.currentTimeMillis(),
    val photoUri: Uri? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "photo"  // "photo" 或 "location"
)

// 转换函数：ParkingRecord <-> ParkingRecordData
private fun ParkingRecord.toData(): ParkingManager.ParkingRecordData {
    return ParkingManager.ParkingRecordData(
        id = id,
        photoUriString = photoUri?.toString(),
        latitude = latitude,
        longitude = longitude,
        address = address,
        timestamp = timestamp,
        type = type
    )
}

private fun ParkingManager.ParkingRecordData.toRecord(): ParkingRecord {
    return ParkingRecord(
        id = id,
        photoUri = photoUriString?.let { Uri.parse(it) },
        latitude = latitude,
        longitude = longitude,
        address = address,
        timestamp = timestamp,
        type = type
    )
}

// ==================== 替换整个 CarBindScreen 函数 ====================

@Composable
fun CarBindScreen(navController: NavController, viewModel: MainViewModel? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ========== 持久化管理器 ==========
    val parkingManager = remember { ParkingManager(context) }

    // ========== 车辆绑定状态 ==========
    var plateNumber by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("sedan") }
    val vehicleState by viewModel?.vehicleState?.collectAsState() ?: remember { mutableStateOf(VehicleState.Idle) }
    val vehicles by viewModel?.vehicles?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isLoading = vehicleState is VehicleState.Loading

    // ========== 车牌识别状态 ==========
    var isRecognizing by remember { mutableStateOf(false) }
    var recognitionResult by remember { mutableStateOf<String?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    // ========== Repository用于调用后端API ==========
    val repository = remember { com.example.smartlogistics.network.Repository(context) }

    // ========== 🚗 智能停车助手状态 ==========
    // ⭐ 从持久化存储加载数据
    var parkingRecords by remember {
        mutableStateOf(parkingManager.getRecords().map { it.toRecord() })
    }
    var parkingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var findCarPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isParkingUploading by remember { mutableStateOf(false) }
    var isGettingLocation by remember { mutableStateOf(false) }
    var isFindingCar by remember { mutableStateOf(false) }

    // ========== 楼层和车位号状态 ==========
    var selectedFloor by remember { mutableStateOf(1) }   // 1=B1, 2=B2, 3=B3
    var spotCodeInput by remember { mutableStateOf("") }
    var findCarApiResult by remember { mutableStateOf<com.example.smartlogistics.network.ParkingFindResponse?>(null) }

    // 高德定位客户端
    var locationClient by remember { mutableStateOf<AMapLocationClient?>(null) }

    // 弹窗状态
    var showPhotoDetailDialog by remember { mutableStateOf(false) }
    var showLocationDetailDialog by remember { mutableStateOf(false) }
    var showFindCarResultDialog by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<ParkingRecord?>(null) }
    var findCarResult by remember { mutableStateOf<String?>(null) }

    // 计算是否有可用记录
    val hasLocationRecord = parkingRecords.any { it.latitude != null && it.longitude != null }
    val hasPhotoRecord = parkingRecords.any { it.photoUri != null }
    val latestLocationRecord = parkingRecords.firstOrNull { it.latitude != null }

    // =====================================================
    // ⭐ 保存数据的辅助函数
    // =====================================================

    fun saveRecordsToStorage(records: List<ParkingRecord>) {
        parkingManager.saveRecords(records.map { it.toData() })
    }

    fun addRecordAndSave(record: ParkingRecord) {
        parkingRecords = listOf(record) + parkingRecords
        saveRecordsToStorage(parkingRecords)
    }

    fun deleteRecordAndSave(id: Long) {
        parkingRecords = parkingRecords.filterNot { it.id == id }
        saveRecordsToStorage(parkingRecords)
    }

    fun clearRecordsAndSave() {
        parkingRecords = emptyList()
        parkingManager.clearRecords()
    }

    // =====================================================
    // 🌍 真实定位函数
    // =====================================================

    fun startRealLocation() {
        try {
            AMapLocationClient.updatePrivacyShow(context, true, true)
            AMapLocationClient.updatePrivacyAgree(context, true)

            val client = locationClient ?: AMapLocationClient(context)
            locationClient = client

            client.setLocationOption(AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = true
                isNeedAddress = true
                isLocationCacheEnable = false
                httpTimeOut = 20000
            })

            client.setLocationListener { location ->
                if (location != null && location.errorCode == 0) {
                    // ⭐ 尝试多个字段获取地址
                    val address = when {
                        !location.address.isNullOrBlank() -> location.address
                        !location.poiName.isNullOrBlank() -> location.poiName
                        !location.aoiName.isNullOrBlank() -> location.aoiName
                        !location.street.isNullOrBlank() -> {
                            "${location.district ?: ""}${location.street ?: ""}${location.streetNum ?: ""}"
                        }
                        !location.district.isNullOrBlank() -> location.district
                        else -> "停车位置"
                    }

                    val record = ParkingRecord(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = address,
                        type = "location"
                    )
                    addRecordAndSave(record)
                    isGettingLocation = false
                    Toast.makeText(context, "位置已标记", Toast.LENGTH_SHORT).show()
                } else {
                    isGettingLocation = false
                    Toast.makeText(context, "定位失败: ${location?.errorInfo ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                }
                client.stopLocation()
            }

            client.startLocation()
        } catch (e: Exception) {
            isGettingLocation = false
            Toast.makeText(context, "定位出错: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // =====================================================
    // Launcher 声明
    // =====================================================

    // 车牌识别 - 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isRecognizing = true
            scope.launch(Dispatchers.IO) {
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
                                    vehicleType = mapVehicleTypeToCarOption(vt)
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

    // 车牌识别 - 相机
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && photoUri != null) {
            isRecognizing = true
            scope.launch(Dispatchers.IO) {
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
                                    vehicleType = mapVehicleTypeToCarOption(vt)
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

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            photoUri = CameraUtils.createImageUri(context)
            photoUri?.let { cameraLauncher.launch(it) }
        } else {
            recognitionResult = "需要相机权限"
        }
    }

    // ⭐ 停车拍照（保存到持久化存储 + 上报后端）
    val parkingCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && parkingPhotoUri != null) {
            val newRecord = ParkingRecord(
                photoUri = parkingPhotoUri,
                type = "photo"
            )
            addRecordAndSave(newRecord)  // ⭐ 保存到持久化存储
            Toast.makeText(context, "照片已保存", Toast.LENGTH_SHORT).show()

            // ⭐ 上报后端（异步，不阻塞UI）
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(parkingPhotoUri!!)
                    val tempFile = java.io.File(context.cacheDir, "parking_upload.jpg")
                    inputStream?.use { input -> tempFile.outputStream().use { input.copyTo(it) } }

                    repository.registerParkingPhoto(
                        imageFile = tempFile,
                        floor = selectedFloor.toString(),
                        spotCode = spotCodeInput.trim().ifBlank { null }
                    )
                    tempFile.delete()
                } catch (e: Exception) {
                    android.util.Log.e("Parking", "上报停车失败: ${e.message}")
                }
            }
        }
    }

    val parkingCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            parkingPhotoUri = CameraUtils.createImageUri(context)
            parkingPhotoUri?.let { parkingCameraLauncher.launch(it) }
        } else {
            Toast.makeText(context, "需要相机权限", Toast.LENGTH_SHORT).show()
        }
    }

    // ⭐ 寻车拍照（调用真实后端接口匹配）
    val findCarCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && findCarPhotoUri != null) {
            isFindingCar = true
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(findCarPhotoUri!!)
                    val tempFile = java.io.File(context.cacheDir, "findcar_upload.jpg")
                    withContext(Dispatchers.IO) {
                        inputStream?.use { input -> tempFile.outputStream().use { input.copyTo(it) } }
                    }
                    when (val result = withContext(Dispatchers.IO) { repository.findParkingByPhoto(tempFile) }) {
                        is com.example.smartlogistics.network.NetworkResult.Success -> {
                            findCarApiResult = result.data
                            findCarResult = result.data.matchResult?.message
                                ?: result.data.message
                                        ?: "匹配完成"
                            showFindCarResultDialog = true
                        }
                        is com.example.smartlogistics.network.NetworkResult.Error -> {
                            findCarResult = "匹配失败: ${result.message}"
                            showFindCarResultDialog = true
                        }
                        else -> {
                            findCarResult = "网络错误，请重试"
                            showFindCarResultDialog = true
                        }
                    }
                    tempFile.delete()
                } catch (e: Exception) {
                    findCarResult = "出错: ${e.message}"
                    showFindCarResultDialog = true
                } finally {
                    isFindingCar = false
                }
            }
        }
    }

    val findCarCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            findCarPhotoUri = CameraUtils.createImageUri(context)
            findCarPhotoUri?.let { findCarCameraLauncher.launch(it) }
        } else {
            Toast.makeText(context, "需要相机权限", Toast.LENGTH_SHORT).show()
        }
    }

    // ⭐ 位置权限（真实定位）
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            startRealLocation()
        } else {
            isGettingLocation = false
            Toast.makeText(context, "需要位置权限", Toast.LENGTH_SHORT).show()
        }
    }

    // =====================================================
    // 辅助函数
    // =====================================================

    fun launchCamera() {
        if (CameraUtils.hasCameraPermission(context)) {
            photoUri = CameraUtils.createImageUri(context)
            photoUri?.let { cameraLauncher.launch(it) }
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    fun launchParkingCamera() {
        if (CameraUtils.hasCameraPermission(context)) {
            parkingPhotoUri = CameraUtils.createImageUri(context)
            parkingPhotoUri?.let { parkingCameraLauncher.launch(it) }
        } else {
            parkingCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    fun launchFindCarCamera() {
        if (CameraUtils.hasCameraPermission(context)) {
            findCarPhotoUri = CameraUtils.createImageUri(context)
            findCarPhotoUri?.let { findCarCameraLauncher.launch(it) }
        } else {
            findCarCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // 🌍 请求真实定位
    fun requestLocationAndMark() {
        isGettingLocation = true

        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            startRealLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun navigateToParking() {
        latestLocationRecord?.let { record ->
            if (record.latitude != null && record.longitude != null) {
                // ⭐ 格式：DIRECT|地址|纬度|经度
                val address = record.address?.takeIf { it.isNotBlank() } ?: "停车位置"
                val destination = "DIRECT:::$address:::${record.latitude}:::${record.longitude}"
                val encodedDest = android.net.Uri.encode(destination)
                navController.navigate("navigation_map?destination=$encodedDest")
            }
        }
    }

    // 副作用
    LaunchedEffect(vehicleState) {
        if (vehicleState is VehicleState.BindSuccess) {
            plateNumber = ""
            recognitionResult = null
            viewModel?.resetVehicleState()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationClient?.stopLocation()
            locationClient?.onDestroy()
        }
    }

    // =====================================================
    // UI
    // =====================================================

    DetailScreenTemplate(navController = navController, title = "车辆绑定", backgroundColor = BackgroundPrimary) {
        // 已绑定车辆列表
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

        // 添加新车辆
        Text(text = "添加新车辆", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f).clickable { launchCamera() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CarGreen.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(imageVector = Icons.Rounded.CameraAlt, contentDescription = null, tint = CarGreen, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "拍照识别", fontSize = 15.sp, color = CarGreen, fontWeight = FontWeight.Medium)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f).clickable { imagePickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CarGreen.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(imageVector = Icons.Rounded.Photo, contentDescription = null, tint = CarGreen, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "相册选择", fontSize = 15.sp, color = CarGreen, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                if (isRecognizing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth().background(CarGreen.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CarGreen, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "正在识别车牌...", fontSize = 13.sp, color = CarGreen)
                    }
                }

                recognitionResult?.let { result ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth().background(CarGreen.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null, tint = CarGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = result, fontSize = 13.sp, color = CarGreen, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "或手动输入", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(16.dp))
                StyledTextField(value = plateNumber, onValueChange = { plateNumber = it.uppercase() }, label = "车牌号", leadingIcon = Icons.Rounded.Pin)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "车辆类型", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                // 第一行：轿车、SUV
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("sedan" to "轿车", "suv" to "SUV").forEach { (type, label) ->
                        FilterChip(selected = vehicleType == type, onClick = { vehicleType = type }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CarGreen.copy(alpha = 0.2f), selectedLabelColor = CarGreen))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 第二行：客车、小型客车
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("bus" to "客车", "minibus" to "小型客车").forEach { (type, label) ->
                        FilterChip(selected = vehicleType == type, onClick = { vehicleType = type }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CarGreen.copy(alpha = 0.2f), selectedLabelColor = CarGreen))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(text = "绑定车辆", onClick = { viewModel?.bindVehicle(plateNumber, vehicleType) }, isLoading = isLoading, enabled = plateNumber.isNotBlank(), backgroundColor = CarGreen, icon = Icons.Rounded.Add)

        // ==================== ⭐ 智能停车助手 ====================
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "智能停车助手", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 记录停车位置
                Text(text = "记录停车位置", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(modifier = Modifier.height(12.dp))

                // ========== ⭐ 楼层选择（B1/B2/B3）==========
                Text(text = "停车楼层", fontSize = 13.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(1 to "B1", 2 to "B2", 3 to "B3").forEach { (floor, label) ->
                        val isSelected = selectedFloor == floor
                        Card(
                            modifier = Modifier.weight(1f).height(44.dp).clickable { selectedFloor = floor },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) CarGreen else CarGreen.copy(alpha = 0.08f)
                            ),
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, CarGreen.copy(alpha = 0.3f))
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else CarGreen
                                )
                            }
                        }
                    }
                }

                // ========== ⭐ 车位号输入 ==========
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = spotCodeInput,
                    onValueChange = { spotCodeInput = it },
                    placeholder = { Text("车位号，如 A-15（可不填）", color = TextTertiary, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CarGreen,
                        unfocusedBorderColor = BorderLight
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Pin, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 标记位置
                    Card(
                        modifier = Modifier.weight(1f).height(80.dp).clickable(enabled = !isGettingLocation) { requestLocationAndMark() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CarGreen.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            if (isGettingLocation) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = CarGreen, strokeWidth = 3.dp)
                            } else {
                                Icon(imageVector = Icons.Rounded.LocationOn, contentDescription = null, tint = CarGreen, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = if (isGettingLocation) "定位中..." else "标记位置", fontSize = 13.sp, color = CarGreen, fontWeight = FontWeight.Medium)
                        }
                    }

                    // 拍照记录
                    Card(
                        modifier = Modifier.weight(1f).height(80.dp).clickable { launchParkingCamera() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CarGreen.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(imageVector = Icons.Rounded.CameraAlt, contentDescription = null, tint = CarGreen, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "拍照记录", fontSize = 13.sp, color = CarGreen, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // ⭐ 停车记录历史（可点击查看详情）
                if (parkingRecords.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "停车记录 (${parkingRecords.size})", fontSize = 13.sp, color = TextSecondary)
                        TextButton(onClick = { clearRecordsAndSave() }) {  // ⭐ 清空时也清除存储
                            Text(text = "清空", fontSize = 12.sp, color = ErrorRed)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(parkingRecords.size) { index ->
                            val record = parkingRecords[index]
                            ParkingRecordCard(
                                record = record,
                                context = context,
                                onClick = {
                                    selectedRecord = record
                                    if (record.type == "photo") {
                                        showPhotoDetailDialog = true
                                    } else {
                                        showLocationDetailDialog = true
                                    }
                                },
                                onDelete = {
                                    deleteRecordAndSave(record.id)  // ⭐ 删除时也更新存储
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = BorderLight)
                Spacer(modifier = Modifier.height(20.dp))

                // 找车
                Text(text = "找车", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 导航找车
                    Card(
                        modifier = Modifier.weight(1f).height(80.dp).clickable(enabled = hasLocationRecord) { navigateToParking() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasLocationRecord) Color(0xFF3B82F6).copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Navigation,
                                contentDescription = null,
                                tint = if (hasLocationRecord) Color(0xFF3B82F6) else Color.Gray,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "导航找车", fontSize = 13.sp, color = if (hasLocationRecord) Color(0xFF3B82F6) else Color.Gray, fontWeight = FontWeight.Medium)
                            if (!hasLocationRecord) {
                                Text(text = "请先标记位置", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }

                    // 图片匹配
                    Card(
                        modifier = Modifier.weight(1f).height(80.dp).clickable(enabled = hasPhotoRecord && !isFindingCar) { launchFindCarCamera() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasPhotoRecord) Color(0xFF3B82F6).copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            if (isFindingCar) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color(0xFF3B82F6), strokeWidth = 3.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = if (hasPhotoRecord) Color(0xFF3B82F6) else Color.Gray,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = if (isFindingCar) "匹配中..." else "图片匹配", fontSize = 13.sp, color = if (hasPhotoRecord) Color(0xFF3B82F6) else Color.Gray, fontWeight = FontWeight.Medium)
                            if (!hasPhotoRecord && !isFindingCar) {
                                Text(text = "请先拍照记录", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(BackgroundSecondary, RoundedCornerShape(8.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Rounded.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "停车记录会自动保存，退出后仍可查看", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }

    // ==================== 📷 照片详情弹窗 ====================
    if (showPhotoDetailDialog && selectedRecord != null) {
        Dialog(onDismissRequest = { showPhotoDetailDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "停车照片", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        IconButton(onClick = { showPhotoDetailDialog = false }) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "关闭", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    selectedRecord?.photoUri?.let { uri ->
                        val bitmap = remember(uri) {
                            try {
                                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                    BitmapFactory.decodeStream(inputStream)
                                }
                            } catch (e: Exception) { null }
                        }

                        bitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "停车照片",
                                modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } ?: Box(
                            modifier = Modifier.fillMaxWidth().height(300.dp).background(Color.LightGray, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("无法加载图片", color = TextSecondary) }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.Schedule, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "拍摄时间: ${dateFormat.format(java.util.Date(selectedRecord?.timestamp ?: 0))}", fontSize = 14.sp, color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = {
                            selectedRecord?.let { deleteRecordAndSave(it.id) }
                            showPhotoDetailDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("删除此记录")
                    }
                }
            }
        }
    }

    // ==================== 📍 位置详情弹窗 ====================
    if (showLocationDetailDialog && selectedRecord != null) {
        Dialog(onDismissRequest = { showLocationDetailDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "停车位置", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        IconButton(onClick = { showLocationDetailDialog = false }) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "关闭", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF3B82F6).copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Rounded.LocationOn, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "GPS已标记", fontSize = 14.sp, color = Color(0xFF3B82F6), fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Rounded.Place, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "地址", fontSize = 12.sp, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = selectedRecord?.address ?: "未知地址", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Rounded.MyLocation, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "坐标", fontSize = 12.sp, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${String.format("%.6f", selectedRecord?.latitude)}, ${String.format("%.6f", selectedRecord?.longitude)}",
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.Schedule, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "标记时间: ${dateFormat.format(java.util.Date(selectedRecord?.timestamp ?: 0))}", fontSize = 14.sp, color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                selectedRecord?.let { deleteRecordAndSave(it.id) }
                                showLocationDetailDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("删除") }
                        Button(
                            onClick = {
                                showLocationDetailDialog = false
                                navigateToParking()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("导航前往") }
                    }
                }
            }
        }
    }

    // ==================== 🔍 图片匹配结果弹窗 ====================
    if (showFindCarResultDialog) {
        Dialog(onDismissRequest = { showFindCarResultDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val matchResult = findCarApiResult?.matchResult
                    val isSuccess = findCarApiResult?.status == "success" || matchResult != null

                    Box(
                        modifier = Modifier.size(64.dp).background(
                            if (isSuccess) Color(0xFF3B82F6).copy(alpha = 0.1f) else ErrorRed.copy(alpha = 0.1f),
                            CircleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                            contentDescription = null,
                            tint = if (isSuccess) Color(0xFF3B82F6) else ErrorRed,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isSuccess) "找到您的车！" else "匹配失败",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // ⭐ 展示楼层和车位号
                    if (matchResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // 停车位置
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📍", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("停车位置", fontSize = 12.sp, color = TextSecondary)
                                        val floorText = when (matchResult.floor) {
                                            1 -> "B1层"
                                            2 -> "B2层"
                                            3 -> "B3层"
                                            else -> matchResult.floor?.let { "${it}层" } ?: "—"
                                        }
                                        val locationText = if (!matchResult.spotCode.isNullOrBlank()) {
                                            "$floorText ${matchResult.spotCode}号车位"
                                        } else if (!matchResult.parkingArea.isNullOrBlank()) {
                                            matchResult.parkingArea
                                        } else {
                                            floorText
                                        }
                                        Text(locationText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    }
                                }
                                // 停车时间
                                if (!matchResult.parkedTime.isNullOrBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🕐", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("停车时间", fontSize = 12.sp, color = TextSecondary)
                                            val timeDisplay = matchResult.parkedTime.substringAfter("T").substringBefore(":").let {
                                                matchResult.parkedTime.substringAfter(" ").substringBeforeLast(":").ifBlank { matchResult.parkedTime }
                                            }
                                            Text(timeDisplay, fontSize = 14.sp, color = TextPrimary)
                                        }
                                    }
                                }
                                // 匹配置信度
                                if (!matchResult.confidence.isNullOrBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("✅", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("匹配置信度", fontSize = 12.sp, color = TextSecondary)
                                            val confidenceText = when (matchResult.confidence) {
                                                "high" -> "高"
                                                "medium" -> "中"
                                                "low" -> "低"
                                                else -> matchResult.confidence
                                            }
                                            Text(confidenceText, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(text = findCarResult ?: "", fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { showFindCarResultDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(text = "关闭", color = TextSecondary) }
                        if (hasLocationRecord) {
                            Button(
                                onClick = {
                                    showFindCarResultDialog = false
                                    navigateToParking()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text(text = "导航前往") }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 📦 停车记录卡片组件 ====================
@Composable
private fun ParkingRecordCard(
    record: ParkingRecord,
    context: Context,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }

    Card(
        modifier = Modifier.width(90.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
    ) {
        Box {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (record.photoUri != null) {
                        val bitmap = remember(record.photoUri) {
                            try {
                                context.contentResolver.openInputStream(record.photoUri)?.use { inputStream ->
                                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                                    BitmapFactory.decodeStream(inputStream, null, options)
                                }
                            } catch (e: Exception) { null }
                        }

                        bitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "停车照片",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } ?: Icon(imageVector = Icons.Rounded.Photo, contentDescription = null, tint = CarGreen, modifier = Modifier.size(32.dp))
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Rounded.LocationOn, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(32.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (record.type == "photo") "📷 照片" else "📍 位置",
                    fontSize = 11.sp,
                    color = if (record.type == "photo") CarGreen else Color(0xFF3B82F6),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = dateFormat.format(java.util.Date(record.timestamp)),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
            ) {
                Box(
                    modifier = Modifier.size(16.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "删除", tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
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
                            .background(CarGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "我的位置",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 定位刷新按钮
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "定位",
                        tint = CarGreen,
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
                        onValueChange = {
                            destination = it
                            showParkingRecommendation = it.isNotBlank()
                        },
                        placeholder = { Text("输入目的地", color = TextTertiary) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CarGreen,
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
                                    tint = CarGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
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

        // 快捷目的地列表
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CarQuickDestinationItem(
                icon = Icons.Rounded.Home,
                title = "家",
                subtitle = "北京市海淀区中关村",
                onClick = {
                    // 直接跳转到导航页面
                    val encodedDest = Uri.encode("北京市海淀区中关村")
                    navController.navigate("navigation_map?destination=$encodedDest")
                }
            )
            CarQuickDestinationItem(
                icon = Icons.Rounded.Work,
                title = "公司",
                subtitle = "北京市朝阳区望京",
                onClick = {
                    val encodedDest = Uri.encode("北京市朝阳区望京")
                    navController.navigate("navigation_map?destination=$encodedDest")
                }
            )
            CarQuickDestinationItem(
                icon = Icons.Rounded.Flight,
                title = "机场",
                subtitle = "北京首都国际机场",
                onClick = {
                    val encodedDest = Uri.encode("北京首都国际机场")
                    navController.navigate("navigation_map?destination=$encodedDest")
                }
            )
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
            onClick = {
                val encodedDest = Uri.encode(destination)
                navController.navigate("navigation_map?destination=$encodedDest")
            },
            enabled = destination.isNotBlank(),
            backgroundColor = CarGreen,
            icon = Icons.Rounded.Navigation
        )
    }
}

// 客运快捷目的地项
@Composable
private fun CarQuickDestinationItem(
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
                    .background(CarGreenLight, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CarGreen,
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

// 路段数据模型
data class RoadSegment(
    val id: String,
    val name: String,
    val distance: String,
    val estimatedTime: String,
    val congestionLevel: RoadCongestionLevel,
    val description: String,
    val avgSpeed: String
)

// 拥堵等级枚举
enum class RoadCongestionLevel(val label: String, val color: Color, val textColor: Color) {
    FREE("畅通", CongestionFree, CongestionFree),
    LIGHT("缓行", CongestionLight, Color(0xFFB8860B)),
    MODERATE("拥堵", CongestionModerate, CongestionModerate),
    SEVERE("严重", CongestionSevere, CongestionSevere)
}

@Composable
fun CarRoadScreen(navController: NavController, viewModel: MainViewModel? = null) {
    val context = LocalContext.current
    // ⭐ 模拟位置
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val isMockMode = settingsManager.mockLocationEnabled
    val mockLat = if (isMockMode) SettingsManager.DAXING_LAT else null
    val mockLng = if (isMockMode) SettingsManager.DAXING_LNG else null

    // 状态管理
    var isRefreshing by remember { mutableStateOf(false) }
    var lastUpdateTime by remember { mutableStateOf("刚刚更新") }
    var selectedSegment by remember { mutableStateOf<RoadSegment?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var aMapInstance by remember { mutableStateOf<AMap?>(null) }
    var currentLocation by remember { mutableStateOf<AMapLocation?>(null) }

    // 模拟路段数据（后端接入后替换为真实数据）
    val roadSegments = remember {
        listOf(
            RoadSegment("1", "机场高速-主干道", "5.2km", "约8分钟", RoadCongestionLevel.FREE, "道路通畅，建议正常行驶", "65km/h"),
            RoadSegment("2", "T1航站楼连接线", "1.8km", "约5分钟", RoadCongestionLevel.LIGHT, "车流量略大，注意保持车距", "35km/h"),
            RoadSegment("3", "T2航站楼环路", "2.3km", "约12分钟", RoadCongestionLevel.MODERATE, "出发层车辆较多，建议绕行到达层", "18km/h"),
            RoadSegment("4", "高铁站进站口", "0.8km", "约6分钟", RoadCongestionLevel.SEVERE, "大量旅客进站，车辆缓慢通行", "8km/h"),
            RoadSegment("5", "P1停车场入口", "0.5km", "约2分钟", RoadCongestionLevel.FREE, "停车位充足，可快速进入", "25km/h"),
            RoadSegment("6", "货运专用通道", "3.2km", "约6分钟", RoadCongestionLevel.LIGHT, "货车较多，小车注意避让", "40km/h"),
            RoadSegment("7", "城市快速路匝道", "1.5km", "约8分钟", RoadCongestionLevel.MODERATE, "匝道汇入口拥堵，请提前变道", "22km/h")
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
        if (isMockMode) {
            aMapInstance?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    com.amap.api.maps.model.LatLng(SettingsManager.DAXING_LAT, SettingsManager.DAXING_LNG),
                    SettingsManager.DAXING_ZOOM
                )
            )
        } else {
            currentLocation?.let { location ->
                aMapInstance?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        16f
                    )
                )
            }
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
                                color = CarGreen,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新",
                                tint = CarGreen
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
                        autoLocateOnStart = !isMockMode,
                        mockLat = mockLat,
                        mockLng = mockLng,
                        onMapReady = { map ->
                            aMapInstance = map
                            // ⭐ 从真实接口加载停车场 Marker
                            val carScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                            carScope.launch {
                                try {
                                    val resp = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.example.smartlogistics.network.RetrofitClient.apiService.getParkingLots()
                                    }
                                    if (resp.isSuccessful && resp.body()?.parkingLots != null) {
                                        resp.body()!!.parkingLots!!.forEach { lot ->
                                            val pos = com.amap.api.maps.model.LatLng(lot.lat, lot.longitude)
                                            map.addMarker(
                                                com.amap.api.maps.model.MarkerOptions()
                                                    .position(pos)
                                                    .title(lot.name ?: lot.id ?: "停车场")
                                                    .snippet(lot.id ?: "")
                                            )
                                        }
                                        // 点击 Marker 显示空位信息
                                        map.setOnMarkerClickListener { marker ->
                                            val lotId = marker.snippet
                                            val lot = resp.body()!!.parkingLots!!.find { it.id == lotId }
                                            val info = if (lot?.availableSpots != null && lot.totalSpots != null) {
                                                val statusText = when {
                                                    lot.availableSpots > lot.totalSpots * 0.3 -> "空位充足"
                                                    lot.availableSpots > 0 -> "较为拥挤"
                                                    else -> "接近满位"
                                                }
                                                "空位${lot.availableSpots}/${lot.totalSpots} · $statusText"
                                            } else "点击查看车位情况"
                                            marker.snippet = info
                                            marker.showInfoWindow()
                                            true
                                        }
                                    } else {
                                        android.util.Log.w("CarRoad", "停车场接口未就绪")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("CarRoad", "加载停车场失败: ${e.message}")
                                }
                            }
                        },
                        onLocationChanged = { location ->
                            currentLocation = location
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
                        contentColor = CarGreen,
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

                    // ⭐ 大兴机场快捷按钮
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .clickable {
                                aMapInstance?.animateCamera(
                                    com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(
                                        com.amap.api.maps.model.LatLng(SettingsManager.DAXING_LAT, SettingsManager.DAXING_LNG), SettingsManager.DAXING_ZOOM
                                    )
                                )
                            },
                        color = CarGreen,
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✈", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("大兴", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
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
                        CarTrafficLegendItem(color = CongestionFree, label = "畅通")
                        CarTrafficLegendItem(color = CongestionLight, label = "缓行")
                        CarTrafficLegendItem(color = CongestionModerate, label = "拥堵")
                        CarTrafficLegendItem(color = CongestionSevere, label = "严重")
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
                        text = "周边路段",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "共${roadSegments.size}条路段",
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
                        RoadSegmentCard(
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
        RoadSegmentDetailDialog(
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

// 路段卡片组件
@Composable
private fun RoadSegmentCard(
    segment: RoadSegment,
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
                Text(
                    text = segment.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
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

// 路段详情弹窗
@Composable
private fun RoadSegmentDetailDialog(
    segment: RoadSegment,
    onDismiss: () -> Unit,
    onNavigate: (RoadSegment) -> Unit
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
                        text = "路段详情",
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
                DetailInfoRow(label = "当前状态", value = segment.congestionLevel.label, valueColor = segment.congestionLevel.textColor)
                DetailInfoRow(label = "路段长度", value = segment.distance)
                DetailInfoRow(label = "预计用时", value = segment.estimatedTime)
                DetailInfoRow(label = "平均车速", value = segment.avgSpeed)

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
                            tint = CarGreen,
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
                    colors = ButtonDefaults.buttonColors(containerColor = CarGreen),
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

// 详情信息行
@Composable
private fun DetailInfoRow(
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
    // ★★★ 所有数据从后端获取 ★★★
    val congestionResponse by viewModel?.congestionData?.collectAsState() ?: remember { mutableStateOf(null) }
    val parkingList by viewModel?.parkingList?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }
    var selectedTimeRange by remember { mutableStateOf("实时") }
    var selectedDataIndex by remember { mutableStateOf(0) }

    // ★★★ 根据时间选择计算API参数 ★★★
    val hoursOffset = when (selectedTimeRange) {
        "实时" -> 0
        "今天" -> 0
        "明天" -> 24
        "后天" -> 48
        else -> 0
    }
    val predictHours = when (selectedTimeRange) {
        "实时" -> 3      // 实时：预测未来3小时
        "今天" -> 12     // 今天：预测12小时
        "明天" -> 24     // 明天：预测24小时
        "后天" -> 24     // 后天：预测24小时
        else -> 5
    }

    // ★★★ 时间选择变化时重新调用API ★★★
    LaunchedEffect(selectedTimeRange) {
        isLoading = true
        viewModel?.predictCongestion(roadId = "airport_expressway", hours = predictHours)
        kotlinx.coroutines.delay(800)
        isLoading = false
    }

    // ★★★ 初始加载停车场数据（大兴机场中心坐标）★★★
    LaunchedEffect(Unit) {
        viewModel?.fetchNearbyParking(lat = 39.5095, lng = 116.4105, radius = 3000)
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
        "main_road" -> "机场高速"
        "airport_expressway" -> "机场高速"
        "truck_main_road" -> "货运主干道"
        "terminal_road" -> "航站楼连接路"
        else -> rawRoadName ?: "主干道"
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
        // 当前道路信息（始终显示）
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CarGreenLight)
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
                    selectedTimeRange = newRange  // 这会触发LaunchedEffect重新调用API
                }
            },
            primaryColor = CarGreen
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            // 加载状态
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
                        CircularProgressIndicator(color = CarGreen)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "正在获取${selectedTimeRange}预测数据...", fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }
        } else if (congestionData.isEmpty()) {
            // 后端无数据
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
                        primaryColor = CarGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 选中时间点详情
            congestionData.getOrNull(selectedDataIndex)?.let { dataPoint ->
                CongestionDetailCard(dataPoint = dataPoint, primaryColor = CarGreen)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 智能建议（后端返回）
            if (serverSuggestion != null) {
                TipCard(
                    text = serverSuggestion,
                    icon = Icons.Rounded.Lightbulb,
                    backgroundColor = CarGreenLight,
                    iconColor = CarGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ★★★ 停车场实时状态（后端数据）★★★
        Text(text = "停车场实时状态", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        if (parkingList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(color = CarGreen, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(text = "暂无停车场数据", fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }
        } else {
            parkingList.forEach { parking ->
                val occupancyRate = if (parking.totalSpots > 0) 1f - (parking.availableSpots.toFloat() / parking.totalSpots) else 0f
                val level = when {
                    occupancyRate < 0.5f -> CongestionLevel.FREE
                    occupancyRate < 0.7f -> CongestionLevel.LIGHT
                    occupancyRate < 0.9f -> CongestionLevel.MODERATE
                    else -> CongestionLevel.SEVERE
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
                            Icon(imageVector = Icons.Rounded.LocalParking, contentDescription = null, tint = CarGreen, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = parking.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text(text = "空位: ${parking.availableSpots}/${parking.totalSpots} · ${parking.price}", fontSize = 12.sp, color = TextSecondary)
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarHistoryScreen(navController: NavController, viewModel: MainViewModel? = null) {
    var selectedTab by remember { mutableStateOf(1) } // 默认选中"本月"
    val tabs = listOf("本周", "本月", "全部")

    // 获取当前日期用于筛选
    val currentDate = remember { java.time.LocalDate.now() }

    // 完整的历史数据（模拟更多数据）
    val allHistoryRecords = remember {
        listOf(
            // 本周数据（最近7天）
            CarHistoryRecord(currentDate.minusDays(0).toString(), "家 → T2航站楼", 28.5, "42分钟", "接人"),
            CarHistoryRecord(currentDate.minusDays(1).toString(), "T1航站楼 → 万达广场", 15.2, "25分钟", "日常"),
            CarHistoryRecord(currentDate.minusDays(2).toString(), "公司 → 家", 18.0, "35分钟", "通勤"),
            CarHistoryRecord(currentDate.minusDays(3).toString(), "家 → 高铁站", 22.3, "38分钟", "送人"),
            CarHistoryRecord(currentDate.minusDays(4).toString(), "银泰商场 → 家", 12.5, "20分钟", "日常"),
            CarHistoryRecord(currentDate.minusDays(5).toString(), "家 → 公司", 18.0, "32分钟", "通勤"),
            CarHistoryRecord(currentDate.minusDays(6).toString(), "机场高速 → 市区", 35.0, "55分钟", "日常"),
            // 本月数据（7-30天前）
            CarHistoryRecord(currentDate.minusDays(8).toString(), "家 → 公司", 18.0, "30分钟", "通勤"),
            CarHistoryRecord(currentDate.minusDays(10).toString(), "超市 → 家", 5.2, "12分钟", "日常"),
            CarHistoryRecord(currentDate.minusDays(12).toString(), "家 → 医院", 8.5, "18分钟", "其他"),
            CarHistoryRecord(currentDate.minusDays(15).toString(), "公司 → 家", 18.0, "35分钟", "通勤"),
            CarHistoryRecord(currentDate.minusDays(18).toString(), "家 → 火车站", 25.0, "40分钟", "送人"),
            CarHistoryRecord(currentDate.minusDays(20).toString(), "商场 → 家", 10.0, "22分钟", "日常"),
            CarHistoryRecord(currentDate.minusDays(22).toString(), "家 → 公司", 18.0, "28分钟", "通勤"),
            CarHistoryRecord(currentDate.minusDays(25).toString(), "机场 → 家", 32.0, "48分钟", "接人"),
            CarHistoryRecord(currentDate.minusDays(28).toString(), "家 → 公司", 18.0, "33分钟", "通勤"),
            // 更早的数据（30天以上）
            CarHistoryRecord(currentDate.minusDays(35).toString(), "家 → 景区", 45.0, "60分钟", "其他"),
            CarHistoryRecord(currentDate.minusDays(40).toString(), "公司 → 家", 18.0, "30分钟", "通勤"),
            CarHistoryRecord(currentDate.minusDays(45).toString(), "家 → 高铁站", 22.3, "35分钟", "送人"),
            CarHistoryRecord(currentDate.minusDays(50).toString(), "朋友家 → 家", 15.0, "25分钟", "日常"),
            CarHistoryRecord(currentDate.minusDays(60).toString(), "家 → 公司", 18.0, "32分钟", "通勤")
        )
    }

    // 根据选中的Tab筛选数据
    val filteredRecords = remember(selectedTab, allHistoryRecords) {
        when (selectedTab) {
            0 -> { // 本周（本周一到今天）
                val startOfWeek = currentDate.with(java.time.DayOfWeek.MONDAY)
                allHistoryRecords.filter {
                    val recordDate = java.time.LocalDate.parse(it.date)
                    recordDate >= startOfWeek && recordDate <= currentDate
                }
            }
            1 -> { // 本月（本月1号到今天）
                val startOfMonth = currentDate.withDayOfMonth(1)
                allHistoryRecords.filter {
                    val recordDate = java.time.LocalDate.parse(it.date)
                    recordDate >= startOfMonth && recordDate <= currentDate
                }
            }
            else -> allHistoryRecords // 全部
        }
    }

    // 根据筛选后的数据计算统计
    val stats = remember(filteredRecords) {
        val totalTrips = filteredRecords.size
        val totalDistance = filteredRecords.sumOf { it.distance }
        val totalMinutes = filteredRecords.sumOf {
            it.duration.replace("分钟", "").toIntOrNull() ?: 0
        }
        val totalHours = totalMinutes / 60

        val commuteCount = filteredRecords.count { it.tripType == "通勤" }
        val dailyCount = filteredRecords.count { it.tripType == "日常" }
        val pickupCount = filteredRecords.count { it.tripType == "接人" || it.tripType == "送人" }
        val otherCount = filteredRecords.count { it.tripType == "其他" }

        CarHistoryStats(
            totalTrips = totalTrips,
            totalDistance = totalDistance.toInt(),
            totalHours = if (totalHours > 0) "${totalHours}h" else "${totalMinutes}min",
            commuteCount = commuteCount,
            dailyCount = dailyCount,
            pickupCount = pickupCount,
            otherCount = otherCount
        )
    }

    // 统计卡片标题
    val statsTitle = when (selectedTab) {
        0 -> "本周出行统计"
        1 -> "本月出行统计"
        else -> "全部出行统计"
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
                    text = statsTitle,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CarStatItem(value = "${stats.totalTrips}", label = "出行次数")
                    CarStatItem(value = "${stats.totalDistance}", label = "总里程(km)")
                    CarStatItem(value = stats.totalHours, label = "行驶时长")
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                Spacer(modifier = Modifier.height(16.dp))

                // 出行类型统计
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CarStatItem(value = "${stats.commuteCount}", label = "通勤")
                    CarStatItem(value = "${stats.dailyCount}", label = "日常出行")
                    CarStatItem(value = "${stats.pickupCount}", label = "接送人")
                    CarStatItem(value = "${stats.otherCount}", label = "其他")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 出行趋势（简化图表）- 只在本周Tab显示
        if (selectedTab == 0) {
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

                    // 根据实际数据生成柱状图
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val days = listOf("一", "二", "三", "四", "五", "六", "日")
                        // 计算每天的出行距离
                        val weekData = (0..6).map { dayOffset ->
                            val targetDate = currentDate.minusDays((6 - dayOffset).toLong())
                            filteredRecords
                                .filter { java.time.LocalDate.parse(it.date) == targetDate }
                                .sumOf { it.distance }
                                .toInt()
                        }
                        val maxValue = (weekData.maxOrNull() ?: 1).coerceAtLeast(1)

                        weekData.forEachIndexed { index, value ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val barHeight = if (maxValue > 0) (value * 70 / maxValue).coerceAtLeast(if (value > 0) 5 else 0) else 0
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(barHeight.dp)
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
        }

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

        // 历史记录列表标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "出行记录",
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

        // 列表内容
        if (filteredRecords.isEmpty()) {
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
                        text = "暂无出行记录",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            filteredRecords.forEach { record ->
                CarHistoryRecordCard(
                    record = record,
                    primaryColor = CarGreen
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// ==================== 私家车历史统计数据类（新增）====================
data class CarHistoryStats(
    val totalTrips: Int,
    val totalDistance: Int,
    val totalHours: String,
    val commuteCount: Int,
    val dailyCount: Int,
    val pickupCount: Int,
    val otherCount: Int
)

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tripType by remember { mutableStateOf("flight") }
    var tripNumber by remember { mutableStateOf("") }
    var tripDate by remember { mutableStateOf("") }
    val tripState by viewModel?.tripState?.collectAsState() ?: remember { mutableStateOf(TripState.Idle) }
    val trips by viewModel?.trips?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isLoading = tripState is TripState.Loading

    // ==================== 图片识别相关状态 ====================
    var showImagePickerDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isRecognizing by remember { mutableStateOf(false) }
    var recognitionResult by remember { mutableStateOf<TripOcrResult?>(null) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // ==================== 位置共享相关状态 ====================
    var showJoinShareDialog by remember { mutableStateOf(false) }
    var joinShareId by remember { mutableStateOf("") }
    var isJoiningShare by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }

    // ==================== OCR识别开关 ====================
    // true = 使用模拟数据（无需百度API）
    // false = 调用百度OCR真实识别
    val USE_MOCK_OCR = false  // ★ 已启用真实识别

    // 执行OCR识别
    fun performOcrRecognition(imageUri: Uri, currentTripType: String) {
        isRecognizing = true
        recognitionResult = null

        scope.launch {
            if (USE_MOCK_OCR) {
                // ==================== Mock模式：返回模拟数据 ====================
                delay(1500) // 模拟识别过程

                val mockResult = if (currentTripType == "flight") {
                    TripOcrResult(
                        tripType = "flight",
                        tripNumber = "MU${(1000..9999).random()}",
                        tripDate = "2026-01-${(20..28).random()}",
                        departureCity = "长沙",
                        arrivalCity = "北京",
                        departureTime = "${(6..20).random()}:${listOf("00", "30", "45").random()}",
                        passengerName = "张*明",
                        seatInfo = "${(1..30).random()}${listOf("A", "B", "C", "D", "E", "F").random()}",
                        confidence = 0.95f
                    )
                } else {
                    TripOcrResult(
                        tripType = "train",
                        tripNumber = "${listOf("G", "D", "K", "Z").random()}${(100..9999).random()}",
                        tripDate = "2026-01-${(20..28).random()}",
                        departureCity = "长沙南",
                        arrivalCity = "广州南",
                        departureTime = "${(6..22).random()}:${listOf("00", "15", "30", "45").random()}",
                        passengerName = "张*明",
                        seatInfo = "${(1..16).random()}车${(1..100).random()}${listOf("A", "B", "C", "D", "F").random()}座",
                        confidence = 0.93f
                    )
                }

                recognitionResult = mockResult
                tripNumber = mockResult.tripNumber
                tripDate = mockResult.tripDate
                tripType = mockResult.tripType
                isRecognizing = false

            } else {
                // ==================== 真实模式：调用百度OCR ====================
                try {
                    val ocrResult = com.example.smartlogistics.utils.BaiduOcrHelper.recognizeTicket(
                        context = context,
                        imageUri = imageUri,
                        tripType = currentTripType
                    )

                    if (ocrResult.success) {
                        val result = TripOcrResult(
                            tripType = ocrResult.tripType,
                            tripNumber = ocrResult.tripNumber,
                            tripDate = ocrResult.tripDate,
                            departureCity = ocrResult.departureStation,
                            arrivalCity = ocrResult.arrivalStation,
                            departureTime = ocrResult.departureTime,
                            passengerName = ocrResult.passengerName,
                            seatInfo = ocrResult.seatInfo,
                            confidence = 0.95f
                        )

                        recognitionResult = result
                        tripNumber = result.tripNumber
                        tripDate = result.tripDate
                        tripType = result.tripType
                    } else {
                        // 识别失败，显示错误
                        Toast.makeText(context, ocrResult.errorMsg ?: "识别失败", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "识别异常: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isRecognizing = false
                }
            }
        }
    }

    // 相机拍照
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraPhotoUri != null) {
            selectedImageUri = cameraPhotoUri
            performOcrRecognition(cameraPhotoUri!!, tripType)
        }
    }

    // 相册选择
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            performOcrRecognition(it, tripType)
        }
    }

    // 相机权限
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = CameraUtils.createImageUri(context)
            cameraPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    DetailScreenTemplate(navController = navController, title = "我的行程", backgroundColor = BackgroundPrimary) {
        // ==================== 加入位置共享 ====================
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
                                text = "加入位置共享",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "输入分享码查看对方实时位置",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showJoinShareDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667EEA))
                ) {
                    Icon(imageVector = Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "输入分享码", fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "日期: ${trip.tripDate}", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)

                        // 共享位置按钮
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                // 跳转到位置共享页面，传递tripId
                                trip.id?.let { tripId ->
                                    navController.navigate("location_share/share/$tripId")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ShareLocation,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "共享实时位置",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ==================== 智能识别卡片 ====================
        Text(text = "智能识别", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(color = CarGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Rounded.DocumentScanner, contentDescription = null, tint = CarGreen, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "拍照识别行程", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(text = "拍摄机票、火车票自动识别信息", fontSize = 13.sp, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 图片预览区域
                if (selectedImageUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BackgroundSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(selectedImageUri),
                            contentDescription = "票据图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        if (isRecognizing) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(text = "正在识别票据...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        if (!isRecognizing) {
                            IconButton(
                                onClick = { selectedImageUri = null; recognitionResult = null; tripNumber = ""; tripDate = "" },
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "清除", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // 识别结果展示
                    if (recognitionResult != null) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().background(SuccessGreenLight, RoundedCornerShape(8.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "识别成功！", fontSize = 14.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 识别详情卡片
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CarGreen.copy(alpha = 0.05f)),
                            border = BorderStroke(1.dp, CarGreen.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (recognitionResult!!.tripType == "flight") Icons.Rounded.Flight else Icons.Rounded.Train,
                                        contentDescription = null, tint = CarGreen, modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = recognitionResult!!.tripNumber, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CarGreen)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(color = CarGreen, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = if (recognitionResult!!.tripType == "flight") "航班" else "火车",
                                            fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(text = recognitionResult!!.departureCity ?: "--", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text(text = recognitionResult!!.departureTime ?: "--:--", fontSize = 14.sp, color = TextSecondary)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 16.dp)) {
                                        Icon(imageVector = Icons.Rounded.ArrowForward, contentDescription = null, tint = CarGreen, modifier = Modifier.size(24.dp))
                                        Text(text = recognitionResult!!.tripDate, fontSize = 12.sp, color = TextTertiary)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = recognitionResult!!.arrivalCity ?: "--", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text(text = "预计到达", fontSize = 14.sp, color = TextSecondary)
                                    }
                                }

                                if (recognitionResult!!.passengerName != null || recognitionResult!!.seatInfo != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = DividerColor)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        if (recognitionResult!!.passengerName != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(imageVector = Icons.Rounded.Person, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(text = recognitionResult!!.passengerName!!, fontSize = 13.sp, color = TextSecondary)
                                            }
                                        }
                                        if (recognitionResult!!.seatInfo != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(imageVector = Icons.Rounded.EventSeat, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(text = recognitionResult!!.seatInfo!!, fontSize = 13.sp, color = TextSecondary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "💡 信息已自动填充到下方表单", fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(horizontal = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 拍照/相册按钮
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showImagePickerDialog = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, CarGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CarGreen)
                    ) {
                        Icon(imageVector = Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "拍照识别", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, CarGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CarGreen)
                    ) {
                        Icon(imageVector = Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "相册选择", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==================== 手动添加行程 ====================
        Text(text = "手动添加", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("flight" to "航班" to Icons.Rounded.Flight, "train" to "火车" to Icons.Rounded.Train).forEach { (typeLabel, icon) ->
                val (type, label) = typeLabel
                Card(modifier = Modifier.weight(1f).height(72.dp).clickable { tripType = type }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (tripType == type) CarGreen.copy(alpha = 0.1f) else Color.White), border = if (tripType == type) BorderStroke(2.dp, CarGreen) else null) {
                    Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = if (tripType == type) CarGreen else TextSecondary, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, fontSize = 15.sp, fontWeight = if (tripType == type) FontWeight.SemiBold else FontWeight.Normal, color = if (tripType == type) CarGreen else TextSecondary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                StyledTextField(value = tripNumber, onValueChange = { tripNumber = it.uppercase() }, label = if (tripType == "flight") "航班号 (如 MU5521)" else "车次号 (如 G1234)", leadingIcon = if (tripType == "flight") Icons.Rounded.Flight else Icons.Rounded.Train)
                Spacer(modifier = Modifier.height(16.dp))
                StyledTextField(value = tripDate, onValueChange = { tripDate = it }, label = "出发日期 (如 2025-01-20)", leadingIcon = Icons.Rounded.CalendarToday)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        PrimaryButton(text = "关联行程", onClick = { viewModel?.createTrip(tripType, tripNumber, tripDate) }, isLoading = isLoading, enabled = tripNumber.isNotBlank() && tripDate.isNotBlank(), backgroundColor = CarGreen, icon = Icons.Rounded.Add)
    }

    // ==================== 图片来源选择对话框 ====================
    if (showImagePickerDialog) {
        AlertDialog(
            onDismissRequest = { showImagePickerDialog = false },
            title = { Text(text = "选择图片来源", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            showImagePickerDialog = false
                            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                val uri = CameraUtils.createImageUri(context)
                                cameraPhotoUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        }.padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(44.dp).background(CarGreen.copy(alpha = 0.1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Rounded.CameraAlt, contentDescription = null, tint = CarGreen, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(text = "拍照", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(text = "使用相机拍摄票据", fontSize = 13.sp, color = TextSecondary)
                        }
                    }

                    HorizontalDivider(color = DividerColor)

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            showImagePickerDialog = false
                            galleryLauncher.launch("image/*")
                        }.padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(44.dp).background(Color(0xFF667EEA).copy(alpha = 0.1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Rounded.PhotoLibrary, contentDescription = null, tint = Color(0xFF667EEA), modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(text = "从相册选择", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(text = "选择已有的票据图片", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showImagePickerDialog = false }) { Text("取消", color = TextSecondary) } }
        )
    }

    // ==================== 加入位置共享对话框 ====================
    if (showJoinShareDialog) {
        AlertDialog(
            onDismissRequest = {
                showJoinShareDialog = false
                joinShareId = ""
                shareError = null
            },
            title = {
                Text(
                    text = "加入位置共享",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "输入对方分享给你的分享码，即可查看对方的实时位置",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = joinShareId,
                        onValueChange = {
                            joinShareId = it.uppercase().take(8)
                            shareError = null
                        },
                        label = { Text("分享码") },
                        placeholder = { Text("如: A1B2C3D4") },
                        singleLine = true,
                        isError = shareError != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF667EEA),
                            unfocusedBorderColor = DividerColor
                        )
                    )

                    if (shareError != null) {
                        Text(
                            text = shareError!!,
                            color = Color(0xFFE53935),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "💡 分享码由对方在「共享实时位置」时生成",
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (joinShareId.isBlank()) {
                            shareError = "请输入分享码"
                        } else if (joinShareId.length < 6) {
                            shareError = "分享码格式不正确"
                        } else {
                            showJoinShareDialog = false
                            // 跳转到查看页面
                            navController.navigate("location_share/view/$joinShareId")
                            joinShareId = ""
                        }
                    },
                    enabled = !isJoiningShare,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667EEA))
                ) {
                    if (isJoiningShare) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("加入")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showJoinShareDialog = false
                    joinShareId = ""
                    shareError = null
                }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }
}