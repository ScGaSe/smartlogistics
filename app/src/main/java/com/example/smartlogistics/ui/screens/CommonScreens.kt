package com.example.smartlogistics.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.smartlogistics.ui.components.*
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.AIState
import com.example.smartlogistics.viewmodel.MainViewModel
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.*
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.amap.api.services.route.*
import android.graphics.Color as AndroidColor

// ==================== 地图导航页面 (高德地图版) ====================
@Composable
fun NavigationMapScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    
    // 地图状态
    var showTraffic by remember { mutableStateOf(true) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var destination by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(true) }
    
    // ★ 新增：搜索相关状态
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<PoiItem>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }
    var selectedPoi by remember { mutableStateOf<PoiItem?>(null) }
    
    // ★ 新增：路线相关状态
    var isLoadingRoute by remember { mutableStateOf(false) }
    var routePath by remember { mutableStateOf<DrivePath?>(null) }
    var showRouteInfo by remember { mutableStateOf(false) }
    
    // ★ 新增：地图引用
    var aMapRef by remember { mutableStateOf<AMap?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    
    // 模式判断
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen
    
    // 权限请求
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!hasLocationPermission) {
            Toast.makeText(context, "需要定位权限才能显示当前位置", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 请求权限
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // ==================== 高德地图 ====================
        AMapView(
            modifier = Modifier.fillMaxSize(),
            showMyLocation = hasLocationPermission,
            showTraffic = showTraffic,
            markers = emptyList(),
            onMapReady = { aMap ->
                aMapRef = aMap
            },
            onLocationChanged = { location ->
                currentLocation = LatLng(location.latitude, location.longitude)
            }
        )
        
        // ==================== 顶部搜索栏 ====================
        AnimatedVisibility(
            visible = isSearchExpanded,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 返回按钮和标题
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "返回",
                                    tint = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "导航",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            // 收起按钮
                            IconButton(onClick = { isSearchExpanded = false }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "收起",
                                    tint = TextSecondary
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // 起点
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(primaryColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (currentLocation != null) "我的位置 (已定位)" else "我的位置 (定位中...)",
                                fontSize = 15.sp,
                                color = if (currentLocation != null) TextPrimary else TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = null,
                                tint = if (currentLocation != null) primaryColor else TextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // 连接线
                        Box(
                            modifier = Modifier
                                .padding(start = 5.dp, top = 4.dp, bottom = 4.dp)
                                .width(2.dp)
                                .height(20.dp)
                                .background(BorderLight)
                        )
                        
                        // 终点输入
                        OutlinedTextField(
                            value = destination,
                            onValueChange = { 
                                destination = it
                                // 清除之前的选择
                                if (selectedPoi != null) {
                                    selectedPoi = null
                                    showRouteInfo = false
                                    routePath = null
                                    routePolyline?.remove()
                                    destinationMarker?.remove()
                                }
                            },
                            placeholder = { Text("输入目的地...", color = TextTertiary) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(0xFFEA4335), CircleShape)
                                )
                            },
                            trailingIcon = {
                                if (destination.isNotBlank()) {
                                    IconButton(onClick = { 
                                        destination = ""
                                        searchResults = emptyList()
                                        showSearchResults = false
                                        selectedPoi = null
                                        showRouteInfo = false
                                        routePath = null
                                        routePolyline?.remove()
                                        destinationMarker?.remove()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "清除",
                                            tint = TextSecondary
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = BorderLight,
                                unfocusedContainerColor = BackgroundSecondary,
                                focusedContainerColor = Color.White
                            ),
                            singleLine = true
                        )
                        
                        // ★ 搜索按钮 - 真正的POI搜索
                        if (destination.isNotBlank() && selectedPoi == null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    // ★★★ 调试：获取运行时SHA1（确认后删除这段代码）★★★
                                    try {
                                        @Suppress("DEPRECATION")
                                        val packageInfo = context.packageManager.getPackageInfo(
                                            context.packageName,
                                            android.content.pm.PackageManager.GET_SIGNATURES
                                        )
                                        for (signature in packageInfo.signatures) {
                                            val md = java.security.MessageDigest.getInstance("SHA1")
                                            md.update(signature.toByteArray())
                                            val sha1 = md.digest().joinToString(":") { "%02X".format(it) }
                                            android.util.Log.e("SHA1_DEBUG", "========================================")
                                            android.util.Log.e("SHA1_DEBUG", "运行时SHA1: $sha1")
                                            android.util.Log.e("SHA1_DEBUG", "PackageName: ${context.packageName}")
                                            android.util.Log.e("SHA1_DEBUG", "========================================")
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("SHA1_DEBUG", "获取SHA1失败: ${e.message}")
                                    }
                                    // ★★★ 调试代码结束 ★★★
                                    
                                    isSearching = true
                                    
                                    // ★★★ 简化版POI搜索 - 只用关键字搜索，不设置额外参数 ★★★
                                    // 参数1: 关键字
                                    // 参数2: POI类型（空=全部类型）
                                    // 参数3: 城市（空字符串=全国搜索）
                                    val query = PoiSearch.Query(destination, "", "")
                                    query.pageSize = 20
                                    query.pageNum = 0
                                    query.cityLimit = false  // 不限制城市
                                    
                                    val poiSearch = PoiSearch(context, query)
                                    
                                    poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                                        override fun onPoiSearched(result: PoiResult?, code: Int) {
                                            isSearching = false
                                            android.util.Log.d("POISearch", "搜索结果: code=$code, 结果数=${result?.pois?.size ?: 0}")
                                            
                                            when (code) {
                                                AMapException.CODE_AMAP_SUCCESS -> {
                                                    if (result?.pois != null && result.pois.isNotEmpty()) {
                                                        searchResults = result.pois
                                                        showSearchResults = true
                                                    } else {
                                                        searchResults = emptyList()
                                                        showSearchResults = false
                                                        Toast.makeText(context, "未找到相关地点", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                1008 -> {
                                                    // Key配置问题
                                                    android.util.Log.e("POISearch", "错误1008: Key配置问题，请检查SHA1和PackageName")
                                                    Toast.makeText(context, "服务配置错误，请检查API Key", Toast.LENGTH_LONG).show()
                                                }
                                                else -> {
                                                    android.util.Log.e("POISearch", "搜索失败: code=$code")
                                                    Toast.makeText(context, "搜索失败(错误码:$code)", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        override fun onPoiItemSearched(item: PoiItem?, code: Int) {}
                                    })
                                    poiSearch.searchPOIAsyn()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                enabled = !isSearching
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isSearching) "搜索中..." else "搜索")
                            }
                        }
                        
                        // ★ 规划路线按钮
                        if (selectedPoi != null && currentLocation != null && !showRouteInfo) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    isLoadingRoute = true
                                    
                                    val routeSearch = RouteSearch(context)
                                    val fromPoint = LatLonPoint(currentLocation!!.latitude, currentLocation!!.longitude)
                                    val toPoint = selectedPoi!!.latLonPoint
                                    val fromAndTo = RouteSearch.FromAndTo(fromPoint, toPoint)
                                    val query = RouteSearch.DriveRouteQuery(fromAndTo, RouteSearch.DRIVING_SINGLE_DEFAULT, null, null, "")
                                    
                                    routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
                                        override fun onDriveRouteSearched(result: DriveRouteResult?, code: Int) {
                                            isLoadingRoute = false
                                            if (code == AMapException.CODE_AMAP_SUCCESS && result?.paths != null && result.paths.isNotEmpty()) {
                                                routePath = result.paths[0]
                                                showRouteInfo = true
                                                
                                                // 在地图上绘制路线
                                                aMapRef?.let { map ->
                                                    routePolyline?.remove()
                                                    
                                                    val points = mutableListOf<LatLng>()
                                                    result.paths[0].steps.forEach { step ->
                                                        step.polyline.forEach { point ->
                                                            points.add(LatLng(point.latitude, point.longitude))
                                                        }
                                                    }
                                                    
                                                    val routeColor = if (isProfessional) 
                                                        AndroidColor.parseColor("#FF6D00") 
                                                    else 
                                                        AndroidColor.parseColor("#4285F4")
                                                    
                                                    routePolyline = map.addPolyline(
                                                        PolylineOptions()
                                                            .addAll(points)
                                                            .width(18f)
                                                            .color(routeColor)
                                                            .geodesic(true)
                                                    )
                                                    
                                                    if (points.size >= 2) {
                                                        val builder = LatLngBounds.Builder()
                                                        points.forEach { builder.include(it) }
                                                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100))
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "路线规划失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        override fun onBusRouteSearched(result: BusRouteResult?, code: Int) {}
                                        override fun onWalkRouteSearched(result: WalkRouteResult?, code: Int) {}
                                        override fun onRideRouteSearched(result: RideRouteResult?, code: Int) {}
                                    })
                                    routeSearch.calculateDriveRouteAsyn(query)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                enabled = !isLoadingRoute
                            ) {
                                if (isLoadingRoute) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Route,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isLoadingRoute) "规划中..." else "规划路线")
                            }
                        }
                    }
                }
                
                // ★ 搜索结果列表
                AnimatedVisibility(
                    visible = showSearchResults && searchResults.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(max = 300.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        LazyColumn {
                            items(searchResults) { poi ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPoi = poi
                                            destination = poi.title
                                            showSearchResults = false
                                            
                                            aMapRef?.let { map ->
                                                destinationMarker?.remove()
                                                destinationMarker = map.addMarker(
                                                    MarkerOptions()
                                                        .position(LatLng(poi.latLonPoint.latitude, poi.latLonPoint.longitude))
                                                        .title(poi.title)
                                                        .snippet(poi.snippet)
                                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                                )
                                                map.animateCamera(
                                                    CameraUpdateFactory.newLatLngZoom(
                                                        LatLng(poi.latLonPoint.latitude, poi.latLonPoint.longitude), 15f
                                                    )
                                                )
                                            }
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LocationOn,
                                            contentDescription = null,
                                            tint = primaryColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = poi.title ?: "",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = poi.snippet ?: poi.cityName ?: "",
                                            fontSize = 13.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (poi != searchResults.last()) {
                                    HorizontalDivider(color = DividerColor)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 展开搜索栏按钮 (当收起时显示)
        if (!isSearchExpanded) {
            FloatingActionButton(
                onClick = { isSearchExpanded = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .statusBarsPadding(),
                containerColor = Color.White,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
        }
        
        // ==================== 右侧控制按钮 ====================
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            // 路况开关
            FloatingActionButton(
                onClick = { showTraffic = !showTraffic },
                modifier = Modifier.size(48.dp),
                containerColor = if (showTraffic) primaryColor else Color.White,
                contentColor = if (showTraffic) Color.White else TextPrimary
            ) {
                Icon(Icons.Rounded.Traffic, contentDescription = "路况")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 图层切换
            FloatingActionButton(
                onClick = { /* 切换地图图层 */ },
                modifier = Modifier.size(48.dp),
                containerColor = Color.White,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Rounded.Layers, contentDescription = "图层")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 定位按钮
            FloatingActionButton(
                onClick = {
                    if (!hasLocationPermission) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        currentLocation?.let { loc ->
                            aMapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 17f))
                        }
                        Toast.makeText(context, "已定位到当前位置", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(56.dp),
                containerColor = primaryColor,
                contentColor = Color.White
            ) {
                Icon(
                    Icons.Rounded.MyLocation,
                    contentDescription = "定位",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        // ==================== 底部路线信息卡 ★ ====================
        AnimatedVisibility(
            visible = showRouteInfo && routePath != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            routePath?.let { path ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "路线方案",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            IconButton(
                                onClick = {
                                    showRouteInfo = false
                                    routePath = null
                                    routePolyline?.remove()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, "关闭", tint = TextSecondary)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 路线信息
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val distance = path.distance.toInt()
                                Text(
                                    text = if (distance >= 1000) String.format("%.1fkm", distance / 1000.0) else "${distance}m",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                                Text("距离", fontSize = 12.sp, color = TextSecondary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val duration = path.duration.toInt()
                                val hours = duration / 3600
                                val minutes = (duration % 3600) / 60
                                Text(
                                    text = if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                                Text("预计时间", fontSize = 12.sp, color = TextSecondary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val toll = path.tolls.toInt()
                                Text(
                                    text = if (toll > 0) "¥$toll" else "免费",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                                Text("过路费", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                        
                        if (path.totalTrafficlights > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "途经 ${path.totalTrafficlights} 个红绿灯",
                                fontSize = 12.sp,
                                color = TextTertiary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { Toast.makeText(context, "开始导航", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Icon(Icons.Rounded.Navigation, null, Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始导航", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        
        // ==================== 底部路况信息（没有路线时显示） ====================
        if (!showRouteInfo) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "实时路况",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (showTraffic) "已开启" else "已关闭",
                            fontSize = 12.sp,
                            color = if (showTraffic) primaryColor else TextTertiary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 路况图例
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TrafficLegendItem(color = CongestionFree, label = "畅通")
                        TrafficLegendItem(color = CongestionLight, label = "缓行")
                        TrafficLegendItem(color = CongestionModerate, label = "拥堵")
                        TrafficLegendItem(color = CongestionSevere, label = "严重")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 快捷操作
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.NearMe,
                            text = "附近",
                            onClick = { }
                        )
                        QuickActionButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.LocalParking,
                            text = "停车场",
                            onClick = { }
                        )
                        QuickActionButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.LocalGasStation,
                            text = "加油站",
                            onClick = { }
                        )
                        QuickActionButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Restaurant,
                            text = "美食",
                            onClick = { }
                        )
                    }
                }
            }
        }
    }
}

// ==================== 路况图例项 ====================
@Composable
private fun TrafficLegendItem(
    color: Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp, 4.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

// ==================== 快捷操作按钮 ====================
@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(BackgroundSecondary, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandBlue,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

// ==================== AI智能助手结果页 ====================
@Composable
fun AiResultScreen(
    navController: NavController,
    query: String,
    viewModel: MainViewModel? = null
) {
    val aiState by viewModel?.aiState?.collectAsState() ?: remember { mutableStateOf(AIState.Idle) }
    val aiResponse by viewModel?.aiResponse?.collectAsState() ?: remember { mutableStateOf(null) }
    
    var userInput by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<Pair<Boolean, String>>()) } // true = user, false = AI
    
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            chatHistory = chatHistory + (true to query)
            viewModel?.askAI(query)
        }
    }
    
    LaunchedEffect(aiResponse) {
        aiResponse?.let {
            chatHistory = chatHistory + (false to it.answer)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundPrimary)
        ) {
            // 顶部栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // AI头像
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(AIGradient, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = "AI智能助手",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "为您提供专业导航建议",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
            
            // 聊天内容区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 欢迎消息
                if (chatHistory.isEmpty()) {
                    AiWelcomeCard()
                }
                
                // 聊天历史
                chatHistory.forEach { (isUser, message) ->
                    if (isUser) {
                        UserMessageBubble(message = message)
                    } else {
                        AiMessageBubble(message = message)
                    }
                }
                
                // 加载状态
                if (aiState is AIState.Loading) {
                    AiTypingIndicator()
                }
                
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        
        // 底部输入区域
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入您的问题...", color = TextTertiary) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = BorderLight
                    ),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                FloatingActionButton(
                    onClick = {
                        if (userInput.isNotBlank()) {
                            chatHistory = chatHistory + (true to userInput)
                            viewModel?.askAI(userInput)
                            userInput = ""
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = BrandBlue
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Send,
                        contentDescription = "发送",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

// ==================== AI欢迎卡片 ====================
@Composable
private fun AiWelcomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(AIGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "您好，我是AI智能助手",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Text(
                text = "我可以帮您规划路线、查询路况、推荐停车场等",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 快捷问题
            Text(
                text = "您可以这样问我：",
                fontSize = 13.sp,
                color = TextTertiary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            listOf(
                "附近有什么好吃的?",
                "去机场走哪条路最快?",
                "危化品车辆禁行区域有哪些?"
            ).forEach { suggestion ->
                SuggestionChip(text = suggestion)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ==================== 建议问题标签 ====================
@Composable
private fun SuggestionChip(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BackgroundSecondary
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = WarningYellow,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                color = TextPrimary
            )
        }
    }
}

// ==================== 用户消息气泡 ====================
@Composable
private fun UserMessageBubble(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            colors = CardDefaults.cardColors(containerColor = BrandBlue)
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(12.dp),
                color = Color.White,
                fontSize = 15.sp
            )
        }
    }
}

// ==================== AI消息气泡 ====================
@Composable
private fun AiMessageBubble(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(AIGradient, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(12.dp),
                color = TextPrimary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

// ==================== AI输入中指示器 ====================
@Composable
private fun AiTypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(AIGradient, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Card(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier.padding(16.dp, 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(TextTertiary, CircleShape)
                    )
                }
            }
        }
    }
}

// ==================== 个人中心页面 ====================
@Composable
fun UserProfileScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val userInfo by viewModel?.userInfo?.collectAsState() ?: remember { mutableStateOf(null) }
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
    ) {
        // 头部个人信息
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor, primaryColor.copy(alpha = 0.8f))
                    )
                )
        ) {
            // 装饰
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .offset(x = (-30).dp, y = (-30).dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 头像
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White, CircleShape)
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackgroundSecondary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = userInfo?.userName ?: viewModel?.getUserName() ?: "用户",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isProfessional) Icons.Rounded.LocalShipping 
                                     else Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isProfessional) "货运司机" else "私家车主",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
        
        // 功能列表
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 账户设置组
            Text(
                text = "账户设置",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            
            ProfileMenuItem(
                icon = Icons.Rounded.Person,
                title = "个人资料",
                subtitle = "修改头像、昵称",
                onClick = { navController.navigate("edit_profile") }
            )
            
            ProfileMenuItem(
                icon = Icons.Rounded.Security,
                title = "账号安全",
                subtitle = "密码、手机号",
                onClick = { navController.navigate("account_security") }
            )
            
            ProfileMenuItem(
                icon = Icons.Rounded.Notifications,
                title = "消息通知",
                subtitle = "推送设置",
                onClick = { navController.navigate("notification_settings") }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 其他设置组
            Text(
                text = "其他",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            
            ProfileMenuItem(
                icon = Icons.Rounded.Help,
                title = "帮助中心",
                subtitle = "常见问题、使用指南",
                onClick = { navController.navigate("help_center") }
            )
            
            ProfileMenuItem(
                icon = Icons.Rounded.Info,
                title = "关于我们",
                subtitle = "版本 1.0.0",
                onClick = { navController.navigate("about") }
            )
            
            ProfileMenuItem(
                icon = Icons.Rounded.Settings,
                title = "系统设置",
                subtitle = "通用设置",
                onClick = { navController.navigate("settings") }
            )
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ==================== 个人中心菜单项 ====================
@Composable
private fun ProfileMenuItem(
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
                    .size(44.dp)
                    .background(BackgroundSecondary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
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
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary
            )
        }
    }
}

// ==================== 设置页面 ====================
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen
    
    var darkMode by remember { mutableStateOf(false) }
    var autoUpdate by remember { mutableStateOf(true) }
    var locationService by remember { mutableStateOf(true) }
    
    // 缓存大小状态
    var cacheSize by remember { mutableStateOf("128MB") }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showSwitchAccountDialog by remember { mutableStateOf(false) }
    
    DetailScreenTemplate(
        navController = navController,
        title = "系统设置",
        backgroundColor = BackgroundPrimary
    ) {
        // 通用设置
        Text(
            text = "通用设置",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        SettingsCard {
            SettingsToggleItem(
                icon = Icons.Rounded.DarkMode,
                title = "深色模式",
                subtitle = "夜间使用更护眼",
                checked = darkMode,
                onCheckedChange = { 
                    darkMode = it
                    Toast.makeText(context, if (it) "深色模式已开启" else "深色模式已关闭", Toast.LENGTH_SHORT).show()
                }
            )
            
            HorizontalDivider(color = DividerColor)
            
            SettingsToggleItem(
                icon = Icons.Rounded.Update,
                title = "自动更新",
                subtitle = "WiFi下自动更新应用",
                checked = autoUpdate,
                onCheckedChange = { autoUpdate = it }
            )
            
            HorizontalDivider(color = DividerColor)
            
            SettingsToggleItem(
                icon = Icons.Rounded.LocationOn,
                title = "位置服务",
                subtitle = "允许获取位置信息",
                checked = locationService,
                onCheckedChange = { 
                    locationService = it
                    if (!it) {
                        Toast.makeText(context, "关闭位置服务将影响导航功能", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 数据管理
        Text(
            text = "数据管理",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        SettingsCard {
            SettingsClickItem(
                icon = Icons.Rounded.Cached,
                title = "清除缓存",
                subtitle = "当前缓存 $cacheSize",
                onClick = { showClearCacheDialog = true }
            )
            
            HorizontalDivider(color = DividerColor)
            
            SettingsClickItem(
                icon = Icons.Rounded.Download,
                title = "离线地图",
                subtitle = "管理已下载的地图",
                onClick = { navController.navigate("offline_map") }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 账户操作
        Text(
            text = "账户操作",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        SettingsCard {
            SettingsClickItem(
                icon = Icons.Rounded.SwapHoriz,
                title = "切换账号",
                subtitle = "登录其他账号",
                onClick = { showSwitchAccountDialog = true }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 退出登录按钮
        Button(
            onClick = {
                viewModel?.logout()
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ErrorRedLight,
                contentColor = ErrorRed
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Logout,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "退出登录",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 版本信息
        Text(
            text = "HubLink Navigator v1.0.0",
            fontSize = 12.sp,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    
    // 清除缓存对话框
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = { Icon(Icons.Rounded.Cached, contentDescription = null, tint = primaryColor) },
            title = { Text("清除缓存") },
            text = { Text("确定要清除应用缓存吗？这将清除临时文件和图片缓存，不会影响您的账号数据。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 清除缓存逻辑
                        try {
                            context.cacheDir.deleteRecursively()
                            cacheSize = "0MB"
                            Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "清除失败，请重试", Toast.LENGTH_SHORT).show()
                        }
                        showClearCacheDialog = false
                    }
                ) {
                    Text("确定", color = primaryColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 切换账号对话框
    if (showSwitchAccountDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchAccountDialog = false },
            icon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = primaryColor) },
            title = { Text("切换账号") },
            text = { Text("切换账号将退出当前账号，确定要继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSwitchAccountDialog = false
                        viewModel?.logout()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                ) {
                    Text("确定切换", color = primaryColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchAccountDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ==================== 设置卡片容器 ====================
@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(content = content)
    }
}

// ==================== 设置开关项 ====================
@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(24.dp)
        )
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
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BrandBlue,
                checkedTrackColor = BrandBlueLight
            )
        )
    }
}

// ==================== 设置点击项 ====================
@Composable
private fun SettingsClickItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(24.dp)
        )
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
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextTertiary
        )
    }
}
