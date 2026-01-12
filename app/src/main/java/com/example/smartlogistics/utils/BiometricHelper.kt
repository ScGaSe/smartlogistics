package com.example.smartlogistics.utils

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 生物识别（指纹/面部）认证工具类
 * 封装AndroidX Biometric库，提供简单的认证接口
 */
class BiometricHelper(private val context: Context) {

    // 认证状态
    sealed class AuthState {
        object Idle : AuthState()
        object Authenticating : AuthState()
        object Success : AuthState()
        data class Error(val message: String, val errorCode: Int) : AuthState()
        object Cancelled : AuthState()
    }

    // 设备支持状态
    sealed class BiometricStatus {
        object Available : BiometricStatus()              // 可用
        object NoHardware : BiometricStatus()             // 设备无硬件
        object HardwareUnavailable : BiometricStatus()    // 硬件不可用
        object NoneEnrolled : BiometricStatus()           // 未录入指纹
        object SecurityUpdateRequired : BiometricStatus() // 需要安全更新
        object Unsupported : BiometricStatus()            // 不支持
        data class Unknown(val code: Int) : BiometricStatus()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private var biometricPrompt: BiometricPrompt? = null

    /**
     * 检查设备是否支持生物识别
     */
    fun checkBiometricStatus(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)

        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.Available
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NoHardware
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HardwareUnavailable
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NoneEnrolled
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SecurityUpdateRequired
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricStatus.Unsupported
            else -> BiometricStatus.Unknown(-1)
        }
    }

    /**
     * 是否可以使用生物识别
     */
    fun isAvailable(): Boolean {
        return checkBiometricStatus() == BiometricStatus.Available
    }

    /**
     * 获取不可用原因的友好提示
     */
    fun getUnavailableReason(): String {
        return when (checkBiometricStatus()) {
            is BiometricStatus.Available -> ""
            is BiometricStatus.NoHardware -> "您的设备不支持指纹识别"
            is BiometricStatus.HardwareUnavailable -> "指纹硬件暂不可用"
            is BiometricStatus.NoneEnrolled -> "请先在系统设置中录入指纹"
            is BiometricStatus.SecurityUpdateRequired -> "需要安全更新才能使用指纹"
            is BiometricStatus.Unsupported -> "当前系统版本不支持指纹识别"
            is BiometricStatus.Unknown -> "指纹识别不可用"
        }
    }

    /**
     * 发起生物识别认证
     * @param activity FragmentActivity实例
     * @param title 弹窗标题
     * @param subtitle 弹窗副标题
     * @param negativeButtonText 取消按钮文字
     * @param onSuccess 认证成功回调
     * @param onError 认证失败回调
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "指纹登录",
        subtitle: String = "请验证指纹以继续",
        negativeButtonText: String = "取消",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // 检查是否可用
        if (!isAvailable()) {
            val reason = getUnavailableReason()
            _authState.value = AuthState.Error(reason, -1)
            onError(reason)
            return
        }

        val executor = ContextCompat.getMainExecutor(context)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                _authState.value = AuthState.Success
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)

                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> {
                        _authState.value = AuthState.Cancelled
                    }
                    else -> {
                        _authState.value = AuthState.Error(errString.toString(), errorCode)
                        onError(errString.toString())
                    }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // 认证失败但可以重试，不改变状态
            }
        }

        biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        _authState.value = AuthState.Authenticating
        biometricPrompt?.authenticate(promptInfo)
    }

    /**
     * 取消认证
     */
    fun cancelAuthentication() {
        biometricPrompt?.cancelAuthentication()
        _authState.value = AuthState.Idle
    }

    /**
     * 重置状态
     */
    fun resetState() {
        _authState.value = AuthState.Idle
    }

    companion object {
        /**
         * 快速检查设备是否支持生物识别
         */
        fun isSupported(context: Context): Boolean {
            return BiometricHelper(context).isAvailable()
        }
    }
}