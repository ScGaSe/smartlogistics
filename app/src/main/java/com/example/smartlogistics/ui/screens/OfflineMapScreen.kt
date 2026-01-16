package com.example.smartlogistics.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.MainViewModel
import com.amap.api.maps.offlinemap.OfflineMapCity
import com.amap.api.maps.offlinemap.OfflineMapManager
import com.amap.api.maps.offlinemap.OfflineMapProvince
import com.amap.api.maps.offlinemap.OfflineMapStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==================== 离线地图管理页面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen
    
    // 选项卡状态
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("已下载", "热门城市", "全部城市")
    
    // 数据状态
    var downloadedCities by remember { mutableStateOf<List<DownloadedCityInfo>>(emptyList()) }
    var hotCities by remember { mutableStateOf<List<OfflineCityItem>>(emptyList()) }
    var allProvinces by remember { mutableStateOf<List<OfflineProvinceItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var downloadingCities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    
    // 展开的省份
    var expandedProvince by remember { mutableStateOf<String?>(null) }
    
    // 删除确认对话框
    var showDeleteDialog by remember { mutableStateOf(false) }
    var cityToDelete by remember { mutableStateOf<String?>(null) }
    
    // 离线地图管理器
    var offlineManager by remember { mutableStateOf<OfflineMapManager?>(null) }
    
    // 刷新已下载列表的函数
    fun refreshDownloadedList() {
        try {
            offlineManager?.let { manager ->
                val list = manager.downloadOfflineMapCityList
                if (list != null) {
                    downloadedCities = list.map { city ->
                        DownloadedCityInfo(
                            name = city.city,
                            size = formatFileSize(city.size),
                            state = city.state
                        )
                    }
                    android.util.Log.d("OfflineMap", "刷新已下载列表: ${downloadedCities.size} 个城市")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OfflineMap", "刷新列表失败: ${e.message}")
        }
    }
    
    // 初始化
    LaunchedEffect(Unit) {
        try {
            // 创建离线地图管理器 - 使用简化的监听器避免网络依赖
            val manager = OfflineMapManager(context, object : OfflineMapManager.OfflineMapDownloadListener {
                override fun onDownload(status: Int, completeCode: Int, downName: String?) {
                    android.util.Log.d("OfflineMap", "下载回调: status=$status, progress=$completeCode, city=$downName")
                    
                    when (status) {
                        OfflineMapStatus.SUCCESS -> {
                            downloadingCities = downloadingCities - (downName ?: "")
                            scope.launch {
                                delay(500)
                                refreshDownloadedList()
                            }
                            Toast.makeText(context, "${downName}下载完成", Toast.LENGTH_SHORT).show()
                        }
                        OfflineMapStatus.LOADING -> {
                            downloadingCities = downloadingCities + ((downName ?: "") to completeCode)
                        }
                        OfflineMapStatus.UNZIP -> {
                            downloadingCities = downloadingCities + ((downName ?: "") to 99)
                        }
                        OfflineMapStatus.WAITING -> { }
                        OfflineMapStatus.PAUSE -> {
                            Toast.makeText(context, "${downName}已暂停", Toast.LENGTH_SHORT).show()
                        }
                        OfflineMapStatus.STOP -> {
                            downloadingCities = downloadingCities - (downName ?: "")
                        }
                        OfflineMapStatus.ERROR -> {
                            downloadingCities = downloadingCities - (downName ?: "")
                            Toast.makeText(context, "${downName}下载失败", Toast.LENGTH_SHORT).show()
                        }
                        OfflineMapStatus.EXCEPTION_AMAP, 
                        OfflineMapStatus.EXCEPTION_NETWORK_LOADING,
                        OfflineMapStatus.EXCEPTION_SDCARD -> {
                            downloadingCities = downloadingCities - (downName ?: "")
                            Toast.makeText(context, "下载异常: $status", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                
                override fun onCheckUpdate(hasNew: Boolean, name: String?) {
                    if (hasNew) {
                        Toast.makeText(context, "${name}有更新可用", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onRemove(success: Boolean, name: String?, describe: String?) {
                    if (success) {
                        Toast.makeText(context, "${name}已删除", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(300)
                            refreshDownloadedList()
                        }
                    } else {
                        Toast.makeText(context, "删除失败: $describe", Toast.LENGTH_SHORT).show()
                    }
                }
            })
            
            offlineManager = manager
            
            // 加载数据 - 分开处理已下载列表和在线列表
            loadOfflineMapData(manager, context) { downloaded, hot, provinces ->
                downloadedCities = downloaded
                hotCities = hot
                allProvinces = provinces
                isLoading = false
                android.util.Log.d("OfflineMap", "数据加载完成: 已下载=${downloaded.size}, 热门=${hot.size}, 省份=${provinces.size}")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("OfflineMap", "初始化失败: ${e.message}")
            e.printStackTrace()
            
            // 即使Manager初始化失败，也尝试从本地读取已下载的地图
            try {
                val fallbackManager = OfflineMapManager(context, null)
                val downloadedList = fallbackManager.downloadOfflineMapCityList
                if (downloadedList != null && downloadedList.isNotEmpty()) {
                    downloadedCities = downloadedList.map { 
                        DownloadedCityInfo(it.city, formatFileSize(it.size), it.state) 
                    }
                    android.util.Log.d("OfflineMap", "备用方式读取到 ${downloadedCities.size} 个已下载城市")
                }
            } catch (e2: Exception) {
                android.util.Log.e("OfflineMap", "备用读取也失败: ${e2.message}")
            }
            
            isLoading = false
            hotCities = getMockHotCities()
            allProvinces = getMockProvinces()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("离线地图", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        refreshDownloadedList()
                        Toast.makeText(context, "已刷新", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundPrimary)
                .padding(paddingValues)
        ) {
            // 选项卡
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = primaryColor
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { 
                            selectedTab = index
                            if (index == 0) refreshDownloadedList()
                        },
                        text = {
                            Text(
                                text = if (index == 0 && downloadedCities.isNotEmpty()) 
                                    "$title(${downloadedCities.size})" 
                                else 
                                    title,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedTab == index) primaryColor else TextSecondary
                            )
                        }
                    )
                }
            }
            
            // 内容区域
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = primaryColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在加载...", color = TextSecondary)
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> DownloadedTab(
                        cities = downloadedCities,
                        downloadingCities = downloadingCities,
                        primaryColor = primaryColor,
                        onDelete = { cityName ->
                            cityToDelete = cityName
                            showDeleteDialog = true
                        },
                        onPause = { offlineManager?.pause() }
                    )
                    1 -> HotCitiesTab(
                        cities = hotCities,
                        downloadingCities = downloadingCities,
                        downloadedCities = downloadedCities.map { it.name },
                        primaryColor = primaryColor,
                        onDownload = { city ->
                            // 使用城市代码下载，确保下载完整数据
                            if (city.code.isNotEmpty()) {
                                offlineManager?.downloadByCityCode(city.code)
                            } else {
                                offlineManager?.downloadByCityName(city.name)
                            }
                            downloadingCities = downloadingCities + (city.name to 0)
                            Toast.makeText(context, "开始下载 ${city.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                    2 -> AllCitiesTab(
                        provinces = allProvinces,
                        expandedProvince = expandedProvince,
                        downloadingCities = downloadingCities,
                        downloadedCities = downloadedCities.map { it.name },
                        primaryColor = primaryColor,
                        onProvinceClick = { provinceName ->
                            expandedProvince = if (expandedProvince == provinceName) null else provinceName
                        },
                        onDownload = { city ->
                            // 使用城市代码下载，确保下载完整数据
                            if (city.code.isNotEmpty()) {
                                offlineManager?.downloadByCityCode(city.code)
                            } else {
                                offlineManager?.downloadByCityName(city.name)
                            }
                            downloadingCities = downloadingCities + (city.name to 0)
                            Toast.makeText(context, "开始下载 ${city.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog && cityToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = ErrorRed) },
            title = { Text("删除离线地图") },
            text = { Text("确定要删除「${cityToDelete}」的离线地图吗？") },
            confirmButton = {
                TextButton(onClick = {
                    offlineManager?.remove(cityToDelete)
                    showDeleteDialog = false
                    cityToDelete = null
                }) { Text("删除", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

// ==================== 已下载选项卡 ====================
@Composable
private fun DownloadedTab(
    cities: List<DownloadedCityInfo>,
    downloadingCities: Map<String, Int>,
    primaryColor: Color,
    onDelete: (String) -> Unit,
    onPause: () -> Unit
) {
    if (cities.isEmpty() && downloadingCities.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CloudDownload, null, Modifier.size(64.dp), tint = TextTertiary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("暂无离线地图", fontSize = 16.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("下载离线地图后可在无网络时使用", fontSize = 14.sp, color = TextTertiary)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            downloadingCities.forEach { (cityName, progress) ->
                item(key = "downloading_$cityName") {
                    DownloadingCityCard(cityName, progress, primaryColor, onPause)
                }
            }
            items(cities, key = { "downloaded_${it.name}" }) { city ->
                DownloadedCityCard(city, primaryColor) { onDelete(city.name) }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = InfoBlueLight)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, tint = InfoBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("离线地图可在无网络时查看地图和导航，实时路况需要联网", fontSize = 13.sp, color = InfoBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadingCityCard(cityName: String, progress: Int, primaryColor: Color, onPause: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Map, null, tint = primaryColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(cityName, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Text(if (progress >= 99) "解压中..." else "下载中 $progress%", fontSize = 13.sp, color = primaryColor)
                    }
                }
                IconButton(onClick = onPause) { Icon(Icons.Rounded.Pause, "暂停", tint = TextSecondary) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = primaryColor, trackColor = primaryColor.copy(alpha = 0.1f))
        }
    }
}

@Composable
private fun DownloadedCityCard(city: DownloadedCityInfo, primaryColor: Color, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(SuccessGreen.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(city.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text(city.size, fontSize = 13.sp, color = TextSecondary)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "删除", tint = ErrorRed.copy(alpha = 0.7f)) }
        }
    }
}

// ==================== 热门城市选项卡 ====================
@Composable
private fun HotCitiesTab(cities: List<OfflineCityItem>, downloadingCities: Map<String, Int>, downloadedCities: List<String>, primaryColor: Color, onDownload: (OfflineCityItem) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cities, key = { it.name }) { city ->
            CityDownloadCard(city, downloadedCities.contains(city.name), downloadingCities.containsKey(city.name), downloadingCities[city.name] ?: 0, primaryColor) { onDownload(city) }
        }
    }
}

// ==================== 全部城市选项卡 ====================
@Composable
private fun AllCitiesTab(provinces: List<OfflineProvinceItem>, expandedProvince: String?, downloadingCities: Map<String, Int>, downloadedCities: List<String>, primaryColor: Color, onProvinceClick: (String) -> Unit, onDownload: (OfflineCityItem) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        provinces.forEach { province ->
            item(key = "province_${province.name}") {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onProvinceClick(province.name) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (expandedProvince == province.name) primaryColor.copy(alpha = 0.05f) else Color.White)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocationCity, null, tint = if (expandedProvince == province.name) primaryColor else TextSecondary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(province.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${province.cities.size}个城市", fontSize = 13.sp, color = TextTertiary)
                        }
                        Icon(if (expandedProvince == province.name) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = TextTertiary)
                    }
                }
            }
            if (expandedProvince == province.name) {
                items(province.cities, key = { "${province.name}_${it.name}" }) { city ->
                    CityDownloadCard(city, downloadedCities.contains(city.name), downloadingCities.containsKey(city.name), downloadingCities[city.name] ?: 0, primaryColor, Modifier.padding(start = 16.dp)) { onDownload(city) }
                }
            }
        }
    }
}

@Composable
private fun CityDownloadCard(city: OfflineCityItem, isDownloaded: Boolean, isDownloading: Boolean, progress: Int, primaryColor: Color, modifier: Modifier = Modifier, onDownload: () -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(city.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(city.size, fontSize = 13.sp, color = TextTertiary)
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = primaryColor, trackColor = primaryColor.copy(alpha = 0.1f))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            when {
                isDownloaded -> Box(modifier = Modifier.background(SuccessGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("已下载", fontSize = 13.sp, color = SuccessGreen, fontWeight = FontWeight.Medium) }
                isDownloading -> Text("$progress%", fontSize = 14.sp, color = primaryColor, fontWeight = FontWeight.SemiBold)
                else -> IconButton(onClick = onDownload, modifier = Modifier.size(36.dp).background(primaryColor.copy(alpha = 0.1f), CircleShape)) { Icon(Icons.Rounded.Download, "下载", tint = primaryColor, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

// ==================== 数据类 ====================
data class DownloadedCityInfo(val name: String, val size: String, val state: Int = OfflineMapStatus.SUCCESS)
data class OfflineCityItem(val name: String, val size: String, val code: String = "")
data class OfflineProvinceItem(val name: String, val cities: List<OfflineCityItem>)

// ==================== 辅助函数 ====================
private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun loadOfflineMapData(manager: OfflineMapManager?, context: Context, onResult: (List<DownloadedCityInfo>, List<OfflineCityItem>, List<OfflineProvinceItem>) -> Unit) {
    // 先读取已下载列表（这个应该在离线时也能工作）
    var downloaded: List<DownloadedCityInfo> = emptyList()
    try {
        val downloadedList = manager?.downloadOfflineMapCityList
        if (downloadedList != null) {
            downloaded = downloadedList.map { DownloadedCityInfo(it.city, formatFileSize(it.size), it.state) }
            android.util.Log.d("OfflineMap", "读取已下载城市: ${downloaded.size}")
        }
    } catch (e: Exception) {
        android.util.Log.e("OfflineMap", "读取已下载列表失败: ${e.message}")
    }
    
    // 尝试读取省份列表（需要网络）
    var hotCities: List<OfflineCityItem> = emptyList()
    var provinces: List<OfflineProvinceItem> = emptyList()
    
    try {
        val provinceList = manager?.offlineMapProvinceList
        if (provinceList != null && provinceList.isNotEmpty()) {
            val hotCityNames = listOf("北京市", "上海市", "广州市", "深圳市", "杭州市", "成都市", "武汉市", "南京市", "西安市", "重庆市", "长沙市", "苏州市")
            val tempHotCities = mutableListOf<OfflineCityItem>()
            val tempProvinces = mutableListOf<OfflineProvinceItem>()
            
            provinceList.forEach { province ->
                val cities = province.cityList?.map { OfflineCityItem(it.city, formatFileSize(it.size), it.code ?: "") } ?: emptyList()
                cities.forEach { if (hotCityNames.contains(it.name)) tempHotCities.add(it) }
                if (cities.isNotEmpty()) tempProvinces.add(OfflineProvinceItem(province.provinceName ?: "未知", cities))
            }
            
            hotCities = hotCityNames.mapNotNull { name -> tempHotCities.find { it.name == name } }
            provinces = tempProvinces
            android.util.Log.d("OfflineMap", "读取省份列表成功: ${provinces.size}个省份")
        }
    } catch (e: Exception) {
        android.util.Log.e("OfflineMap", "读取省份列表失败（可能无网络）: ${e.message}")
    }
    
    // 如果无法获取在线列表，使用模拟数据
    if (hotCities.isEmpty()) hotCities = getMockHotCities()
    if (provinces.isEmpty()) provinces = getMockProvinces()
    
    onResult(downloaded, hotCities, provinces)
}

private fun getMockHotCities() = listOf(
    OfflineCityItem("北京市", "385 MB", "110000"),
    OfflineCityItem("上海市", "298 MB", "310000"),
    OfflineCityItem("广州市", "276 MB", "440100"),
    OfflineCityItem("深圳市", "198 MB", "440300"),
    OfflineCityItem("杭州市", "245 MB", "330100"),
    OfflineCityItem("成都市", "312 MB", "510100"),
    OfflineCityItem("武汉市", "267 MB", "420100"),
    OfflineCityItem("南京市", "234 MB", "320100"),
    OfflineCityItem("西安市", "289 MB", "610100"),
    OfflineCityItem("重庆市", "356 MB", "500000"),
    OfflineCityItem("长沙市", "215 MB", "430100"),
    OfflineCityItem("苏州市", "187 MB", "320500")
)

private fun getMockProvinces() = listOf(
    OfflineProvinceItem("湖南省", listOf(
        OfflineCityItem("长沙市", "215 MB", "430100"),
        OfflineCityItem("株洲市", "98 MB", "430200"),
        OfflineCityItem("湘潭市", "87 MB", "430300")
    )),
    OfflineProvinceItem("广东省", listOf(
        OfflineCityItem("广州市", "276 MB", "440100"),
        OfflineCityItem("深圳市", "198 MB", "440300"),
        OfflineCityItem("东莞市", "156 MB", "441900")
    )),
    OfflineProvinceItem("浙江省", listOf(
        OfflineCityItem("杭州市", "245 MB", "330100"),
        OfflineCityItem("宁波市", "178 MB", "330200"),
        OfflineCityItem("温州市", "165 MB", "330300")
    )),
    OfflineProvinceItem("江苏省", listOf(
        OfflineCityItem("南京市", "234 MB", "320100"),
        OfflineCityItem("苏州市", "187 MB", "320500"),
        OfflineCityItem("无锡市", "145 MB", "320200")
    )),
    OfflineProvinceItem("四川省", listOf(
        OfflineCityItem("成都市", "312 MB", "510100"),
        OfflineCityItem("绵阳市", "123 MB", "510700")
    )),
    OfflineProvinceItem("湖北省", listOf(
        OfflineCityItem("武汉市", "267 MB", "420100"),
        OfflineCityItem("宜昌市", "134 MB", "420500")
    )),
    OfflineProvinceItem("北京市", listOf(OfflineCityItem("北京市", "385 MB", "110000"))),
    OfflineProvinceItem("上海市", listOf(OfflineCityItem("上海市", "298 MB", "310000"))),
    OfflineProvinceItem("重庆市", listOf(OfflineCityItem("重庆市", "356 MB", "500000")))
)
