package com.example.smartlogistics.network

import com.google.gson.annotations.SerializedName

// ==================== 认证相关 ====================

data class RegisterRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    val password: String,
    val role: String,  // "professional" 或 "personal"
    val nickname: String? = null
)

data class RegisterResponse(
    val id: Int,
    val username: String,
    val role: String,
    val message: String?
)

// 注意：后端login使用username字段
data class LoginRequest(
    val username: String,  // 后端用username
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user_info") val userInfo: UserInfo? = null
)

data class UserInfo(
    val id: Int? = null,
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("user_name") val userName: String? = null,
    val username: String? = null,
    val role: String = "personal",
    val phone: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null
)

// ==================== 车辆相关 ====================

data class Vehicle(
    val id: Int? = null,
    @SerializedName("vehicle_id") val vehicleId: String? = null,
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("plate_number") val plateNumber: String,
    @SerializedName("vehicle_type") val vehicleType: String,
    val brand: String? = null,
    val specs: VehicleSpecs? = null,
    // 兼容旧字段
    @SerializedName("height_m") val heightM: Double? = null,
    @SerializedName("weight_t") val weightT: Double? = null,
    @SerializedName("axle_count") val axleCount: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

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
    val brand: String? = null,
    val specs: VehicleSpecs? = null
)

// ==================== 货物报备相关 (专业模式) ====================

data class CargoReport(
    val id: Int? = null,
    @SerializedName("report_id") val reportId: String? = null,
    @SerializedName("vehicle_id") val vehicleId: Int,
    @SerializedName("destination_poi_id") val destinationPoiId: String,
    @SerializedName("cargo_info") val cargoInfo: CargoInfo,
    val status: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("eta") val estimatedArrival: String? = null
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
    @SerializedName("cargo_type") val cargoType: String,
    val weight: Double? = null,
    val origin: String? = null,
    val destination: String? = null,
    val eta: String? = null,
    // 兼容旧字段
    @SerializedName("destination_poi_id") val destinationPoiId: String? = null,
    @SerializedName("cargo_info") val cargoInfo: CargoInfo? = null
)

data class ReportListResponse(
    val items: List<CargoReport>,
    val total: Int,
    val page: Int,
    @SerializedName("page_size") val pageSize: Int
)

// ==================== 行程相关 (个人模式) ====================

data class Trip(
    val id: Int? = null,
    @SerializedName("trip_id") val tripId: String? = null,
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("trip_type") val tripType: String,  // "flight" 或 "train"
    @SerializedName("trip_number") val tripNumber: String,  // 航班号/车次
    @SerializedName("trip_date") val tripDate: String,
    val status: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CreateTripRequest(
    @SerializedName("trip_type") val tripType: String,
    @SerializedName("trip_number") val tripNumber: String,
    @SerializedName("trip_date") val tripDate: String
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
    val lat: Double? = null,
    val lng: Double? = null,
    val type: String? = null,      // 停车场、航站楼、餐厅等
    val address: String? = null,
    // 兼容旧字段
    @SerializedName("poi_type") val poiType: String? = null,
    val mode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null
)

// ==================== 停车相关 ====================

data class ParkingSession(
    val id: Int? = null,
    @SerializedName("session_id") val sessionId: String? = null,
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

// ==================== 导航路线相关 ====================

data class RouteRequest(
    val start: LatLngPoint,
    val end: LatLngPoint,
    @SerializedName("vehicle_id") val vehicleId: String? = null,
    val role: String = "car"  // car=私家车, truck=货车
)

data class LatLngPoint(
    val lat: Double,
    val lng: Double
)

data class RouteResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: RouteData? = null
)

data class RouteData(
    val routes: List<RouteInfo>? = null,
    @SerializedName("algorithm_info") val algorithmInfo: AlgorithmInfo? = null
)

data class RouteInfo(
    val distance: Int = 0,           // 距离（米）
    val duration: Int = 0,           // 时间（秒）
    @SerializedName("toll_cost") val tollCost: Int = 0,
    val strategy: String? = null,
    val polyline: String? = null,    // "lng,lat;lng,lat;..."
    val steps: List<RouteStep>? = null
)

data class RouteStep(
    val instruction: String? = null,
    val distance: Int = 0,
    val duration: Int = 0,
    val polyline: String? = null
)

data class AlgorithmInfo(
    val path: List<String>? = null,
    @SerializedName("total_cost") val totalCost: Float? = null,
    @SerializedName("constraints_applied") val constraintsApplied: List<String>? = null,
    @SerializedName("congestion_info") val congestionInfo: Map<String, Int>? = null
)

// 兼容旧代码
data class RouteResult(
    val path: List<String>,
    @SerializedName("total_cost") val totalCost: Float? = null,
    @SerializedName("constraints_applied") val constraintsApplied: List<String>? = null,
    @SerializedName("congestion_info") val congestionInfo: Map<String, Int>? = null,
    @SerializedName("total_distance") val totalDistance: Double? = null,
    @SerializedName("estimated_time") val estimatedTime: Int? = null,
    val waypoints: List<Waypoint>? = null
)

data class Waypoint(
    val lat: Double,
    val lng: Double,
    val name: String? = null
)

// ==================== AI问答相关 (新增) ====================

data class AskRequest(
    val query: String,
    val role: String? = null  // 可选，帮助AI识别模式: truck/car
)

data class AskResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: AskData? = null,
    // 兼容旧格式（直接返回）
    val answer: String? = null,
    val intent: AskIntent? = null
)

