package com.example.smartlogistics.network

import com.google.gson.annotations.SerializedName

// ==================== 认证相关 ====================

data class RegisterRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    val password: String,
    val role: String  // "professional" 或 "personal"
)

data class RegisterResponse(
    val id: Int,
    @SerializedName("phone_number") val phoneNumber: String,
    val role: String,
    val nickname: String? = null,
    @SerializedName("device_id") val deviceId: String? = null
)

// OAuth2表单登录
data class LoginRequest(
    val username: String,  // 手机号
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
)

data class UserInfo(
    val id: Int,
    @SerializedName("phone_number") val phoneNumber: String,
    val role: String,
    val nickname: String? = null,
    @SerializedName("device_id") val deviceId: String? = null
)

// 发送验证码请求
data class SendCodeRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    val purpose: String = "reset_password"  // reset_password
)

// 发送验证码响应
data class SendCodeResponse(
    val code: Int = 0,
    val message: String,
    val data: Any? = null
)

// 重置密码请求
data class ResetPasswordRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    val code: String,
    @SerializedName("new_password") val newPassword: String
)

// 重置密码响应
data class ResetPasswordResponse(
    val code: Int = 0,
    val message: String,
    val data: Any? = null
)

// 指纹登录请求
data class BiometricLoginRequest(
    @SerializedName("device_id") val deviceId: String,
    val signature: String? = null
)

// 设备绑定请求
data class BiometricBindRequest(
    @SerializedName("device_id") val deviceId: String
)

// ==================== 车辆相关 ====================

data class Vehicle(
    val id: Int,
    @SerializedName("owner_id") val ownerId: Int,
    @SerializedName("plate_number") val plateNumber: String,
    @SerializedName("vehicle_type") val vehicleType: String,
    val brand: String,
    val specs: VehicleSpecs? = null,
    @SerializedName("is_default") val isDefault: Boolean = false
) {
    // 兼容旧代码中的 vehicleId 引用
    val vehicleId: Int get() = id
}

data class VehicleSpecs(
    @SerializedName("height_m") val heightM: Double? = null,
    @SerializedName("weight_t") val weightT: Double? = null,
    @SerializedName("axle_count") val axleCount: Int? = null,
    val length: Double? = null,
    val width: Double? = null
)

data class BindVehicleRequest(
    @SerializedName("plate_number") val plateNumber: String,
    @SerializedName("vehicle_type") val vehicleType: String,
    val brand: String,
    val specs: VehicleSpecs? = null
)

// ==================== 货物报备相关 (专业模式) ====================

data class CargoReport(
    val id: Int,
    @SerializedName("vehicle_id") val vehicleId: Int,
    @SerializedName("destination_poi_id") val destinationPoiId: String,
    @SerializedName("cargo_info") val cargoInfo: CargoInfo,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    val eta: String? = null
)

data class CargoInfo(
    @SerializedName("cargo_type") val cargoType: String,
    @SerializedName("is_hazardous") val isHazardous: Boolean = false,
    @SerializedName("hazard_class") val hazardClass: String? = null,
    val weight: Double? = null,
    val description: String? = null
)

data class SubmitReportRequest(
    @SerializedName("vehicle_id") val vehicleId: Int,
    @SerializedName("destination_poi_id") val destinationPoiId: String,
    @SerializedName("cargo_info") val cargoInfo: CargoInfo
)

data class ReportListResponse(
    val items: List<CargoReport>,
    val total: Int,
    val page: Int,
    @SerializedName("page_size") val pageSize: Int
)

// 语音报备解析请求
data class ReportParseRequest(
    val text: String
)

// 语音报备解析响应
data class ReportParseResponse(
    val code: Int = 0,
    val message: String = "success",
    val data: ReportParseData? = null
)

data class ReportParseData(
    @SerializedName("cargo_type") val cargoType: String? = null,
    val weight: Double? = null,
    @SerializedName("weight_unit") val weightUnit: String? = null,
    val origin: String? = null,
    val destination: String? = null,
    val eta: String? = null,
    @SerializedName("is_hazmat") val isHazmat: Boolean = false
)

// ==================== 行程相关 (个人模式) ====================

