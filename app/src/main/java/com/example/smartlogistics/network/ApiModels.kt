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
    @SerializedName("destination_poi_id") val destinationPoiId: String,
    @SerializedName("cargo_info") val cargoInfo: CargoInfo
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

data class POI(
    val id: String,
    val name: String,
    @SerializedName("poi_type") val poiType: String,
    val mode: String? = null,  // "pro" 或 "personal"
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
    @SerializedName("start_poi_id") val startPoiId: String,
    @SerializedName("end_poi_id") val endPoiId: String,
    @SerializedName("vehicle_id") val vehicleId: Int? = null
)

data class RouteResult(
    val path: List<String>,
    @SerializedName("total_cost") val totalCost: Float? = null,
    @SerializedName("constraints_applied") val constraintsApplied: List<String>? = null,
    @SerializedName("congestion_info") val congestionInfo: Map<String, Int>? = null,
    // 兼容旧字段
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
    val role: String? = null  // 可选，帮助AI识别模式
)

data class AskResponse(
    val answer: String,
    val role: String? = null,
    val intent: AskIntent? = null
)

data class AskIntent(
    val role: String,
    val confidence: Float,
    @SerializedName("intent_type") val intentType: String,
    @SerializedName("matched_keywords") val matchedKeywords: List<String>? = null,
    // 导航相关
    @SerializedName("destination") val destination: String? = null,
    @SerializedName("poi_id") val poiId: String? = null
)

// ==================== 拥堵预测相关 (新增) ====================

data class CongestionResponse(
    val status: String,
    @SerializedName("road_id") val roadId: String,
    val forecast: List<CongestionForecast>
)

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

// ==================== 停车预测相关 ====================

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
