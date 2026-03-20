package com.example.smartlogistics.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.MainViewModel
import com.example.smartlogistics.utils.SettingsManager
import java.io.File

// ==================== 个人资料编辑页面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen

    // 获取设置管理器
    val settingsManager = remember { SettingsManager.getInstance(context) }

    // 获取真实用户信息
    val userInfo by viewModel?.userInfo?.collectAsState() ?: remember { mutableStateOf(null) }

    // 真实手机号（脱敏显示）
    val realPhone = userInfo?.phoneNumber ?: viewModel?.getUserName() ?: ""
    val maskedPhone = if (realPhone.length >= 11) {
        "${realPhone.substring(0, 3)}****${realPhone.substring(7)}"
    } else {
        realPhone
    }

    // 用户信息状态 - 从本地读取昵称，默认使用手机号
    var nickname by remember {
        mutableStateOf(settingsManager.getNickname() ?: realPhone)
    }
    var isEditing by remember { mutableStateOf(false) }

    // 头像状态
    var avatarBitmap by remember { mutableStateOf(settingsManager.getAvatarBitmap()) }
    var avatarUpdateTrigger by remember { mutableStateOf(0) }

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (settingsManager.saveAvatar(it)) {
                avatarBitmap = settingsManager.getAvatarBitmap()
                avatarUpdateTrigger++
                Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "头像保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 更新昵称（当 realPhone 变化时）
    LaunchedEffect(realPhone) {
        if (settingsManager.getNickname() == null && realPhone.isNotEmpty()) {
            nickname = realPhone
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人资料", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (isEditing) {
                                // 保存昵称到本地
                                settingsManager.saveNickname(nickname)
                                Toast.makeText(context, "资料已保存", Toast.LENGTH_SHORT).show()
                            }
                            isEditing = !isEditing
                        }
                    ) {
                        Text(
                            text = if (isEditing) "保存" else "编辑",
                            color = primaryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundPrimary)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 头像区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.1f))
                            .clickable(enabled = isEditing) {
                                imagePickerLauncher.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // 显示头像或默认图标
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap!!.asImageBitmap(),
                                contentDescription = "头像",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(50.dp),
                                tint = primaryColor
                            )
                        }

                        if (isEditing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = "更换头像",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    if (isEditing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点击更换头像",
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 信息编辑区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 昵称
                    ProfileEditItem(
                        label = "昵称",
                        value = nickname,
                        onValueChange = { nickname = it },
                        isEditing = isEditing,
                        primaryColor = primaryColor
                    )

                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 12.dp))

                    // 手机号（显示真实手机号，脱敏，不可编辑）
                    ProfileDisplayItem(
                        label = "手机号",
                        value = maskedPhone
                    )

                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 12.dp))

                    // 用户角色
                    ProfileDisplayItem(
                        label = "用户类型",
                        value = if (isProfessional) "货运司机" else "私家车主"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 提示信息
            Text(
                text = "修改昵称后，将在所有设备上同步显示",
                fontSize = 12.sp,
                color = TextTertiary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

// ==================== 账号安全页面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号安全", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
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
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    // 修改密码 - 跳转到独立页面
                    SecurityMenuItem(
                        icon = Icons.Rounded.Lock,
                        title = "修改密码",
                        subtitle = "定期修改密码更安全",
                        onClick = { navController.navigate("change_password") }
                    )

                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    // 指纹登录
                    SecurityToggleItem(
                        icon = Icons.Rounded.Fingerprint,
                        title = "指纹登录",
                        subtitle = "使用指纹快速登录",
                        primaryColor = primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 账号注销
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                SecurityMenuItem(
                    icon = Icons.Rounded.DeleteForever,
                    title = "注销账号",
                    subtitle = "永久删除账号及所有数据",
                    iconTint = ErrorRed,
                    onClick = {
                        Toast.makeText(context, "请联系客服注销账号", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

// ==================== 消息通知设置页面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen

    var pushEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var trafficAlert by remember { mutableStateOf(true) }
    var tripReminder by remember { mutableStateOf(true) }
    var systemNotice by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消息通知", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 通知方式
            Text(
                text = "通知方式",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    NotificationToggleItem(
                        icon = Icons.Rounded.Notifications,
                        title = "推送通知",
                        subtitle = "接收应用推送消息",
                        checked = pushEnabled,
                        onCheckedChange = { pushEnabled = it },
                        primaryColor = primaryColor
                    )

                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    NotificationToggleItem(
                        icon = Icons.Rounded.VolumeUp,
                        title = "声音",
                        subtitle = "收到通知时播放提示音",
                        checked = soundEnabled,
                        onCheckedChange = { soundEnabled = it },
                        enabled = pushEnabled,
                        primaryColor = primaryColor
                    )

                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    NotificationToggleItem(
                        icon = Icons.Rounded.Vibration,
                        title = "振动",
                        subtitle = "收到通知时振动提醒",
                        checked = vibrationEnabled,
                        onCheckedChange = { vibrationEnabled = it },
                        enabled = pushEnabled,
                        primaryColor = primaryColor
                    )
                }
            }

            // 通知类型
            Text(
                text = "通知类型",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    NotificationToggleItem(
                        icon = Icons.Rounded.Traffic,
                        title = "路况提醒",
                        subtitle = "拥堵、事故等路况变化提醒",
                        checked = trafficAlert,
                        onCheckedChange = { trafficAlert = it },
                        enabled = pushEnabled,
                        primaryColor = primaryColor
                    )

                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    NotificationToggleItem(
                        icon = Icons.Rounded.Schedule,
                        title = "行程提醒",
                        subtitle = if (isProfessional) "报备到期、装卸提醒" else "航班动态、出行提醒",
                        checked = tripReminder,
                        onCheckedChange = { tripReminder = it },
                        enabled = pushEnabled,
                        primaryColor = primaryColor
                    )

                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    NotificationToggleItem(
                        icon = Icons.Rounded.Campaign,
                        title = "系统公告",
                        subtitle = "版本更新、活动通知等",
                        checked = systemNotice,
                        onCheckedChange = { systemNotice = it },
                        enabled = pushEnabled,
                        primaryColor = primaryColor
                    )
                }
            }
        }
    }
}

// ==================== 帮助中心页面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpCenterScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen

    val faqList = remember {
        if (isProfessional) {
            listOf(
                FAQ("如何绑定货车？", "进入主页 > 点击「车辆绑定」> 拍摄车牌照片 > 填写车辆信息 > 提交绑定"),
                FAQ("如何进行货物报备？", "进入主页 > 点击「货物报备」> 填写货物信息或使用语音输入 > 提交报备"),
                FAQ("如何规划路线？", "进入导航页面 > 输入目的地 > 系统会自动规划符合货车限制的路线"),
                FAQ("如何查看拥堵预测？", "进入主页 > 点击「拥堵预测」> 可查看未来3小时内的路况趋势"),
                FAQ("危化品车辆有什么限制？", "危化品车辆必须从指定闸口进入，系统会自动规划专用路线")
            )
        } else {
            listOf(
                FAQ("如何绑定车辆？", "进入主页 > 点击「车辆绑定」> 拍摄车牌照片 > 确认信息后提交"),
                FAQ("如何关联航班/火车？", "进入「我的行程」> 点击添加 > 输入航班号或车次 > 系统自动获取行程信息"),
                FAQ("如何使用寻车功能？", "停车时先标记位置或拍照 > 需要找车时点击「导航找车」"),
                FAQ("如何查看停车场空位？", "进入导航页面 > 点击停车场图标 > 可查看实时空位数量"),
                FAQ("如何分享实时位置？", "在行程详情中 > 点击「位置共享」> 生成分享链接发送给接机人")
            )
        }
    }

    var expandedIndex by remember { mutableStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("帮助中心", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "常见问题",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            faqList.forEachIndexed { index, faq ->
                FAQItem(
                    question = faq.question,
                    answer = faq.answer,
                    isExpanded = expandedIndex == index,
                    onClick = {
                        expandedIndex = if (expandedIndex == index) -1 else index
                    },
                    primaryColor = primaryColor
                )
            }
        }
    }
}

