package com.example.smartlogistics.network

import android.content.Context
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Retrofit网络客户端
 * 配置API基础地址和请求拦截器
 *
 * 修复记录：
 * - 2026-01-17: 修复 authInterceptor 覆盖 Content-Type 的问题
 */
object RetrofitClient {

    private const val TAG = "RetrofitClient"

    // ★★★ 后端API地址配置 ★★★
    // Mock Server地址 (开发测试用)
    private const val MOCK_BASE_URL = "https://9483c0d4-6115-459b-b90c-7c05c86e269b.mock.pstmn.io/"

    // 真实后端地址
    // Android模拟器访问本机用 10.0.2.2
    // 真机调试用电脑局域网IP，如 192.168.1.100
    private const val REAL_BASE_URL = "http://192.168.31.4:8000/"

    private var baseUrl = REAL_BASE_URL
    private var retrofit: Retrofit? = null
    private var tokenManager: TokenManager? = null

    /**
     * 初始化网络客户端
     * @param context 应用Context
     * @param useMock true使用Mock Server, false使用真实后端
     * @param customBaseUrl 可选的自定义API地址
     */
    fun init(context: Context, useMock: Boolean = false, customBaseUrl: String? = null) {
        tokenManager = TokenManager(context)
        baseUrl = customBaseUrl ?: if (useMock) MOCK_BASE_URL else REAL_BASE_URL
        retrofit = createRetrofit()
        Log.d(TAG, "RetrofitClient initialized with baseUrl: $baseUrl")
    }

    /**
     * 创建Retrofit实例
     */
    private fun createRetrofit(): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(errorInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * 认证拦截器 - 自动添加Token
     *
     * ⚠️ 重要修复：不再强制设置 Content-Type
     * Retrofit 会根据注解自动设置正确的 Content-Type:
     * - @FormUrlEncoded -> application/x-www-form-urlencoded
     * - @Body (JSON) -> application/json
     * - @Multipart -> multipart/form-data
     */
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val token = tokenManager?.getToken()

        val requestBuilder = originalRequest.newBuilder()

        // 只添加 Authorization header
        // 不设置 Content-Type，让 Retrofit 根据注解自动处理
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        // 添加通用请求头
        requestBuilder.header("Accept", "application/json")

        val newRequest = requestBuilder.build()

        // 调试日志
        Log.d(TAG, "Request: ${newRequest.method} ${newRequest.url}")
        Log.d(TAG, "Content-Type: ${newRequest.body?.contentType()}")

        chain.proceed(newRequest)
    }

    /**
     * 错误拦截器 - 统一处理网络错误
     */
    private val errorInterceptor = Interceptor { chain ->
        try {
            val response = chain.proceed(chain.request())

            // 记录响应状态
            Log.d(TAG, "Response: ${response.code} for ${chain.request().url}")

            response
        } catch (e: ConnectException) {
            Log.e(TAG, "连接失败: ${e.message}")
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "连接超时: ${e.message}")
            throw e
        } catch (e: UnknownHostException) {
            Log.e(TAG, "无法解析主机: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "网络错误: ${e.message}")
            throw e
        }
    }

    /**
     * 日志拦截器 - 开发调试用
     */
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("OkHttp", message)
    }.apply {
        // 开发环境显示完整日志，生产环境只显示基本信息
        level = HttpLoggingInterceptor.Level.BODY
    }

    /**
     * 获取API服务接口
     */
    val apiService: ApiService
        get() {
            if (retrofit == null) {
                throw IllegalStateException("RetrofitClient未初始化，请先调用init()")
            }
            return retrofit!!.create(ApiService::class.java)
        }

    /**
     * 获取当前使用的API地址
     */
    fun getCurrentBaseUrl(): String = baseUrl

    /**
     * 获取WebSocket基础地址
     * 自动将 http/https 转换为 ws/wss
     */
    fun getWebSocketBaseUrl(): String {
        return baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/')
    }

    /**
     * 动态切换API地址
     */
    fun switchBaseUrl(newBaseUrl: String) {
        baseUrl = newBaseUrl
        retrofit = createRetrofit()
        Log.d(TAG, "Switched to baseUrl: $baseUrl")
    }

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = retrofit != null
}