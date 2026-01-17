package com.example.smartlogistics.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit网络客户端
 * 配置API基础地址和请求拦截器
 */
object RetrofitClient {

    // ★★★ 后端API地址配置 ★★★
    // Mock Server地址 (开发测试用)
    private const val MOCK_BASE_URL = "https://9483c0d4-6115-459b-b90c-7c05c86e269b.mock.pstmn.io/"

    // 真实后端地址
    // Android模拟器访问本机用 10.0.2.2
    // 真机调试用电脑局域网IP，如 192.168.1.100
    private const val REAL_BASE_URL = "http://192.168.31.4:8000/"

    private var baseUrl = MOCK_BASE_URL
    private var retrofit: Retrofit? = null
    private var tokenManager: TokenManager? = null

    /**
     * 初始化网络客户端
     * @param context 应用Context
     * @param useMock true使用Mock Server, false使用真实后端
     * @param customBaseUrl 可选的自定义API地址
     */
    fun init(context: Context, useMock: Boolean = true, customBaseUrl: String? = null) {
        tokenManager = TokenManager(context)
        baseUrl = customBaseUrl ?: if (useMock) MOCK_BASE_URL else REAL_BASE_URL
        retrofit = createRetrofit()
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
     */
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val token = tokenManager?.getToken()

        val newRequest = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()
        } else {
            originalRequest.newBuilder()
                .header("Content-Type", "application/json")
                .build()
        }

        chain.proceed(newRequest)
    }

    /**
     * 日志拦截器 - 开发调试用
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
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
     * 动态切换API地址
     */
    fun switchBaseUrl(newBaseUrl: String) {
        baseUrl = newBaseUrl
        retrofit = createRetrofit()
    }
}