data class AskData(
    val answer: String? = null,
    val intent: String? = null,  // navigation, query, report
    val entities: AskEntities? = null
)

data class AskEntities(
    val destination: String? = null,
    val lat: Double? = null,
    val lng: Double? = null
)

data class AskIntent(
    val role: String? = null,
    val confidence: Float = 0f,
    @SerializedName("intent_type") val intentType: String? = null,
    @SerializedName("matched_keywords") val matchedKeywords: List<String>? = null,
    val destination: String? = null,
    @SerializedName("poi_id") val poiId: String? = null
)

// ==================== 拥堵预测相关 (新增) ====================

data class CongestionResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: CongestionData? = null
)

data class CongestionData(
    @SerializedName("road_name") val roadName: String? = null,
    @SerializedName("current_tti") val currentTti: Float = 1.0f,
    val predictions: List<CongestionPrediction>? = null,
    val suggestion: String? = null
)

data class CongestionPrediction(
    val time: String,
    val tti: Float
)

// 兼容旧代码
data class CongestionForecast(
    val time: String,
    @SerializedName("predicted_tti") val predictedTti: Float,
    val status: String  // "Free Flow" 或 "Congested"
)

// ==================== 视觉检测相关 (新增) ====================

data class VisionResponse(
    val status: String,
    @SerializedName("vehicle_type") val vehicleType: String? = null,
    @SerializedName("vehicle_type_cn") val vehicleTypeCn: String? = null,
    @SerializedName("vehicle_conf") val vehicleConf: Float? = null,
    val plates: List<PlateDetection>? = null,
    val hazmat: List<HazmatDetection>? = null,
    @SerializedName("is_hazmat") val isHazmat: Boolean = false,
    @SerializedName("has_plate") val hasPlate: Boolean = false
)

data class PlateDetection(
    val bbox: List<Int>,  // [x1, y1, x2, y2]
    val conf: Float,
    val text: String? = null  // 识别出的车牌号
)

data class HazmatDetection(
    val bbox: List<Int>,
    val conf: Float,
    val cls: Int
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
    val tti: Float,  // Travel Time Index
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

// ==================== 停车预测相关 ====================

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

data class ParkingPrediction(
    @SerializedName("parking_lot_id") val parkingLotId: String,
    val name: String,
    @SerializedName("available_spots") val availableSpots: Int,
    @SerializedName("total_spots") val totalSpots: Int,
    @SerializedName("predicted_availability") val predictedAvailability: Int? = null,
    @SerializedName("wait_time_minutes") val waitTimeMinutes: Int? = null
)

// ==================== 通用响应 ====================

data class ApiResponse<T>(
    val status: String,
    val data: T? = null,
    val message: String? = null
)

data class ErrorResponse(
    val detail: String? = null,
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

/**
 * 发起位置共享的响应
 * POST /trips/{trip_id}/share
 */
data class LocationShareResponse(
    val id: Int? = null,
    @SerializedName("share_id") val shareId: String,
    @SerializedName("trip_id") val tripId: Int? = null,
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("ws_url") val wsUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("expired_at") val expiredAt: String? = null
)

/**
 * 位置共享详情（加入时获取）
 * GET /share/{share_id}
 */
data class LocationShareDetail(
    @SerializedName("share_id") val shareId: String,
    @SerializedName("trip_id") val tripId: Int? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("owner_name") val ownerName: String? = null,
    @SerializedName("trip_info") val tripInfo: Trip? = null,
    @SerializedName("ws_url") val wsUrl: String? = null,
    @SerializedName("expired_at") val expiredAt: String? = null
)

/**
 * WebSocket位置消息（发送/接收）
 */
data class LocationMessage(
    val type: String = "location",
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val heading: Float? = null,
    val timestamp: String? = null
)