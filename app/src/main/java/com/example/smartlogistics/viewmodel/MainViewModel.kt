package com.example.smartlogistics.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartlogistics.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主ViewModel
 * 管理全局状态和通用数据
 * 与后端API真实对接
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val repository = Repository(application.applicationContext)

    // ==================== 认证状态 ====================
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(repository.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    private val _userRole = MutableStateFlow("personal")
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    // ==================== 车辆状态 ====================
    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _vehicleState = MutableStateFlow<VehicleState>(VehicleState.Idle)
    val vehicleState: StateFlow<VehicleState> = _vehicleState.asStateFlow()

    // ==================== 报备状态 (专业模式) ====================
    private val _reports = MutableStateFlow<List<CargoReport>>(emptyList())
    val reports: StateFlow<List<CargoReport>> = _reports.asStateFlow()

    private val _reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    // ==================== 行程状态 (个人模式) ====================
    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _tripState = MutableStateFlow<TripState>(TripState.Idle)
    val tripState: StateFlow<TripState> = _tripState.asStateFlow()

    // ==================== 导航状态 ====================
    private val _routeResult = MutableStateFlow<RouteResponse?>(null)
    val routeResult: StateFlow<RouteResponse?> = _routeResult.asStateFlow()

    // ==================== AI助手状态 ====================
    private val _aiResponse = MutableStateFlow<AskResponse?>(null)
    val aiResponse: StateFlow<AskResponse?> = _aiResponse.asStateFlow()

    private val _aiState = MutableStateFlow<AIState>(AIState.Idle)
    val aiState: StateFlow<AIState> = _aiState.asStateFlow()

    // ==================== 拥堵预测状态 ====================
    private val _congestionData = MutableStateFlow<CongestionResponse?>(null)
    val congestionData: StateFlow<CongestionResponse?> = _congestionData.asStateFlow()

    private val _congestionState = MutableStateFlow<CongestionState>(CongestionState.Idle)
    val congestionState: StateFlow<CongestionState> = _congestionState.asStateFlow()

    // ==================== 闸口排队数据 ====================
    private val _gateQueues = MutableStateFlow<Map<String, Int>>(emptyMap())
    val gateQueues: StateFlow<Map<String, Int>> = _gateQueues.asStateFlow()

    // ==================== POI数据 ====================
    private val _pois = MutableStateFlow<List<POI>>(emptyList())
    val pois: StateFlow<List<POI>> = _pois.asStateFlow()

    // ==================== 停车场列表 ====================
    private val _parkingList = MutableStateFlow<List<ParkingInfo>>(emptyList())
    val parkingList: StateFlow<List<ParkingInfo>> = _parkingList.asStateFlow()

    // ==================== 停车预测 ====================
    private val _parkingPrediction = MutableStateFlow<ParkingPredictResponse?>(null)
    val parkingPrediction: StateFlow<ParkingPredictResponse?> = _parkingPrediction.asStateFlow()

    // ==================== 位置共享状态 ====================
    private val _locationShare = MutableStateFlow<LocationShareResponse?>(null)
    val locationShare: StateFlow<LocationShareResponse?> = _locationShare.asStateFlow()

    // ==================== 认证方法 ====================

    fun register(phoneNumber: String, password: String, role: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = repository.register(phoneNumber, password, role)) {
                is NetworkResult.Success -> {
                    Log.d(TAG, "Register success: ${result.data}")
                    _authState.value = AuthState.RegisterSuccess
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Register error: ${result.message}")
                    _authState.value = AuthState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Register exception: ${result.throwable.message}")
                    _authState.value = AuthState.Error(result.throwable.message ?: "注册失败")
                }
                else -> {}
            }
        }
    }

    fun login(username: String, password: String, selectedRole: String) {
        Log.d(TAG, "Starting login for user: $username with role: $selectedRole")

        viewModelScope.launch {
            _authState.value = AuthState.Loading

            when (val result = repository.login(username, password)) {
                is NetworkResult.Success -> {
                    Log.d(TAG, "Login successful, fetching user info...")

                    when (val meResult = repository.getCurrentUser()) {
                        is NetworkResult.Success -> {
                            val userInfo = meResult.data
                            val actualRole = userInfo.role
                            val targetHome = if (actualRole == "professional") "truck_home" else "car_home"

                            Log.d(TAG, "User info received - role: $actualRole, selected: $selectedRole")

                            if (selectedRole == actualRole) {
                                _isLoggedIn.value = true
                                _userInfo.value = userInfo
                                _userRole.value = actualRole
                                _authState.value = AuthState.LoginSuccess(targetHome)
                            } else {
                                _userInfo.value = userInfo
                                _authState.value = AuthState.RoleMismatch(
                                    selectedRole = selectedRole,
                                    actualRole = actualRole,
                                    targetHome = targetHome
                                )
                            }
                        }
                        is NetworkResult.Error -> {
                            Log.e(TAG, "Get user info error: ${meResult.message}")
                            _authState.value = AuthState.Error("获取用户信息失败: ${meResult.message}")
                        }
                        is NetworkResult.Exception -> {
                            Log.e(TAG, "Get user info exception: ${meResult.throwable.message}")
                            _authState.value = AuthState.Error("获取用户信息失败")
                        }
                        else -> {}
                    }
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Login error: ${result.message}")
                    _authState.value = AuthState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Login exception: ${result.throwable.message}")
                    _authState.value = AuthState.Error(result.throwable.message ?: "登录失败")
                }
                else -> {}
            }
        }
    }

    /**
     * 指纹登录
     */
    fun biometricLogin(deviceId: String, selectedRole: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            when (val result = repository.biometricLogin(deviceId)) {
                is NetworkResult.Success -> {
                    when (val meResult = repository.getCurrentUser()) {
                        is NetworkResult.Success -> {
                            val userInfo = meResult.data
                            val actualRole = userInfo.role
                            val targetHome = if (actualRole == "professional") "truck_home" else "car_home"

                            _isLoggedIn.value = true
                            _userInfo.value = userInfo
                            _userRole.value = actualRole
                            _authState.value = AuthState.LoginSuccess(targetHome)
                        }
                        is NetworkResult.Error -> {
                            _authState.value = AuthState.Error(meResult.message)
                        }
                        is NetworkResult.Exception -> {
                            _authState.value = AuthState.Error("获取用户信息失败")
                        }
                        else -> {}
                    }
                }
                is NetworkResult.Error -> {
                    _authState.value = AuthState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _authState.value = AuthState.Error(result.throwable.message ?: "指纹登录失败")
                }
                else -> {}
            }
        }
    }

    /**
     * 绑定设备（用于指纹登录）
     */
    fun bindDevice(deviceId: String) {
        viewModelScope.launch {
            when (val result = repository.bindDevice(deviceId)) {
                is NetworkResult.Success -> {
                    _userInfo.value = result.data
                    Log.d(TAG, "Device bound successfully")
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Bind device error: ${result.message}")
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Bind device exception: ${result.throwable.message}")
                }
                else -> {}
            }
        }
    }

    /**
     * 发送验证码
     */
    fun sendVerificationCode(phoneNumber: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = repository.sendVerificationCode(phoneNumber)) {
                is NetworkResult.Success -> {
                    _authState.value = AuthState.CodeSent
                }
                is NetworkResult.Error -> {
                    _authState.value = AuthState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _authState.value = AuthState.Error(result.throwable.message ?: "发送验证码失败")
                }
                else -> {}
            }
        }
    }

    /**
     * 重置密码
     */
    fun resetPassword(phoneNumber: String, code: String, newPassword: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = repository.resetPassword(phoneNumber, code, newPassword)) {
                is NetworkResult.Success -> {
                    _authState.value = AuthState.ResetPasswordSuccess
                }
                is NetworkResult.Error -> {
                    _authState.value = AuthState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _authState.value = AuthState.Error(result.throwable.message ?: "重置密码失败")
                }
                else -> {}
            }
        }
    }

    /**
     * 确认角色切换后继续登录
     */
    fun confirmRoleMismatch() {
        val currentState = _authState.value
        if (currentState is AuthState.RoleMismatch) {
            _isLoggedIn.value = true
            _userRole.value = currentState.actualRole
            _authState.value = AuthState.LoginSuccess(currentState.targetHome)
        }
    }

    fun logout() {
        repository.logout()
        _isLoggedIn.value = false
        _userInfo.value = null
        _vehicles.value = emptyList()
        _reports.value = emptyList()
        _trips.value = emptyList()
    }

    fun isProfessionalMode(): Boolean = repository.isProfessionalMode()

    fun getUserName(): String = repository.getUserName()

    // ==================== 车辆管理方法 ====================

    fun fetchVehicles() {
        viewModelScope.launch {
            _vehicleState.value = VehicleState.Loading
            when (val result = repository.getVehicles()) {
                is NetworkResult.Success -> {
                    _vehicles.value = result.data
                    _vehicleState.value = VehicleState.Success
                }
                is NetworkResult.Error -> {
                    _vehicleState.value = VehicleState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _vehicleState.value = VehicleState.Error(result.throwable.message ?: "获取车辆失败")
                }
                else -> {}
            }
        }
    }

    fun bindVehicle(
        plateNumber: String,
        vehicleType: String,
        brand: String = "",
        heightM: Double? = null,
        weightT: Double? = null,
        axleCount: Int? = null
    ) {
        viewModelScope.launch {
            _vehicleState.value = VehicleState.Loading
            when (val result = repository.bindVehicle(plateNumber, vehicleType, brand, heightM, weightT, axleCount)) {
                is NetworkResult.Success -> {
                    _vehicles.value = listOf(result.data) + _vehicles.value
                    _vehicleState.value = VehicleState.BindSuccess
                }
                is NetworkResult.Error -> {
                    _vehicleState.value = VehicleState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _vehicleState.value = VehicleState.Error(result.throwable.message ?: "绑定车辆失败")
                }
                else -> {}
            }
        }
    }

    fun unbindVehicle(vehicleId: Int) {
        viewModelScope.launch {
            _vehicleState.value = VehicleState.Loading
            when (val result = repository.unbindVehicle(vehicleId)) {
                is NetworkResult.Success -> {
                    _vehicles.value = _vehicles.value.filterNot { it.id == vehicleId }
                    _vehicleState.value = VehicleState.Success
                }
                is NetworkResult.Error -> {
                    _vehicleState.value = VehicleState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _vehicleState.value = VehicleState.Error(result.throwable.message ?: "解绑车辆失败")
                }
                else -> {}
            }
        }
    }

    // ==================== 货物报备方法 (专业模式) ====================

    fun fetchReports(page: Int = 1) {
        viewModelScope.launch {
            _reportState.value = ReportState.Loading
            when (val result = repository.getCargoReports(page)) {
                is NetworkResult.Success -> {
                    _reports.value = result.data.items
                    _reportState.value = ReportState.Success
                }
                is NetworkResult.Error -> {
                    _reportState.value = ReportState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _reportState.value = ReportState.Error(result.throwable.message ?: "获取报备失败")
                }
                else -> {}
            }
        }
    }

    fun submitReport(
        vehicleId: Int,
        destinationPoiId: String,
        cargoType: String,
        isHazardous: Boolean,
        hazardClass: String? = null,
        weight: Double? = null,
        description: String? = null
    ) {
        viewModelScope.launch {
            _reportState.value = ReportState.Loading
            when (val result = repository.submitCargoReport(
                vehicleId, destinationPoiId, cargoType, isHazardous, hazardClass, weight, description
            )) {
                is NetworkResult.Success -> {
                    _reports.value = listOf(result.data) + _reports.value
                    _reportState.value = ReportState.SubmitSuccess
                }
                is NetworkResult.Error -> {
                    _reportState.value = ReportState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _reportState.value = ReportState.Error(result.throwable.message ?: "提交报备失败")
                }
                else -> {}
            }
        }
    }

    // ==================== 行程管理方法 (个人模式) ====================

    fun fetchTrips() {
        viewModelScope.launch {
            _tripState.value = TripState.Loading
            when (val result = repository.getTrips()) {
                is NetworkResult.Success -> {
                    _trips.value = result.data
                    _tripState.value = TripState.Success
                }
                is NetworkResult.Error -> {
                    _tripState.value = TripState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _tripState.value = TripState.Error(result.throwable.message ?: "获取行程失败")
                }
                else -> {}
            }
        }
    }

    fun createTrip(tripType: String, tripNumber: String, tripDate: String) {
        viewModelScope.launch {
            _tripState.value = TripState.Loading
            when (val result = repository.createTrip(tripType, tripNumber, tripDate)) {
                is NetworkResult.Success -> {
                    _trips.value = listOf(result.data) + _trips.value
                    _tripState.value = TripState.CreateSuccess
                }
                is NetworkResult.Error -> {
                    _tripState.value = TripState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _tripState.value = TripState.Error(result.throwable.message ?: "创建行程失败")
                }
                else -> {}
            }
        }
    }

    fun deleteTrip(tripId: Int) {
        viewModelScope.launch {
            _tripState.value = TripState.Loading
            when (val result = repository.deleteTrip(tripId)) {
                is NetworkResult.Success -> {
                    _trips.value = _trips.value.filterNot { it.id == tripId }
                    _tripState.value = TripState.Success
                }
                is NetworkResult.Error -> {
                    _tripState.value = TripState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _tripState.value = TripState.Error(result.throwable.message ?: "删除行程失败")
                }
                else -> {}
            }
        }
    }

    // ==================== 位置共享方法 ====================

    fun createLocationShare(tripId: Int, expiresInHours: Int = 24) {
        viewModelScope.launch {
            when (val result = repository.createLocationShare(tripId, expiresInHours)) {
                is NetworkResult.Success -> {
                    _locationShare.value = result.data
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Create location share error: ${result.message}")
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Create location share exception: ${result.throwable.message}")
                }
                else -> {}
            }
        }
    }

    fun stopLocationShare(tripId: Int) {
        viewModelScope.launch {
            when (val result = repository.stopLocationShare(tripId)) {
                is NetworkResult.Success -> {
                    _locationShare.value = null
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Stop location share error: ${result.message}")
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Stop location share exception: ${result.throwable.message}")
                }
                else -> {}
            }
        }
    }

    // ==================== 导航方法 ====================

    fun planRoute(startPoiId: String, endPoiId: String, vehicleId: Int? = null) {
        viewModelScope.launch {
            when (val result = repository.planRoute(startPoiId, endPoiId, vehicleId)) {
                is NetworkResult.Success -> {
                    _routeResult.value = result.data
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Plan route error: ${result.message}")
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Plan route exception: ${result.throwable.message}")
                }
                else -> {}
            }
        }
    }

    // ==================== AI助手方法 ====================

    fun askAI(query: String, role: String? = null) {
        viewModelScope.launch {
            _aiState.value = AIState.Loading
            val userRole = role ?: if (isProfessionalMode()) "truck" else "car"

            when (val result = repository.askAI(query, userRole)) {
                is NetworkResult.Success -> {
                    _aiResponse.value = result.data
                    _aiState.value = AIState.Success
                }
                is NetworkResult.Error -> {
                    _aiState.value = AIState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _aiState.value = AIState.Error(result.throwable.message ?: "AI请求失败")
                }
                else -> {}
            }
        }
    }

    // ==================== 拥堵预测方法 ====================

    fun predictCongestion(roadId: String? = null, lat: Double? = null, lng: Double? = null, hours: Int = 5) {
        viewModelScope.launch {
            _congestionState.value = CongestionState.Loading
            when (val result = repository.predictCongestion(roadId, lat, lng, hours = hours)) {
                is NetworkResult.Success -> {
                    _congestionData.value = result.data
                    _congestionState.value = CongestionState.Success
                }
                is NetworkResult.Error -> {
                    _congestionState.value = CongestionState.Error(result.message)
                }
                is NetworkResult.Exception -> {
                    _congestionState.value = CongestionState.Error(result.throwable.message ?: "拥堵预测失败")
                }
                else -> {}
            }
        }
    }

    // ==================== 闸口排队方法 ====================

    fun fetchGateQueues() {
        viewModelScope.launch {
            when (val result = repository.getGateQueues()) {
                is NetworkResult.Success -> {
                    _gateQueues.value = result.data.queues ?: emptyMap()
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Fetch gate queues error: ${result.message}")
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Fetch gate queues exception: ${result.throwable.message}")
                }
                else -> {}
            }
        }
    }

    // ==================== POI方法 ====================

    fun fetchPOIs(type: String? = null) {
        viewModelScope.launch {
            val role = if (isProfessionalMode()) "truck" else "car"
            when (val result = repository.getPOIs(role, type)) {
                is NetworkResult.Success -> {
                    _pois.value = result.data
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Fetch POIs error: ${result.message}")
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Fetch POIs exception: ${result.throwable.message}")
                }
                else -> {}
            }
        }
    }

    fun fetchNearbyPOIs(lat: Double, lng: Double, radius: Int = 1000, type: String? = null) {
        viewModelScope.launch {
            when (val result = repository.getNearbyPOIs(lat, lng, radius, type)) {
                is NetworkResult.Success -> {
                    _pois.value = result.data
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Fetch nearby POIs error: ${result.message}")
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Fetch nearby POIs exception: ${result.throwable.message}")
                }
                else -> {}
            }
        }
    }

    // ==================== 停车场方法 ====================

    fun fetchNearbyParking(lat: Double, lng: Double, radius: Int = 2000) {
        viewModelScope.launch {
            when (val result = repository.getNearbyParking(lat, lng, radius)) {
                is NetworkResult.Success -> {
                    _parkingList.value = result.data
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Fetch parking error: ${result.message}")
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Fetch parking exception: ${result.throwable.message}")
                }
                else -> {}
            }
        }
    }

    fun predictParking(lotId: String, hours: Int = 3) {
        viewModelScope.launch {
            when (val result = repository.predictParking(lotId, hours)) {
                is NetworkResult.Success -> {
                    _parkingPrediction.value = result.data
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Predict parking error: ${result.message}")
                }
                is NetworkResult.Exception -> {
                    Log.e(TAG, "Predict parking exception: ${result.throwable.message}")
                }
                else -> {}
            }
        }
    }

    // ==================== 重置状态 ====================

    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    fun resetVehicleState() {
        _vehicleState.value = VehicleState.Idle
    }

    fun resetReportState() {
        _reportState.value = ReportState.Idle
    }

    fun resetTripState() {
        _tripState.value = TripState.Idle
    }

    fun resetAIState() {
        _aiState.value = AIState.Idle
    }

    fun resetCongestionState() {
        _congestionState.value = CongestionState.Idle
    }
}

