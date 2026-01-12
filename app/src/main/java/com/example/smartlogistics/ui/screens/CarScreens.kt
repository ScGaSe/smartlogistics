package com.example.smartlogistics.ui.screens

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
    
    LaunchedEffect(vehicleState) {
        if (vehicleState is VehicleState.BindSuccess) { plateNumber = ""; viewModel?.resetVehicleState() }
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
                Card(modifier = Modifier.fillMaxWidth().clickable { }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CarGreen.copy(alpha = 0.1f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(imageVector = Icons.Rounded.CameraAlt, contentDescription = null, tint = CarGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "AI智能识别车牌", fontSize = 15.sp, color = CarGreen, fontWeight = FontWeight.Medium)
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
        
        // ==================== 寻车助手模块 ====================
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "寻车助手", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 记录停车位置区域
                Text(text = "记录停车位置", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 标记位置按钮
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
                    
                    // 拍照记录按钮
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
                
                // 找车区域
                Text(text = "找车", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 导航找车按钮
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
                    
                    // 查看照片按钮
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
                // 提示信息
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
                        text = "停车后记录位置，方便您快速找到爱车",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

// ==================== 路线规划页面 ====================
@Composable
fun CarRouteScreen(navController: NavController, viewModel: MainViewModel? = null) {
    var destination by remember { mutableStateOf("") }
    
    DetailScreenTemplate(navController = navController, title = "路线规划", backgroundColor = BackgroundPrimary) {
        AiEntryCard(title = "语音导航", subtitle = "说出目的地，智能规划路线", primaryColor = CarGreen, onClick = { navController.navigate("ai_chat") })
        Spacer(modifier = Modifier.height(20.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(CarGreen, CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "我的位置", fontSize = 15.sp, color = TextPrimary)
                }
                Box(modifier = Modifier.padding(start = 5.dp, top = 4.dp, bottom = 4.dp).width(2.dp).height(24.dp).background(BorderLight))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(ErrorRed, CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(value = destination, onValueChange = { destination = it }, placeholder = { Text("输入目的地", color = TextTertiary) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CarGreen, unfocusedBorderColor = BorderLight), singleLine = true)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "快捷目的地", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        
        listOf(Icons.Rounded.Home to "家", Icons.Rounded.Work to "公司", Icons.Rounded.Flight to "机场").forEach { (icon, name) ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { destination = name; navController.navigate("ai_chat") }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null, tint = CarGreen, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = name, fontSize = 15.sp, color = TextPrimary)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(text = "开始导航", onClick = { navController.navigate("navigation_map") }, enabled = destination.isNotBlank(), backgroundColor = CarGreen, icon = Icons.Rounded.Navigation)
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
    DetailScreenTemplate(navController = navController, title = "拥堵预测", backgroundColor = BackgroundPrimary) {
        Card(modifier = Modifier.fillMaxWidth().height(280.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "未来2小时拥堵预测", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(BackgroundSecondary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text(text = "拥堵趋势图\n(TTI指数)", color = TextSecondary, textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "🟢 当前: 畅通", fontSize = 13.sp, color = CongestionFree)
                    Text(text = "⚠️ 15:45预计拥堵", fontSize = 13.sp, color = CongestionModerate)
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        TipCard(text = "建议避开15:30-16:00时段出行。", icon = Icons.Rounded.Lightbulb, backgroundColor = CarGreenLight, iconColor = CarGreen)
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "停车场入口预测", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        listOf(Triple("P1停车场", "畅通", CongestionFree), Triple("P2停车场", "缓行", CongestionLight), Triple("P3停车场", "拥堵", CongestionModerate)).forEach { (name, status, color) ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.LocalParking, contentDescription = null, tint = CarGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = name, fontSize = 15.sp, color = TextPrimary)
                    }
                    Text(text = status, fontSize = 14.sp, color = color, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ==================== 历史数据页面 ====================
@Composable
fun CarHistoryScreen(navController: NavController, viewModel: MainViewModel? = null) {
    DetailScreenTemplate(navController = navController, title = "历史数据", backgroundColor = BackgroundPrimary) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CarGreen)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "本月出行统计", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "28", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text(text = "出行次数", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "486km", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text(text = "行驶里程", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "最近出行", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        listOf(Triple("2024-12-06", "T2航站楼 → 万达广场", "32km"), Triple("2024-12-05", "家 → T1航站楼", "28km")).forEach { (date, route, distance) ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).background(CarGreenLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Rounded.Route, contentDescription = null, tint = CarGreen, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = route, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Text(text = date, fontSize = 12.sp, color = TextSecondary)
                    }
                    Text(text = distance, fontSize = 14.sp, color = CarGreen, fontWeight = FontWeight.SemiBold)
                }
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
