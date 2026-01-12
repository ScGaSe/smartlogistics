package com.example.smartlogistics.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.smartlogistics.ui.components.*
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.MainViewModel
import com.example.smartlogistics.viewmodel.VehicleState
import com.example.smartlogistics.viewmodel.ReportState

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
    var plateNumber by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("truck") }
    var heightM by remember { mutableStateOf("") }
    var weightT by remember { mutableStateOf("") }
    var axleCount by remember { mutableStateOf("2") }
    val vehicleState by viewModel?.vehicleState?.collectAsState() ?: remember { mutableStateOf(VehicleState.Idle) }
    val vehicles by viewModel?.vehicles?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isLoading = vehicleState is VehicleState.Loading
    
    LaunchedEffect(vehicleState) { if (vehicleState is VehicleState.BindSuccess) { plateNumber = ""; heightM = ""; weightT = ""; viewModel?.resetVehicleState() } }
    
    DetailScreenTemplate(navController = navController, title = "车辆绑定", backgroundColor = BackgroundPrimary) {
        if (vehicles.isNotEmpty()) {
            Text(text = "已绑定车辆", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            vehicles.forEach { vehicle ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).background(TruckOrangeLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Rounded.LocalShipping, contentDescription = null, tint = TruckOrange, modifier = Modifier.size(28.dp))
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
        
        Text(text = "添加货运车辆", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Card(modifier = Modifier.fillMaxWidth().clickable { }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = TruckOrange.copy(alpha = 0.1f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(imageVector = Icons.Rounded.CameraAlt, contentDescription = null, tint = TruckOrange, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "AI智能识别车牌", fontSize = 15.sp, color = TruckOrange, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                StyledTextField(value = plateNumber, onValueChange = { plateNumber = it.uppercase() }, label = "车牌号", leadingIcon = Icons.Rounded.Pin)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "车辆类型", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("truck" to "普通货车", "hazmat" to "危化品车", "refrigerated" to "冷链车").forEach { (type, label) ->
                        FilterChip(selected = vehicleType == type, onClick = { vehicleType = type }, label = { Text(label, fontSize = 13.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TruckOrange.copy(alpha = 0.2f), selectedLabelColor = TruckOrange))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "车辆参数", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = heightM, onValueChange = { heightM = it }, label = { Text("车高(米)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TruckOrange, unfocusedBorderColor = BorderLight))
                    OutlinedTextField(value = weightT, onValueChange = { weightT = it }, label = { Text("载重(吨)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TruckOrange, unfocusedBorderColor = BorderLight))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "轴数", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("2", "3", "4", "5", "6+").forEach { count ->
                        FilterChip(selected = axleCount == count, onClick = { axleCount = count }, label = { Text("$count 轴") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TruckOrange.copy(alpha = 0.2f), selectedLabelColor = TruckOrange))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(text = "绑定车辆", onClick = { viewModel?.bindVehicle(plateNumber, vehicleType, heightM.toDoubleOrNull(), weightT.toDoubleOrNull(), axleCount.replace("+", "").toIntOrNull()) }, isLoading = isLoading, enabled = plateNumber.isNotBlank(), backgroundColor = TruckOrange, icon = Icons.Rounded.Add)
    }
}

// ==================== 路线规划页面 ====================
@Composable
fun TruckRouteScreen(navController: NavController, viewModel: MainViewModel? = null) {
    var destination by remember { mutableStateOf("") }
    DetailScreenTemplate(navController = navController, title = "路线规划", backgroundColor = BackgroundPrimary) {
        AiEntryCard(title = "语音导航", subtitle = "说出目的地，智能规划货车路线", primaryColor = TruckOrange, onClick = { navController.navigate("ai_chat") })
        Spacer(modifier = Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(TruckOrange, CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "我的位置", fontSize = 15.sp, color = TextPrimary)
                }
                Box(modifier = Modifier.padding(start = 5.dp, top = 4.dp, bottom = 4.dp).width(2.dp).height(24.dp).background(BorderLight))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(ErrorRed, CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(value = destination, onValueChange = { destination = it }, placeholder = { Text("输入仓库、货站名称", color = TextTertiary) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TruckOrange, unfocusedBorderColor = BorderLight), singleLine = true)
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        TipCard(text = "系统将自动避开限高、限重路段，并考虑危化品车辆通行限制。", icon = Icons.Rounded.Info, backgroundColor = TruckOrangeLight, iconColor = TruckOrange)
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(text = "开始导航", onClick = { navController.navigate("navigation_map") }, enabled = destination.isNotBlank(), backgroundColor = TruckOrange, icon = Icons.Rounded.Navigation)
    }
}

// ==================== 道路实况页面 ====================
@Composable
fun TruckRoadScreen(navController: NavController, viewModel: MainViewModel? = null) {
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
            TruckTrafficLegendItem(color = CongestionFree, label = "畅通")
            TruckTrafficLegendItem(color = CongestionLight, label = "缓行")
            TruckTrafficLegendItem(color = CongestionModerate, label = "拥堵")
            TruckTrafficLegendItem(color = CongestionSevere, label = "严重")
        }
    }
}

// ==================== 拥堵预测页面 ====================
@Composable
fun TruckCongestionScreen(navController: NavController, viewModel: MainViewModel? = null) {
    DetailScreenTemplate(navController = navController, title = "拥堵预测", backgroundColor = BackgroundPrimary) {
        Card(modifier = Modifier.fillMaxWidth().height(280.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "货运主干道拥堵预测", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(BackgroundSecondary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text(text = "拥堵趋势图\n(TTI指数)", color = TextSecondary, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        TipCard(text = "建议避开16:00-17:30时段通过北门闸口。", icon = Icons.Rounded.Lightbulb, backgroundColor = TruckOrangeLight, iconColor = TruckOrange)
    }
}

// ==================== 历史数据页面 ====================
@Composable
fun TruckHistoryScreen(navController: NavController, viewModel: MainViewModel? = null) {
    DetailScreenTemplate(navController = navController, title = "历史数据", backgroundColor = BackgroundPrimary) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = TruckOrange)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "本月运输统计", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "156", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text(text = "运输单数", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "2,486km", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text(text = "总里程", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ==================== 货物报备页面 ====================
@Composable
fun CargoReportScreen(navController: NavController, viewModel: MainViewModel? = null) {
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
    
    LaunchedEffect(reportState) { if (reportState is ReportState.SubmitSuccess) { navController.popBackStack() } }
    
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
                    
                    // 语音填写按钮
                    Surface(
                        modifier = Modifier.clickable { navController.navigate("ai_chat") },
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
                
                // 过滤无效车辆数据 (包含 "string" 的都过滤掉)
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
                            FilterChip(selected = selectedVehicleId == vehicle.vehicleId, onClick = { selectedVehicleId = vehicle.vehicleId ?: "" }, label = { Text(vehicle.plateNumber) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TruckOrange.copy(alpha = 0.2f), selectedLabelColor = TruckOrange))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "货物类型", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("普通货物", "冷链货物", "危险品").forEach { type ->
                        FilterChip(selected = cargoType == type, onClick = { cargoType = type; isHazardous = type == "危险品" }, label = { Text(type) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = if (type == "危险品") ErrorRed.copy(alpha = 0.2f) else TruckOrange.copy(alpha = 0.2f), selectedLabelColor = if (type == "危险品") ErrorRed else TruckOrange))
                    }
                }
                if (isHazardous) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TipCard(text = "危化品运输需要特殊审批。", icon = Icons.Rounded.Warning, backgroundColor = ErrorRedLight, iconColor = ErrorRed)
                    Spacer(modifier = Modifier.height(12.dp))
                    StyledTextField(value = hazardClass, onValueChange = { hazardClass = it }, label = "危化品类别", leadingIcon = Icons.Rounded.Warning)
                }
                Spacer(modifier = Modifier.height(16.dp))
                StyledTextField(value = weight, onValueChange = { weight = it }, label = "货物重量 (吨)", leadingIcon = Icons.Rounded.Scale, keyboardType = KeyboardType.Decimal)
                Spacer(modifier = Modifier.height(16.dp))
                StyledTextField(value = destination, onValueChange = { destination = it }, label = "目的地", leadingIcon = Icons.Rounded.LocationOn)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 提交报备按钮
        PrimaryButton(
            text = "提交报备",
            onClick = { viewModel?.submitReport(selectedVehicleId, destination, cargoType, isHazardous, hazardClass.ifBlank { null }, weight.toDoubleOrNull(), description.ifBlank { null }) },
            isLoading = isLoading,
            enabled = selectedVehicleId.isNotBlank() && destination.isNotBlank(),
            backgroundColor = TruckOrange,
            icon = Icons.Rounded.Send
        )
    }
}