// ==================== 状态类定义 ====================

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object RegisterSuccess : AuthState()
    data class LoginSuccess(val targetHome: String) : AuthState()
    object CodeSent : AuthState()
    object ResetPasswordSuccess : AuthState()
    data class Error(val message: String) : AuthState()
    data class RoleMismatch(
        val selectedRole: String,
        val actualRole: String,
        val targetHome: String
    ) : AuthState()
}

sealed class VehicleState {
    object Idle : VehicleState()
    object Loading : VehicleState()
    object Success : VehicleState()
    object BindSuccess : VehicleState()
    data class Error(val message: String) : VehicleState()
}

sealed class ReportState {
    object Idle : ReportState()
    object Loading : ReportState()
    object Success : ReportState()
    object SubmitSuccess : ReportState()
    data class Error(val message: String) : ReportState()
}

sealed class TripState {
    object Idle : TripState()
    object Loading : TripState()
    object Success : TripState()
    object CreateSuccess : TripState()
    data class Error(val message: String) : TripState()
}

sealed class AIState {
    object Idle : AIState()
    object Loading : AIState()
    object Success : AIState()
    data class Error(val message: String) : AIState()
}

sealed class CongestionState {
    object Idle : CongestionState()
    object Loading : CongestionState()
    object Success : CongestionState()
    data class Error(val message: String) : CongestionState()
}