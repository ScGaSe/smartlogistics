package com.example.smartlogistics.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * WebSocket位置共享管理器
 * 用于实时位置共享功能
 */
class WebSocketManager(
    private val baseUrl: String,
    private val token: String?
) {
    companion object {
        private const val TAG = "WebSocketManager"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }
    
    // WebSocket连接状态
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var reconnectAttempts = 0
    private var shouldReconnect = true
    private var currentShareId: String? = null
    
    // 连接状态
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    // 接收到的位置消息
    private val _locationUpdates = MutableSharedFlow<LocationMessage>(replay = 1)
    val locationUpdates: SharedFlow<LocationMessage> = _locationUpdates
    
    /**
     * 连接到位置共享WebSocket
     * @param shareId 分享ID
     */
    fun connect(shareId: String) {
        if (_connectionState.value == ConnectionState.Connected || 
            _connectionState.value == ConnectionState.Connecting) {
            Log.d(TAG, "Already connected or connecting")
            return
        }
        
        currentShareId = shareId
        shouldReconnect = true
        reconnectAttempts = 0
        
        doConnect(shareId)
    }
    
    private fun doConnect(shareId: String) {
        _connectionState.value = ConnectionState.Connecting
        
        val wsUrl = "$baseUrl/ws/share/$shareId"
        Log.d(TAG, "Connecting to WebSocket: $wsUrl")
        
        val requestBuilder = Request.Builder().url(wsUrl)
        
        // 添加Token认证（如果有）
        token?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        
        val request = requestBuilder.build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _connectionState.value = ConnectionState.Connected
                reconnectAttempts = 0
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received message: $text")
                try {
                    val message = gson.fromJson(text, LocationMessage::class.java)
                    if (message.type == "location") {
                        scope.launch {
                            _locationUpdates.emit(message)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse message: ${e.message}")
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                _connectionState.value = ConnectionState.Disconnected
                
                // 尝试重连
                if (shouldReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    scheduleReconnect()
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                _connectionState.value = ConnectionState.Error(t.message ?: "连接失败")
                
                // 尝试重连
                if (shouldReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    scheduleReconnect()
                }
            }
        })
    }
    
    private fun scheduleReconnect() {
        reconnectAttempts++
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts")
        
        scope.launch {
            delay(RECONNECT_DELAY_MS * reconnectAttempts)
            currentShareId?.let { doConnect(it) }
        }
    }
    
    /**
     * 发送位置消息
     */
    fun sendLocation(latitude: Double, longitude: Double, accuracy: Float? = null, speed: Float? = null, heading: Float? = null) {
        if (_connectionState.value != ConnectionState.Connected) {
            Log.w(TAG, "Cannot send location: not connected")
            return
        }
        
        val message = LocationMessage(
            type = "location",
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            speed = speed,
            heading = heading,
            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
        )
        
        val json = gson.toJson(message)
        Log.d(TAG, "Sending location: $json")
        
        webSocket?.send(json)
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        shouldReconnect = false
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    /**
     * 释放资源
     */
    fun release() {
        disconnect()
        scope.cancel()
    }
}

/**
 * Mock WebSocket管理器（用于本地测试）
 * 模拟对方位置移动
 */
class MockWebSocketManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _connectionState = MutableStateFlow<WebSocketManager.ConnectionState>(
        WebSocketManager.ConnectionState.Disconnected
    )
    val connectionState: StateFlow<WebSocketManager.ConnectionState> = _connectionState
    
    private val _locationUpdates = MutableSharedFlow<LocationMessage>(replay = 1)
    val locationUpdates: SharedFlow<LocationMessage> = _locationUpdates
    
    // 模拟的起始位置（长沙火车站）
    private var mockLat = 28.194
    private var mockLng = 113.005
    private var simulationJob: Job? = null
    
    /**
     * 连接（模拟）
     */
    fun connect(shareId: String) {
        _connectionState.value = WebSocketManager.ConnectionState.Connecting
        
        scope.launch {
            delay(500) // 模拟连接延迟
            _connectionState.value = WebSocketManager.ConnectionState.Connected
            
            // 开始模拟位置移动
            startLocationSimulation()
        }
    }
    
    private fun startLocationSimulation() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            while (isActive) {
                // 模拟位置移动（向用户方向靠近）
                mockLat += (Math.random() - 0.3) * 0.001
                mockLng += (Math.random() - 0.3) * 0.001
                
                val message = LocationMessage(
                    type = "location",
                    latitude = mockLat,
                    longitude = mockLng,
                    accuracy = 10f,
                    speed = (20 + Math.random() * 40).toFloat(),
                    heading = (Math.random() * 360).toFloat(),
                    timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date())
                )
                
                _locationUpdates.emit(message)
                
                delay(3000) // 每3秒更新一次
            }
        }
    }
    
    /**
     * 发送位置（模拟模式下不需要真正发送）
     */
    fun sendLocation(latitude: Double, longitude: Double, accuracy: Float? = null, speed: Float? = null, heading: Float? = null) {
        // Mock模式下记录日志即可
        android.util.Log.d("MockWebSocket", "Sending location: $latitude, $longitude")
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        simulationJob?.cancel()
        _connectionState.value = WebSocketManager.ConnectionState.Disconnected
    }
    
    /**
     * 释放资源
     */
    fun release() {
        disconnect()
        scope.cancel()
    }
}
