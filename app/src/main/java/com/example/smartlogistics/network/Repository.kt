package com.example.smartlogistics.network

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * 数据仓库
 * 封装所有网络请求逻辑
 */
class Repository(private val context: Context) {
    
    private val api: ApiService = RetrofitClient.apiService
    private val tokenManager = TokenManager(context)
    
    companion object {
        // ★★★ 本地Mock开关 ★★★
        // true = 不走网络，用本地假数据（Mock Server挂了时用）
        // false = 正常走网络请求
        var USE_LOCAL_MOCK = true
    }
    
    // ==================== 认证相关 ====================
    
    suspend fun register(phoneNumber: String, password: String, role: String): NetworkResult<RegisterResponse> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(RegisterResponse(
                id = 1,
                username = phoneNumber,
                role = role,
                message = "注册成功"
            ))
        }
        
        return try {
            val response = api.register(RegisterRequest(phoneNumber, password, role))
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("注册失败: ${response.message()}")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }

    suspend fun login(phoneNumber: String, password: String): NetworkResult<LoginResponse> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            val fakeToken = "local_mock_token_${System.currentTimeMillis()}"
            tokenManager.saveToken(fakeToken)
            tokenManager.saveUserName(phoneNumber)
            val role = if (phoneNumber.startsWith("138")) "professional" else "personal"
            tokenManager.saveUserRole(role)
            return NetworkResult.Success(LoginResponse(
                accessToken = fakeToken,
                tokenType = "bearer",
                userInfo = UserInfo(
                    id = 1,
                    userId = 1,
                    userName = "测试用户",
                    username = phoneNumber,
                    role = role,
                    phone = phoneNumber,
                    phoneNumber = phoneNumber
                )
            ))
        }
        
        return try {
            val response = api.login(
                username = phoneNumber,
                password = password,
                grantType = "password",
                scope = "",
                clientId = "string",
                clientSecret = "string"
            )
            if (response.isSuccessful && response.body() != null) {
                val loginResponse = response.body()!!
                // 保存Token
                tokenManager.saveToken(loginResponse.accessToken)
                tokenManager.saveUserName(phoneNumber)
                loginResponse.userInfo?.role?.let { tokenManager.saveUserRole(it) }
                NetworkResult.Success(loginResponse)
            } else {
                NetworkResult.Error("登录失败: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            NetworkResult.Exception(e)
        }
    }
    
    fun logout() {
        tokenManager.clearToken()
    }
    
    fun isLoggedIn(): Boolean = tokenManager.getToken() != null
    
    fun getUserName(): String = tokenManager.getUserName() ?: "用户"
    
    fun isProfessionalMode(): Boolean = tokenManager.getUserRole() == "professional"
    
    fun getSavedUserRole(): String = tokenManager.getUserRole()
    
    suspend fun getCurrentUser(): NetworkResult<UserInfo> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(UserInfo(
                id = 1,
                userId = 1,
                userName = "测试用户",
                username = tokenManager.getUserName(),
                role = tokenManager.getUserRole(),
                phone = tokenManager.getUserName(),
                phoneNumber = tokenManager.getUserName()
            ))
        }
        
        return try {
            val response = api.getCurrentUser()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取用户信息失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 车辆管理 ====================
    
    suspend fun getVehicles(): NetworkResult<List<Vehicle>> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(listOf(
                Vehicle(
                    id = 1,
                    plateNumber = "湘A12345",
                    vehicleType = "truck",
                    brand = "东风"
                )
            ))
        }
        
        return try {
            val response = api.getVehicles()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取车辆列表失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    suspend fun bindVehicle(
        plateNumber: String,
        vehicleType: String,
        heightM: Double? = null,
        weightT: Double? = null,
        axleCount: Int? = null
    ): NetworkResult<Vehicle> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(Vehicle(
                id = System.currentTimeMillis().toInt(),
                plateNumber = plateNumber,
                vehicleType = vehicleType,
                specs = VehicleSpecs(heightM, weightT, axleCount)
            ))
        }
        
        return try {
            val specs = if (heightM != null || weightT != null || axleCount != null) {
                VehicleSpecs(heightM, weightT, axleCount)
            } else null
            
            val request = BindVehicleRequest(plateNumber, vehicleType, specs = specs)
            val response = api.createVehicle(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("绑定车辆失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    suspend fun unbindVehicle(vehicleId: String): Boolean {
        if (USE_LOCAL_MOCK) return true
        
        return try {
            val response = api.deleteVehicle(vehicleId.toInt())
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    // ==================== 货物报备 (专业模式) ====================
    
    suspend fun getCargoReports(page: Int = 1): NetworkResult<ReportListResponse> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(ReportListResponse(
                items = emptyList(),
                total = 0,
                page = page,
                pageSize = 20
            ))
        }
        
        return try {
            val response = api.getReports(page)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取报备记录失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    suspend fun submitCargoReport(
        vehicleId: String,
        destinationId: String,
        cargoType: String,
        isHazardous: Boolean,
        hazardClass: String? = null,
        weight: Double? = null,
        description: String? = null
    ): NetworkResult<CargoReport> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            val cargoInfo = CargoInfo(cargoType, isHazardous, hazardClass, weight, description)
            return NetworkResult.Success(CargoReport(
                id = System.currentTimeMillis().toInt(),
                vehicleId = vehicleId.toInt(),
                destinationPoiId = destinationId,
                cargoInfo = cargoInfo,
                status = "pending"
            ))
        }
        
        return try {
            val cargoInfo = CargoInfo(
                cargoType = cargoType,
                isHazardous = isHazardous,
                hazardClass = hazardClass,
                weight = weight,
                description = description
            )
            val request = SubmitReportRequest(
                vehicleId = vehicleId.toInt(),
                destinationPoiId = destinationId,
                cargoInfo = cargoInfo
            )
            val response = api.createReport(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("提交报备失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 行程管理 (个人模式) ====================
    
    suspend fun getTrips(): NetworkResult<List<Trip>> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(emptyList())
        }
        
        return try {
            val response = api.getTrips()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取行程失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    suspend fun createTrip(tripType: String, tripNumber: String, tripDate: String): NetworkResult<Trip> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(Trip(
                id = System.currentTimeMillis().toInt(),
                tripType = tripType,
                tripNumber = tripNumber,
                tripDate = tripDate,
                status = "scheduled"
            ))
        }
        
        return try {
            val request = CreateTripRequest(tripType, tripNumber, tripDate)
            val response = api.createTrip(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("创建行程失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== POI查询 ====================
    
    suspend fun getPOIs(type: String? = null, mode: String? = null): NetworkResult<List<POI>> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(listOf(
                POI("1", "中央停车场", "parking", "personal", 28.225, 112.950),
                POI("2", "T1航站楼", "terminal", "personal", 28.227, 112.953)
            ))
        }
        
        return try {
            val response = api.getPois(type, mode)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取POI失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 停车管理 ====================
    
    suspend fun startParking(vehicleId: Int, parkingLotId: String): NetworkResult<ParkingSession> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(ParkingSession(
                id = System.currentTimeMillis().toInt(),
                vehicleId = vehicleId,
                parkingLotId = parkingLotId,
                status = "active"
            ))
        }
        
        return try {
            val request = StartParkingRequest(vehicleId, parkingLotId)
            val response = api.startParking(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("开始停车失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    suspend fun getParkingHistory(): NetworkResult<List<ParkingSession>> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(emptyList())
        }
        
        return try {
            val response = api.getParkingHistory()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取停车记录失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 导航路线 ====================
    
    suspend fun getRoute(startPoiId: String, endPoiId: String, vehicleId: Int? = null): NetworkResult<RouteResult> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(RouteResult(
                path = listOf(startPoiId, "waypoint1", endPoiId),
                totalCost = 100f,
                totalDistance = 1500.0,
                estimatedTime = 300
            ))
        }
        
        return try {
            val request = RouteRequest(startPoiId, endPoiId, vehicleId)
            val response = api.planRoute(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("路径规划失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== AI智能问答 (新增) ====================
    
    suspend fun askAI(query: String, role: String? = null): NetworkResult<AskResponse> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(AskResponse(
                answer = "您好！我是智慧物流助手，可以帮您规划路线、查询路况、预测拥堵等。请问有什么可以帮您的？",
                role = role
            ))
        }
        
        return try {
            val request = AskRequest(query, role)
            val response = api.askAI(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("AI请求失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 拥堵预测 (新增) ====================
    
    suspend fun predictCongestion(roadId: String, hours: Int = 2): NetworkResult<CongestionResponse> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(CongestionResponse(
                status = "success",
                roadId = roadId,
                forecast = listOf(
                    CongestionForecast("14:00", 1.2f, "Free Flow"),
                    CongestionForecast("15:00", 1.5f, "Slow"),
                    CongestionForecast("16:00", 2.0f, "Congested")
                )
            ))
        }
        
        return try {
            val response = api.predictCongestion(roadId, hours)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("拥堵预测失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 视觉检测 (新增) ====================
    
    suspend fun analyzeVehicleImage(imageFile: File): NetworkResult<VisionResponse> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(VisionResponse(
                status = "success",
                vehicleType = "truck",
                vehicleTypeCn = "货车",
                vehicleConf = 0.95f,
                hasPlate = true,
                plates = listOf(PlateDetection(listOf(100, 200, 300, 250), 0.98f, "湘A12345"))
            ))
        }
        
        return try {
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)
            val response = api.analyzeVehicle(body)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("图像分析失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 路况查询 ====================
    
    suspend fun getTrafficData(): NetworkResult<TrafficData> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(TrafficData(
                timestamp = System.currentTimeMillis().toString(),
                congestionLevel = "moderate"
            ))
        }
        
        return try {
            val response = api.getTrafficStatus()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取路况失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    suspend fun getRoadTraffic(): NetworkResult<List<RoadTraffic>> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(listOf(
                RoadTraffic("road_1", "机场高速", 1.5f, "Slow"),
                RoadTraffic("road_2", "枢纽大道", 1.0f, "Free Flow")
            ))
        }
        
        return try {
            val response = api.getRoadTraffic()
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("获取道路路况失败")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
    
    // ==================== 停车预测 ====================
    
    suspend fun getParkingPrediction(arrivalTime: String): NetworkResult<List<ParkingPrediction>> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(listOf(
                ParkingPrediction("parking_1", "中央停车场", 50, 200, 45, 5)
            ))
        }
        
        // TODO: 实现停车预测API调用
        return NetworkResult.Success(emptyList())
    }

    // ==================== 智能停车助手 ====================
    
    suspend fun registerParkingPhoto(imageFile: File, floor: String? = null, zone: String? = null): NetworkResult<ParkingRegisterResponse> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(ParkingRegisterResponse(
                success = true,
                sessionId = "mock_session_${System.currentTimeMillis()}",
                detectedInfo = ParkingDetectedInfo(
                    parkingLot = "中央停车场",
                    floor = floor ?: "B1",
                    zone = zone ?: "A区",
                    landmarks = listOf("电梯口", "柱子A12")
                ),
                message = "停车位置已记录"
            ))
        }
        
        return try {
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)
            val floorPart = floor?.toRequestBody("text/plain".toMediaTypeOrNull())
            val zonePart = zone?.toRequestBody("text/plain".toMediaTypeOrNull())
            val response = api.registerParking(body, floorPart, zonePart)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("停车记录失败: ${response.message()}")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }

    suspend fun findParkingByPhoto(imageFile: File): NetworkResult<ParkingFindResponse> {
        // 本地Mock模式
        if (USE_LOCAL_MOCK) {
            return NetworkResult.Success(ParkingFindResponse(
                success = true,
                matchConfidence = 0.92f,
                location = ParkingLocationInfo(
                    parkingLot = "中央停车场",
                    floor = "B1",
                    zone = "A区",
                    photoUrl = null
                ),
                navigation = ParkingNavigation(
                    distance = "约50米",
                    directions = listOf("前方直走20米", "左转进入A区", "车辆在第3排"),
                    latitude = 28.225,
                    longitude = 112.950
                ),
                message = "找到您的车辆"
            ))
        }
        
        return try {
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)
            val response = api.findParking(body)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error("寻车失败: ${response.message()}")
            }
        } catch (e: Exception) {
            NetworkResult.Exception(e)
        }
    }
}

// ==================== 网络结果封装 ====================

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String) : NetworkResult<Nothing>()
    data class Exception(val throwable: Throwable) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}