// ==================== 关于我们页面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen
    val settingsManager = remember { SettingsManager.getInstance(context) }
    var mockLocationEnabled by remember { mutableStateOf(settingsManager.mockLocationEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于我们", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
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
                .verticalScroll(rememberScrollState())
        ) {
            // Logo和版本信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = if (isProfessional)
                                    listOf(TruckOrange, TruckYellow)
                                else
                                    listOf(CarGreen, CarTeal)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "HubLink Navigator",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "版本 1.0.0",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 功能列表
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    AboutMenuItem(
                        icon = Icons.Rounded.NewReleases,
                        title = "检查更新",
                        onClick = {
                            Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    AboutMenuItem(
                        icon = Icons.Rounded.Description,
                        title = "用户协议",
                        onClick = { navController.navigate("user_agreement") }
                    )

                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    AboutMenuItem(
                        icon = Icons.Rounded.PrivacyTip,
                        title = "隐私政策",
                        onClick = { navController.navigate("privacy_policy") }
                    )

                    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                    AboutMenuItem(
                        icon = Icons.Rounded.Star,
                        title = "给我们评分",
                        onClick = {
                            Toast.makeText(context, "感谢您的支持！", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ⭐ 演示模式卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                            .background(
                                if (mockLocationEnabled) primaryColor.copy(alpha = 0.12f)
                                else BackgroundSecondary,
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✈", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "演示模式（大兴机场）",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (mockLocationEnabled) "当前位置已模拟为大兴机场" else "开启后定位将切换到大兴机场",
                            fontSize = 12.sp,
                            color = if (mockLocationEnabled) primaryColor else TextSecondary
                        )
                    }
                    Switch(
                        checked = mockLocationEnabled,
                        onCheckedChange = {
                            mockLocationEnabled = it
                            settingsManager.mockLocationEnabled = it
                            Toast.makeText(
                                context,
                                if (it) "演示模式已开启，重新进入地图页生效" else "演示模式已关闭",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = primaryColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 版权信息
            Text(
                text = "© 2026 HubLink Navigator\n智慧枢纽导航系统",
                fontSize = 12.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ==================== 用户协议页面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAgreementScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户协议", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = """
                    HubLink Navigator 用户服务协议
                    
                    更新日期：2025年1月1日
                    生效日期：2025年1月1日
                    
                    欢迎您使用 HubLink Navigator（以下简称"本应用"）！
                    
                    一、服务内容
                    
                    本应用为用户提供智慧枢纽导航服务，包括但不限于：
                    1. 实时路况查询与导航
                    2. 车辆绑定与管理
                    3. 货物报备（专业模式）
                    4. 行程管理（个人模式）
                    5. 智能AI问答服务
                    
                    二、用户责任
                    
                    1. 用户应提供真实、准确的个人信息
                    2. 用户应遵守相关交通法规
                    3. 用户不得利用本应用从事违法活动
                    
                    三、隐私保护
                    
                    我们重视您的隐私保护，详见《隐私政策》
                    
                    四、免责声明
                    
                    1. 导航信息仅供参考，请以实际路况为准
                    2. 因不可抗力导致的服务中断，本应用不承担责任
                    
                    五、协议修改
                    
                    本应用有权根据需要修改本协议，修改后的协议将在应用内公布
                    
                    如您继续使用本应用，即表示您同意本协议的全部内容。
                """.trimIndent(),
                fontSize = 14.sp,
                color = TextPrimary,
                lineHeight = 24.sp
            )
        }
    }
}

// ==================== 隐私政策页面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隐私政策", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = """
                    HubLink Navigator 隐私政策
                    
                    更新日期：2025年1月1日
                    生效日期：2025年1月1日
                    
                    我们深知个人信息对您的重要性，并会尽全力保护您的个人信息安全。
                    
                    一、我们收集的信息
                    
                    1. 账号信息：手机号码、用户昵称
                    2. 车辆信息：车牌号、车型（仅绑定时）
                    3. 位置信息：用于导航和路况服务
                    4. 设备信息：用于安全验证
                    
                    二、信息使用目的
                    
                    1. 提供导航和路况服务
                    2. 车辆管理和报备服务
                    3. 个性化推荐和服务优化
                    4. 安全验证和风险控制
                    
                    三、信息存储
                    
                    1. 我们在中国境内存储您的信息
                    2. 我们采用加密技术保护数据安全
                    3. 账号注销后，我们将删除相关信息
                    
                    四、信息共享
                    
                    除以下情况外，我们不会共享您的信息：
                    1. 获得您的明确同意
                    2. 法律法规要求
                    3. 保护公共利益
                    
                    五、您的权利
                    
                    1. 查询、更正您的个人信息
                    2. 删除您的账号和数据
                    3. 撤回授权同意
                    
                    六、联系我们
                    
                    如有疑问，请联系：privacy@hublink.com
                """.trimIndent(),
                fontSize = 14.sp,
                color = TextPrimary,
                lineHeight = 24.sp
            )
        }
    }
}

// ==================== 辅助组件 ====================

@Composable
private fun ProfileEditItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isEditing: Boolean,
    primaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = TextSecondary
        )

        if (isEditing) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(200.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    cursorColor = primaryColor
                )
            )
        } else {
            Text(
                text = value,
                fontSize = 16.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProfileDisplayItem(
    label: String,
    value: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = TextSecondary
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontSize = 16.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun SecurityMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = TextSecondary,
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
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondary
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextTertiary
        )
    }
}

@Composable
private fun SecurityToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primaryColor: Color
) {
    var checked by remember { mutableStateOf(true) }

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

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = primaryColor
            )
        )
    }
}

@Composable
private fun NotificationToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    primaryColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
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
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = primaryColor
            )
        )
    }
}

@Composable
private fun Modifier.alpha(alpha: Float): Modifier = this.graphicsLayer(alpha = alpha)

private data class FAQ(val question: String, val answer: String)

@Composable
private fun FAQItem(
    question: String,
    answer: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    primaryColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QuestionMark,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier
                            .size(24.dp)
                            .background(primaryColor.copy(alpha = 0.1f), CircleShape)
                            .padding(4.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = question,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = TextTertiary
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DividerColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = answer,
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutMenuItem(
    icon: ImageVector,
    title: String,
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

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            fontSize = 16.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextTertiary
        )
    }
}

// ==================== 修改密码页面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val isProfessional = viewModel?.isProfessionalMode() ?: false
    val primaryColor = if (isProfessional) TruckOrange else CarGreen

    // 获取真实手机号
    val userInfo by viewModel?.userInfo?.collectAsState() ?: remember { mutableStateOf(null) }
    val phoneNumber = userInfo?.phoneNumber ?: viewModel?.getUserName() ?: ""

    // 脱敏手机号
    val maskedPhone = if (phoneNumber.length >= 11) {
        "${phoneNumber.substring(0, 3)}****${phoneNumber.substring(7)}"
    } else {
        phoneNumber
    }

    // 表单状态
    var verifyCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(0) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // 监听认证状态
    val authState by viewModel?.authState?.collectAsState() ?: remember { mutableStateOf(null) }
    val isLoading = authState is com.example.smartlogistics.viewmodel.AuthState.Loading

    // 倒计时
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }

    // 处理状态变化
    LaunchedEffect(authState) {
        when (authState) {
            is com.example.smartlogistics.viewmodel.AuthState.CodeSent -> {
                countdown = 60
                Toast.makeText(context, "验证码已发送", Toast.LENGTH_SHORT).show()
            }
            is com.example.smartlogistics.viewmodel.AuthState.ResetPasswordSuccess -> {
                Toast.makeText(context, "密码修改成功，请重新登录", Toast.LENGTH_LONG).show()
                viewModel?.resetAuthState()
                viewModel?.logout()
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }
            is com.example.smartlogistics.viewmodel.AuthState.Error -> {
                showError = true
                errorMessage = (authState as com.example.smartlogistics.viewmodel.AuthState.Error).message
            }
            else -> {}
        }
    }

    // 离开页面时重置状态
    DisposableEffect(Unit) {
        onDispose {
            viewModel?.resetAuthState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("修改密码", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel?.resetAuthState()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // 顶部图标
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // 手机号提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = primaryColor.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "验证码将发送至 $maskedPhone",
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 验证码输入
            Text(
                text = "验证码",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = verifyCode,
                onValueChange = {
                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                        verifyCode = it
                        showError = false
                    }
                },
                placeholder = { Text("请输入6位验证码") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = BorderLight,
                    cursorColor = primaryColor
                ),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            if (phoneNumber.isNotEmpty()) {
                                viewModel?.sendVerificationCode(phoneNumber)
                            }
                        },
                        enabled = countdown == 0 && !isLoading
                    ) {
                        Text(
                            text = if (countdown > 0) "${countdown}s 后重发" else "获取验证码",
                            color = if (countdown == 0 && !isLoading) primaryColor else TextTertiary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 新密码
            Text(
                text = "新密码",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            var showNewPassword by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = newPassword,
                onValueChange = {
                    newPassword = it
                    showError = false
                },
                placeholder = { Text("请输入新密码（至少8位）") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = BorderLight,
                    cursorColor = primaryColor
                ),
                trailingIcon = {
                    IconButton(onClick = { showNewPassword = !showNewPassword }) {
                        Icon(
                            imageVector = if (showNewPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = TextTertiary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 确认密码
            Text(
                text = "确认密码",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            var showConfirmPassword by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    showError = false
                },
                placeholder = { Text("请再次输入新密码") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = BorderLight,
                    cursorColor = primaryColor,
                    errorBorderColor = ErrorRed
                ),
                trailingIcon = {
                    IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                        Icon(
                            imageVector = if (showConfirmPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = TextTertiary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 密码不一致提示
            if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                Text(
                    text = "两次输入的密码不一致",
                    color = ErrorRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 错误提示
            AnimatedVisibility(visible = showError) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRedLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Error,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = ErrorRed,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 确认修改按钮
            Button(
                onClick = {
                    when {
                        verifyCode.length != 6 -> {
                            showError = true
                            errorMessage = "请输入6位验证码"
                        }
                        newPassword.length < 8 -> {
                            showError = true
                            errorMessage = "密码长度至少8位"
                        }
                        newPassword != confirmPassword -> {
                            showError = true
                            errorMessage = "两次输入的密码不一致"
                        }
                        else -> {
                            viewModel?.resetPassword(phoneNumber, verifyCode, newPassword)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "确认修改",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 提示信息
            Text(
                text = "修改密码后需要重新登录",
                fontSize = 12.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}