data class Trip(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("trip_type") val tripType: String,  // "flight" 或 "train"
    @SerializedName("trip_number") val tripNumber: String,
    @SerializedName("trip_date") val tripDate: String,
    val status: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CreateTripRequest(
    @SerializedName("trip_type") val tripType: String,
    @SerializedName("trip_number") val tripNumber: String,
    @SerializedName("trip_date") val tripDate: String
)

// 行程详情响应
data class TripDetailResponse(
    val code: Int = 0,
    val message: String = "success",
    val data: TripDetailData? = null
)

data class TripDetailData(
    @SerializedName("trip_id") val tripId: Int,
    @SerializedName("trip_type") val tripType: String,
    @SerializedName("trip_number") val tripNumber: String,
    @SerializedName("trip_date") val tripDate: String,
    val status: String,
    @SerializedName("departure_time") val departureTime: String? = null,
    @SerializedName("arrival_time") val arrivalTime: String? = null,
    @SerializedName("departure_airport") val departureAirport: String? = null,
    @SerializedName("arrival_airport") val arrivalAirport: String? = null,
    val gate: String? = null,
    val terminal: String? = null
)

// ==================== POI相关 ====================

data class PoisResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: PoisData? = null
)

data class PoisData(
    val pois: List<POI>? = null
)

data class POI(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val type: String? = null,
    val mode: String? = null,  // car/truck
    val address: String? = null,
    val description: String? = null
)

// ==================== 停车相关 ====================

data class ParkingSession(
    val id: Int,
    @SerializedName("vehicle_id") val vehicleId: Int,
    @SerializedName("parking_lot_id") val parkingLotId: String,
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("end_time") val endTime: String? = null,
    val status: String? = null,
    @SerializedName("photo_url") val photoUrl: String? = null
)

data class StartParkingRequest(
    @SerializedName("vehicle_id") val vehicleId: Int,
    @SerializedName("parking_lot_id") val parkingLotId: String
)

// 停车场列表响应
data class ParkingListResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: ParkingData? = null
)

data class ParkingData(
    val parkings: List<ParkingInfo>? = null
)

data class ParkingInfo(
    val id: String,
    val name: String,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerializedName("total_spots") val totalSpots: Int = 0,
    @SerializedName("available_spots") val availableSpots: Int = 0,
    @SerializedName("predicted_available") val predictedAvailable: Int? = null,
    val price: String? = null,
    val distance: Int = 0
)

// 停车预测响应
data class ParkingPredictResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: ParkingPredictData? = null
)

data class ParkingPredictData(
    @SerializedName("lot_id") val lotId: String,
    @SerializedName("lot_name") val lotName: String,
    @SerializedName("current_available") val currentAvailable: Int,
    val predictions: List<ParkingTimePrediction>? = null
)

data class ParkingTimePrediction(
    val time: String,
    @SerializedName("predicted_available") val predictedAvailable: Int
)

data class ParkingPrediction(
    @SerializedName("parking_lot_id") val parkingLotId: String,
    val name: String,
    @SerializedName("available_spots") val availableSpots: Int,
    @SerializedName("total_spots") val totalSpots: Int,
    @SerializedName("predicted_availability") val predictedAvailability: Int? = null,
    @SerializedName("wait_time_minutes") val waitTimeMinutes: Int? = null
)

// ==================== 导航路线相关 ====================

data class RouteRequest(
    @SerializedName("start_poi_id") val startPoiId: String,
    @SerializedName("end_poi_id") val endPoiId: String,
    @SerializedName("vehicle_id") val vehicleId: Int? = null
)

data class RouteResponse(
    val path: List<String>,
    @SerializedName("total_cost") val totalCost: Float? = null,
    @SerializedName("constraints_applied") val constraintsApplied: List<String>? = null,
    @SerializedName("congestion_info") val congestionInfo: Map<String, Int>? = null
)

// 兼容旧代码
data class RouteResult(
    val path: List<String>,
    @SerializedName("total_cost") val totalCost: Float? = null,
    @SerializedName("constraints_applied") val constraintsApplied: List<String>? = null,
    @SerializedName("total_distance") val totalDistance: Double? = null,
    @SerializedName("estimated_time") val estimatedTime: Int? = null
)

data class Waypoint(
    val lat: Double,
    val lng: Double,
    val name: String? = null
)

// ==================== AI问答相关 ====================

data class AskRequest(
    val query: String,
    val role: String? = null  // truck/car
)

