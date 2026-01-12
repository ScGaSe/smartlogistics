package com.example.smartlogistics.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.smartlogistics.ui.components.*
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.AuthState
import com.example.smartlogistics.viewmodel.MainViewModel

// ==================== 登录页面 ====================
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: MainViewModel? = null,
    onLoginSuccess: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("professional") } // professional | personal
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // 观察登录状态
    val authState by viewModel?.authState?.collectAsState() ?: remember { mutableStateOf(AuthState.Idle) }
    val isLoading = authState is AuthState.Loading
    
    // 处理登录结果
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.LoginSuccess -> {
                val targetHome = (authState as AuthState.LoginSuccess).targetHome
                onLoginSuccess(targetHome)
                navController.navigate(targetHome) {
                    popUpTo("login") { inclusive = true }
                }
                viewModel?.resetAuthState()
            }
            is AuthState.Error -> {
                showError = true
                errorMessage = (authState as AuthState.Error).message
            }
            else -> {}
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
    ) {
        // 顶部装饰背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BrandBlue,
                            BrandBlue.copy(alpha = 0.8f)
                        )
                    ),
                    shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
                )
        ) {
            // 装饰圆形
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .offset(x = (-40).dp, y = (-40).dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 30.dp, y = 60.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Logo区域
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(BrandBlue, Color(0xFF9B51E0))
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Navigation,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "HubLink Navigator",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "智慧交通 · 一路畅行",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // 登录卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "欢迎登录",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "请选择您的身份并登录",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 角色选择
                    Text(
                        text = "选择身份",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 货运司机选项
                        RoleSelectionCard(
                            modifier = Modifier.weight(1f),
                            title = "货运司机",
                            icon = Icons.Rounded.LocalShipping,
                            isSelected = selectedRole == "professional",
                            selectedColor = TruckOrange,
                            onClick = { selectedRole = "professional" }
                        )
                        
                        // 私家车主选项
                        RoleSelectionCard(
                            modifier = Modifier.weight(1f),
                            title = "私家车主",
                            icon = Icons.Rounded.DirectionsCar,
                            isSelected = selectedRole == "personal",
                            selectedColor = CarGreen,
                            onClick = { selectedRole = "personal" }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    // 用户名输入
                    StyledTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            showError = false
                        },
                        label = "用户名",
                        leadingIcon = Icons.Default.Person,
                        keyboardType = KeyboardType.Text
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 密码输入
                    StyledTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            showError = false
                        },
                        label = "密码",
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true
                    )
                    
                    // 错误提示
                    AnimatedVisibility(
                        visible = showError,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
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
                                    imageVector = Icons.Default.Error,
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
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 忘记密码
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { /* TODO */ }) {
                            Text(
                                text = "忘记密码?",
                                color = BrandBlue,
                                fontSize = 13.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 登录按钮
                    PrimaryButton(
                        text = "登 录",
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                showError = true
                                errorMessage = "请输入用户名和密码"
                                return@PrimaryButton
                            }
                            
                            if (viewModel != null) {
                                viewModel.login(username, password, selectedRole)
                            } else {
                                // 模拟登录 (无ViewModel时)
                                val targetHome = if (selectedRole == "professional") "truck_home" else "car_home"
                                onLoginSuccess(targetHome)
                                navController.navigate(targetHome) {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        },
                        isLoading = isLoading,
                        backgroundColor = if (selectedRole == "professional") TruckOrange else CarGreen
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 其他登录方式
                    SectionDivider(title = "其他登录方式")
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 指纹登录
                        IconButton(
                            onClick = { /* TODO: 生物识别 */ },
                            modifier = Modifier
                                .size(56.dp)
                                .background(BackgroundSecondary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = "指纹登录",
                                tint = TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(24.dp))
                        
                        // 人脸登录
                        IconButton(
                            onClick = { /* TODO: 人脸识别 */ },
                            modifier = Modifier
                                .size(56.dp)
                                .background(BackgroundSecondary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Face,
                                contentDescription = "人脸登录",
                                tint = TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 注册链接
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "还没有账号? ",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                TextButton(onClick = { navController.navigate("register") }) {
                    Text(
                        text = "立即注册",
                        color = BrandBlue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ==================== 角色选择卡片 ====================
@Composable
fun RoleSelectionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) selectedColor.copy(alpha = 0.1f) else BackgroundSecondary
        ),
        border = if (isSelected) BorderStroke(2.dp, selectedColor) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) selectedColor else TextSecondary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) selectedColor else TextSecondary
            )
        }
    }
}

// ==================== 注册页面 ====================
@Composable
fun RegisterScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("professional") }
    var agreedToTerms by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val authState by viewModel?.authState?.collectAsState() ?: remember { mutableStateOf(AuthState.Idle) }
    val isLoading = authState is AuthState.Loading
    
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.RegisterSuccess -> {
                navController.popBackStack()
                viewModel?.resetAuthState()
            }
            is AuthState.Error -> {
                showError = true
                errorMessage = (authState as AuthState.Error).message
            }
            else -> {}
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
    ) {
        // 顶部装饰
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(BrandBlue, BrandBlue.copy(alpha = 0.8f))
                    ),
                    shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "创建账号",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 注册卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "注册新账号",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "填写以下信息完成注册",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 角色选择
                    Text(
                        text = "选择身份",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RoleSelectionCard(
                            modifier = Modifier.weight(1f),
                            title = "货运司机",
                            icon = Icons.Rounded.LocalShipping,
                            isSelected = selectedRole == "professional",
                            selectedColor = TruckOrange,
                            onClick = { selectedRole = "professional" }
                        )
                        RoleSelectionCard(
                            modifier = Modifier.weight(1f),
                            title = "私家车主",
                            icon = Icons.Rounded.DirectionsCar,
                            isSelected = selectedRole == "personal",
                            selectedColor = CarGreen,
                            onClick = { selectedRole = "personal" }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 手机号
                    StyledTextField(
                        value = phoneNumber,
                        onValueChange = { 
                            phoneNumber = it
                            showError = false
                        },
                        label = "手机号",
                        leadingIcon = Icons.Default.Phone,
                        keyboardType = KeyboardType.Phone
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 密码
                    StyledTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            showError = false
                        },
                        label = "设置密码",
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 确认密码
                    StyledTextField(
                        value = confirmPassword,
                        onValueChange = { 
                            confirmPassword = it
                            showError = false
                        },
                        label = "确认密码",
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true,
                        isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                        errorMessage = if (confirmPassword.isNotEmpty() && password != confirmPassword) 
                            "两次输入的密码不一致" else null
                    )
                    
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
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = errorMessage, color = ErrorRed, fontSize = 13.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 用户协议
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = agreedToTerms,
                            onCheckedChange = { agreedToTerms = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BrandBlue,
                                uncheckedColor = TextTertiary
                            )
                        )
                        Text(
                            text = "我已阅读并同意",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        TextButton(
                            onClick = { /* TODO */ },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "《用户协议》",
                                fontSize = 13.sp,
                                color = BrandBlue
                            )
                        }
                        Text(text = "和", fontSize = 13.sp, color = TextSecondary)
                        TextButton(
                            onClick = { /* TODO */ },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "《隐私政策》",
                                fontSize = 13.sp,
                                color = BrandBlue
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 注册按钮
                    PrimaryButton(
                        text = "注 册",
                        onClick = {
                            when {
                                phoneNumber.isBlank() -> {
                                    showError = true
                                    errorMessage = "请输入手机号"
                                }
                                password.length < 6 -> {
                                    showError = true
                                    errorMessage = "密码长度至少6位"
                                }
                                password != confirmPassword -> {
                                    showError = true
                                    errorMessage = "两次输入的密码不一致"
                                }
                                !agreedToTerms -> {
                                    showError = true
                                    errorMessage = "请先同意用户协议"
                                }
                                else -> {
                                    if (viewModel != null) {
                                        viewModel.register(phoneNumber, password, selectedRole)
                                    } else {
                                        navController.popBackStack()
                                    }
                                }
                            }
                        },
                        isLoading = isLoading,
                        enabled = agreedToTerms,
                        backgroundColor = if (selectedRole == "professional") TruckOrange else CarGreen
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 登录链接
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已有账号? ",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                TextButton(onClick = { navController.popBackStack() }) {
                    Text(
                        text = "立即登录",
                        color = BrandBlue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
