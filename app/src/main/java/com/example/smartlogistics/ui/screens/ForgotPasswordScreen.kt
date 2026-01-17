package com.example.smartlogistics.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.smartlogistics.ui.components.*
import com.example.smartlogistics.ui.theme.*
import com.example.smartlogistics.viewmodel.AuthState
import com.example.smartlogistics.viewmodel.MainViewModel

/**
 * 忘记密码/找回密码页面
 *
 * 流程：输入手机号 → 获取验证码 → 输入验证码+新密码 → 重置成功
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    navController: NavController,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current

    // 监听认证状态
    val authState by viewModel?.authState?.collectAsState() ?: remember { mutableStateOf(AuthState.Idle) }

    // 输入状态
    var phoneNumber by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // UI状态
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    // 验证码倒计时
    var countdown by remember { mutableIntStateOf(0) }
    var isCodeSent by remember { mutableStateOf(false) }

    // 是否正在加载
    val isLoading = authState is AuthState.Loading

    // 倒计时效果
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000L)
            countdown--
        }
    }

    // 监听认证状态变化
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.CodeSent -> {
                // 验证码发送成功
                isCodeSent = true
                countdown = 60
                showError = false
                Toast.makeText(context, "验证码已发送到 $phoneNumber", Toast.LENGTH_SHORT).show()
            }
            is AuthState.ResetPasswordSuccess -> {
                // 密码重置成功
                showError = false
                showSuccess = true
                Toast.makeText(context, "密码重置成功！", Toast.LENGTH_SHORT).show()

                // 延迟返回登录页
                kotlinx.coroutines.delay(1500)
                viewModel?.resetAuthState()
                navController.popBackStack()
            }
            is AuthState.Error -> {
                // 显示错误信息
                showError = true
                errorMessage = (authState as AuthState.Error).message
            }
            else -> {}
        }
    }

    // 页面退出时重置状态
    DisposableEffect(Unit) {
        onDispose {
            viewModel?.resetAuthState()
        }
    }

    // 发送验证码
    fun sendVerificationCode() {
        if (phoneNumber.length != 11) {
            showError = true
            errorMessage = "请输入正确的手机号"
            return
        }

        showError = false

        if (viewModel != null) {
            // 调用真实接口
            viewModel.sendVerificationCode(phoneNumber)
        } else {
            // Mock模式（无ViewModel时）
            isCodeSent = true
            countdown = 60
            Toast.makeText(context, "验证码已发送到 $phoneNumber (Mock)", Toast.LENGTH_SHORT).show()
        }
    }

    // 重置密码
    fun resetPassword() {
        // 表单验证
        when {
            phoneNumber.length != 11 -> {
                showError = true
                errorMessage = "请输入正确的手机号"
            }
            verificationCode.length != 6 -> {
                showError = true
                errorMessage = "请输入6位验证码"
            }
            newPassword.length < 6 -> {
                showError = true
                errorMessage = "密码长度至少6位"
            }
            newPassword != confirmPassword -> {
                showError = true
                errorMessage = "两次输入的密码不一致"
            }
            else -> {
                showError = false

                if (viewModel != null) {
                    // 调用真实接口
                    viewModel.resetPassword(phoneNumber, verificationCode, newPassword)
                } else {
                    // Mock模式
                    showSuccess = true
                    Toast.makeText(context, "密码重置成功！(Mock)", Toast.LENGTH_SHORT).show()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        navController.popBackStack()
                    }, 1500)
                }
            }
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
                .height(220.dp)
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
                    .size(120.dp)
                    .offset(x = (-30).dp, y = (-30).dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = 40.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                Text(
                    text = "找回密码",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 图标
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(80.dp)
                    .background(Color.White, RoundedCornerShape(20.dp))
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
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockReset,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 表单卡片
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
                        text = "重置密码",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "请输入您的手机号，我们将发送验证码",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 手机号输入
                    StyledTextField(
                        value = phoneNumber,
                        onValueChange = {
                            if (it.length <= 11 && it.all { c -> c.isDigit() }) {
                                phoneNumber = it
                                showError = false
                            }
                        },
                        label = "手机号",
                        leadingIcon = Icons.Default.Phone,
                        keyboardType = KeyboardType.Phone,
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 验证码输入 + 获取按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 验证码输入框
                        Box(modifier = Modifier.weight(1f)) {
                            StyledTextField(
                                value = verificationCode,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                        verificationCode = it
                                        showError = false
                                    }
                                },
                                label = "验证码",
                                leadingIcon = Icons.Default.Sms,
                                keyboardType = KeyboardType.Number,
                                enabled = !isLoading
                            )
                        }

                        // 获取验证码按钮
                        OutlinedButton(
                            onClick = { sendVerificationCode() },
                            enabled = countdown == 0 && phoneNumber.length == 11 && !isLoading,
                            modifier = Modifier
                                .height(48.dp)
                                .width(100.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (countdown == 0) BrandBlue else TextTertiary,
                                disabledContentColor = TextTertiary
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (countdown == 0 && phoneNumber.length == 11 && !isLoading)
                                    BrandBlue else Color.Gray.copy(alpha = 0.3f)
                            )
                        ) {
                            if (isLoading && !isCodeSent) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandBlue
                                )
                            } else {
                                Text(
                                    text = if (countdown > 0) "${countdown}s" else "获取验证码",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 新密码
                    StyledTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            showError = false
                        },
                        label = "新密码",
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true,
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 确认密码
                    StyledTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            showError = false
                        },
                        label = "确认新密码",
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true,
                        isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword,
                        errorMessage = if (confirmPassword.isNotEmpty() && newPassword != confirmPassword)
                            "两次输入的密码不一致" else null,
                        enabled = !isLoading
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

                    // 成功提示
                    AnimatedVisibility(
                        visible = showSuccess,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "密码重置成功，正在返回登录页...",
                                    color = SuccessGreen,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 重置密码按钮
                    PrimaryButton(
                        text = "重置密码",
                        onClick = { resetPassword() },
                        isLoading = isLoading && isCodeSent,
                        enabled = phoneNumber.isNotEmpty() &&
                                verificationCode.isNotEmpty() &&
                                newPassword.isNotEmpty() &&
                                confirmPassword.isNotEmpty() &&
                                !showSuccess &&
                                !isLoading,
                        backgroundColor = BrandBlue
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 提示信息
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "验证码将发送到您的手机",
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 返回登录
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "想起密码了? ",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                TextButton(onClick = { navController.popBackStack() }) {
                    Text(
                        text = "返回登录",
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