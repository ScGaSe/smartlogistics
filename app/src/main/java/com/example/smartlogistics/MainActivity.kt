package com.example.smartlogistics

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.smartlogistics.ui.screens.*
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.MainViewModel
import com.amap.api.maps.MapsInitializer
import com.example.smartlogistics.network.NotificationService

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化讯飞SDK
        initXunfeiSDK()

        // 初始化高德地图隐私合规
        try {
            MapsInitializer.updatePrivacyShow(this, true, true)
            MapsInitializer.updatePrivacyAgree(this, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 初始化网络客户端
        com.example.smartlogistics.network.RetrofitClient.init(
            context = applicationContext,
            useMock = true
        )

        // ⭐ 初始化通知服务
        NotificationService.getInstance().initialize(this)

        setContent {
            SmartLogisticsTheme {
                MainAppEntry(viewModel = viewModel)
            }
        }
    }

    // 讯飞SDK初始化（队友B添加）
    private fun initXunfeiSDK() {
        try {
            val config = com.iflytek.sparkchain.core.SparkChainConfig.builder()
                .appID("a0ede71d")
                .apiKey("ae2e5348692344a0bc4834e44ec338ff")
                .apiSecret("ZjM1YWM2OTA5YTRmYTEzMGY5ZWRjNDJk")

            val ret = com.iflytek.sparkchain.core.SparkChain.getInst().init(application, config)
            android.util.Log.d("XunfeiSDK", "SDK初始化结果: $ret (0=成功)")

            if (ret != 0) {
                android.util.Log.e("XunfeiSDK", "SDK初始化失败，错误码: $ret")
            }
        } catch (e: Exception) {
            android.util.Log.e("XunfeiSDK", "SDK初始化异常: ${e.message}")
            e.printStackTrace()
        }
    }
}


@Composable
fun MainAppEntry(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 当前用户主页路由
    var userHomeRoute by remember { mutableStateOf("car_home") }

    // 底部导航配置
    val bottomItems = listOf(
        BottomNavItem("主页", userHomeRoute, Icons.Rounded.Home),
        BottomNavItem("导航", "navigation_map", Icons.Rounded.Navigation),
        BottomNavItem("我的", "user_profile", Icons.Rounded.Person)
    )

    // 显示底部导航栏的页面
    val showBottomBar = currentRoute?.startsWith("truck_home") == true ||
            currentRoute?.startsWith("car_home") == true ||
            currentRoute?.startsWith("navigation_map") == true ||
            currentRoute?.startsWith("user_profile") == true

    // 根据当前模式决定主色
    val isProfessionalMode = currentRoute?.startsWith("truck") == true ||
            userHomeRoute == "truck_home"
    val primaryColor = if (isProfessionalMode) TruckOrange else CarGreen

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar(
                    modifier = Modifier
                        .shadow(16.dp)
                        .background(Color.White),
                    containerColor = Color.White,
                    tonalElevation = 0.dp
                ) {
                    bottomItems.forEach { item ->
                        val isSelected = if (item.name == "主页") {
                            currentRoute == "truck_home" || currentRoute == "car_home"
                        } else {
                            currentRoute?.startsWith(item.route) == true
                        }

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.name,
                                    modifier = Modifier.size(32.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = item.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                val targetRoute = if (item.name == "主页") userHomeRoute else item.route
                                navController.navigate(targetRoute) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = primaryColor,
                                selectedTextColor = primaryColor,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                                indicatorColor = primaryColor.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "login",
            modifier = Modifier.padding(innerPadding),
            // 禁用页面切换动画，避免"弹一下"
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            // ==================== 认证模块 ====================
            composable("login") {
                LoginScreen(
                    navController = navController,
                    viewModel = viewModel,
                    onLoginSuccess = { targetHome ->
                        userHomeRoute = targetHome
                    }
                )
            }

            composable("register") {
                RegisterScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            // ==================== 货车司机模块 ====================
            composable("truck_home") {
                TruckHomeScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("truck_bind") {
                TruckBindScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("truck_route") {
                TruckRouteScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("truck_road") {
                TruckRoadScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("truck_congestion") {
                TruckCongestionScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("truck_history") {
                TruckHistoryScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable(route = "cargo_report") {
                CargoReportScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            // ==================== 私家车主模块 ====================
            composable("car_home") {
                CarHomeScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("car_bind") {
                CarBindScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("car_route") {
                CarRouteScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("car_road") {
                CarRoadScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("car_congestion") {
                CarCongestionScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("car_history") {
                CarHistoryScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("my_trips") {
                MyTripsScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            // ==================== 位置共享模块 ====================
            // 发起位置共享（分享自己的位置）
            composable(
                route = "location_share/share/{tripId}",
                arguments = listOf(
                    navArgument("tripId") {
                        type = NavType.IntType
                    }
                )
            ) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getInt("tripId")
                LocationShareScreen(
                    navController = navController,
                    mode = "share",
                    tripId = tripId
                )
            }

            // 查看位置共享（查看对方分享的位置）
            composable(
                route = "location_share/view/{shareId}",
                arguments = listOf(
                    navArgument("shareId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val shareId = backStackEntry.arguments?.getString("shareId")
                LocationShareScreen(
                    navController = navController,
                    mode = "view",
                    shareId = shareId
                )
            }

            // ==================== 公共模块 ====================
            // 导航页面 - 支持可选的目的地参数
            composable(
                route = "navigation_map?destination={destination}",
                arguments = listOf(
                    navArgument("destination") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val destination = backStackEntry.arguments?.getString("destination") ?: ""
                val decodedDest = if (destination.isNotBlank()) {
                    try {
                        android.net.Uri.decode(destination)
                    } catch (e: Exception) {
                        destination
                    }
                } else ""
                NavigationMapScreenNew(
                    navController = navController,
                    viewModel = viewModel,
                    initialDestination = decodedDest
                )
            }

            composable(
                route = "ai_result/{query}",
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query") ?: ""
                AiResultScreen(
                    navController = navController,
                    query = query,
                    viewModel = viewModel
                )
            }

            composable("user_profile") {
                UserProfileScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("settings") {
                SettingsScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            // ==================== 个人中心子页面 ====================
            composable("edit_profile") {
                EditProfileScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("account_security") {
                AccountSecurityScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("notification_settings") {
                NotificationSettingsScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("help_center") {
                HelpCenterScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("about") {
                AboutScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("user_agreement") {
                UserAgreementScreen(navController = navController)
            }

            composable("privacy_policy") {
                PrivacyPolicyScreen(navController = navController)
            }

            composable("offline_map") {
                OfflineMapScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            // ==================== AI对话页面 ====================
            composable("ai_chat") {
                AiChatScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }

            composable("ai_result/{query}") {
                AiChatScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }
    }
}

// 底部导航项数据类
data class BottomNavItem(
    val name: String,
    val route: String,
    val icon: ImageVector
)