data class AskResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: AskData? = null
)

data class AskData(
    val answer: String,
    val intent: String? = null,  // navigation, query, report
    val entities: AskEntities? = null
)

data class AskEntities(
    val destination: String? = null,
    @SerializedName("poi_id") val poiId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null
)

// ==================== 拥堵预测相关 ====================

data class CongestionResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: CongestionData? = null
)

data class CongestionData(
    @SerializedName("road_id") val roadId: String? = null,
    @SerializedName("road_name") val roadName: String? = null,
    @SerializedName("current_tti") val currentTti: Float = 1.0f,
    val predictions: List<CongestionPrediction>? = null,
    val suggestion: String? = null
)

data class CongestionPrediction(
    val time: String,
    val tti: Float
)

// ==================== 视觉检测相关 ====================

data class VisionResponse(
    val status: String,
    @SerializedName("license_plate") val licensePlate: LicensePlateInfo? = null,
    @SerializedName("vehicle_type") val vehicleType: VehicleTypeInfo? = null,
    val hazmat: HazmatInfo? = null
)

data class LicensePlateInfo(
    val detected: Boolean = false,
    val text: String? = null
)

data class VehicleTypeInfo(
    @SerializedName("class") val vehicleClass: String? = null,
    val confidence: Float? = null
)

data class HazmatInfo(
    val detected: Boolean = false,
    val labels: List<String>? = null
)

// ==================== 路况相关 ====================

data class TrafficData(
    val timestamp: String? = null,
    val roads: List<RoadTraffic>? = null,
    @SerializedName("congestion_level") val congestionLevel: String? = null
)

data class RoadTraffic(
    @SerializedName("road_id") val roadId: String,
    val name: String? = null,
    val tti: Float,
    val status: String  // "Free Flow", "Slow", "Congested"
)

data class GateQueuesResponse(
    val queues: Map<String, Int>? = null
)

// ==================== 历史记录相关 ====================

data class TripHistoryResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: TripHistoryData? = null
)

data class TripHistoryData(
    val total: Int = 0,
    val trips: List<TripHistory>? = null
)

data class TripHistory(
    val id: Int,
    @SerializedName("trip_type") val tripType: String,
    @SerializedName("trip_number") val tripNumber: String,
    @SerializedName("trip_date") val tripDate: String,
    val status: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

// ==================== 通用响应 ====================

data class ApiResponse<T>(
    val status: String,
    val data: T? = null,
    val message: String? = null
)

data class ErrorResponse(
    val detail: Any? = null,  // 可能是String或Object
    val message: String? = null
)

// ==================== 智能停车助手模型 ====================

data class ParkingRegisterResponse(
    val success: Boolean,
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("detected_info") val detectedInfo: ParkingDetectedInfo?,
    val message: String?
)

data class ParkingDetectedInfo(
    @SerializedName("parking_lot") val parkingLot: String?,
    val floor: String?,
    val zone: String?,
    val landmarks: List<String>?
)

data class ParkingFindResponse(
    val success: Boolean,
    @SerializedName("match_confidence") val matchConfidence: Float?,
    val location: ParkingLocationInfo?,
    val navigation: ParkingNavigation?,
    val message: String?
)

data class ParkingLocationInfo(
    @SerializedName("parking_lot") val parkingLot: String?,
    val floor: String?,
    val zone: String?,
    @SerializedName("photo_url") val photoUrl: String?
)

data class ParkingNavigation(
    val distance: String?,
    val directions: List<String>?,
    val latitude: Double?,
    val longitude: Double?
)

// ==================== 位置共享相关 ====================

data class LocationShareResponse(
    val id: Int,
    @SerializedName("share_id") val shareId: String,
    @SerializedName("trip_id") val tripId: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("ws_url") val wsUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("expired_at") val expiredAt: String? = null
)

data class LocationShareDetail(
    @SerializedName("share_id") val shareId: String,
    @SerializedName("trip_id") val tripId: Int? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("owner_name") val ownerName: String? = null,
    @SerializedName("trip_info") val tripInfo: Trip? = null,
    @SerializedName("ws_url") val wsUrl: String? = null,
    @SerializedName("expired_at") val expiredAt: String? = null
)

data class LocationMessage(
    val type: String = "location",
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val heading: Float? = null,
    val timestamp: String? = null
)