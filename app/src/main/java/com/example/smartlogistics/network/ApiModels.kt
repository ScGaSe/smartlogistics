package com.example.smartlogistics.network

import com.google.gson.annotations.JsonAdapter
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
    @SerializedName("estimated_arrival_time") val estimatedArrivalTime: String,
    @SerializedName("cargo_info") val cargoInfo: CargoInfo? = null
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
    @SerializedName("origin_lat") val originLat: Double? = null,
    @SerializedName("origin_lon") val originLon: Double? = null,
    @SerializedName("dest_lat") val destLat: Double? = null,
    @SerializedName("dest_lon") val destLon: Double? = null,
    @SerializedName("start_poi_id") val startPoiId: String? = null,
    @SerializedName("end_poi_id") val endPoiId: String? = null,
    @SerializedName("vehicle_id") val vehicleId: Int? = null
)

data class RouteCoordinate(
    val lat: Double,
    val lng: Double
)

data class RouteResponse(
    val path: List<String>,
    @SerializedName("total_cost") val totalCost: Float? = null,
    @SerializedName("constraints_applied") val constraintsApplied: List<String>? = null,
    @SerializedName("congestion_info") val congestionInfo: Map<String, Int>? = null,
    val coordinates: List<RouteCoordinate>? = null   // 后端新增：可直接用于地图绘制的经纬度列表
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

// 严格按照后端 vision_service.py 返回格式定义
@JsonAdapter(VisionResponseAdapter::class)
data class VisionResponse(
    val status: String,
    @SerializedName("license_plate") val licensePlate: LicensePlateInfo? = null,
    @SerializedName("vehicle_type") val vehicleType: VehicleTypeInfo? = null,
    val hazmat: List<String>? = null
)

// hazmat 可能是 ["oxygen"] 数组，也可能是 {"detected":true,"labels":["oxygen"]} 对象
class VisionResponseAdapter : com.google.gson.TypeAdapter<VisionResponse>() {
    private val gson = com.google.gson.GsonBuilder().create()

    override fun write(out: com.google.gson.stream.JsonWriter, value: VisionResponse?) {
        out.nullValue()
    }

    override fun read(reader: com.google.gson.stream.JsonReader): VisionResponse {
        var status = "success"
        var licensePlate: LicensePlateInfo? = null
        var vehicleType: VehicleTypeInfo? = null
        var hazmat: List<String>? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "status" -> status = reader.nextString()
                "license_plate" -> licensePlate = gson.fromJson(reader, LicensePlateInfo::class.java)
                "vehicle_type" -> {
                    // vehicle_type 可能是对象 {"class":"Truck"} 或字符串
                    when (reader.peek()) {
                        com.google.gson.stream.JsonToken.BEGIN_OBJECT ->
                            vehicleType = gson.fromJson(reader, VehicleTypeInfo::class.java)
                        com.google.gson.stream.JsonToken.STRING -> {
                            val cls = reader.nextString()
                            vehicleType = VehicleTypeInfo(vehicleClass = cls)
                        }
                        else -> reader.skipValue()
                    }
                }
                "hazmat" -> {
                    // hazmat 可能是数组 ["oxygen"] 或对象 {"detected":true,"labels":[...]}
                    hazmat = when (reader.peek()) {
                        com.google.gson.stream.JsonToken.BEGIN_ARRAY -> {
                            val list = mutableListOf<String>()
                            reader.beginArray()
                            while (reader.hasNext()) list.add(reader.nextString())
                            reader.endArray()
                            list
                        }
                        com.google.gson.stream.JsonToken.BEGIN_OBJECT -> {
                            val list = mutableListOf<String>()
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "labels", "classes", "types", "items" -> {
                                        if (reader.peek() == com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
                                            reader.beginArray()
                                            while (reader.hasNext()) list.add(reader.nextString())
                                            reader.endArray()
                                        } else reader.skipValue()
                                    }
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            list
                        }
                        com.google.gson.stream.JsonToken.STRING -> listOf(reader.nextString())
                        com.google.gson.stream.JsonToken.NULL -> { reader.nextNull(); null }
                        else -> { reader.skipValue(); null }
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return VisionResponse(status, licensePlate, vehicleType, hazmat)
    }
}

data class LicensePlateInfo(
    val detected: Boolean = false,
    val text: String? = null
)

data class VehicleTypeInfo(
    @SerializedName("class") val vehicleClass: String? = null,
    val confidence: Float? = null
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

@JsonAdapter(GateQueuesAdapter::class)
data class GateQueuesResponse(
    val queues: Map<String, Int>? = null
)

// 后端直接返回 {"Gate_N1": 3, "Gate_S1": 7}，没有 queues 包装层
class GateQueuesAdapter : com.google.gson.TypeAdapter<GateQueuesResponse>() {
    override fun write(out: com.google.gson.stream.JsonWriter, value: GateQueuesResponse?) {
        out.nullValue()
    }
    override fun read(reader: com.google.gson.stream.JsonReader): GateQueuesResponse {
        val map = mutableMapOf<String, Int>()
        return try {
            when (reader.peek()) {
                com.google.gson.stream.JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        when (reader.peek()) {
                            com.google.gson.stream.JsonToken.NUMBER -> {
                                try { map[key] = reader.nextInt() }
                                catch (e: Exception) { reader.skipValue() }
                            }
                            com.google.gson.stream.JsonToken.BEGIN_OBJECT -> {
                                // 可能是 {"Gate_N1": {"queue": 3, ...}} 格式
                                var queueCount = 0
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val field = reader.nextName()
                                    if (field == "queue" || field == "queue_length" || field == "count") {
                                        queueCount = try { reader.nextInt() } catch (e: Exception) { reader.skipValue(); 0 }
                                    } else { reader.skipValue() }
                                }
                                reader.endObject()
                                map[key] = queueCount
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
            GateQueuesResponse(queues = map.ifEmpty { null })
        } catch (e: Exception) {
            GateQueuesResponse(queues = null)
        }
    }
}

data class GateRecommendResponse(
    @SerializedName("recommended_gate") val recommendedGate: String?,
    @SerializedName("recommended_name") val recommendedName: String?,
    @SerializedName("estimated_wait_min") val estimatedWaitMin: Int?,
    val reason: String?,
    @SerializedName("all_gates") val allGates: List<GateScoreItem>?
)

data class GateScoreItem(
    @SerializedName("gate_id") val gateId: String,
    val name: String?,
    val score: Float?,
    @SerializedName("queue_count") val queueCount: Int?,
    val status: String?,
    val distance: Int?
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


// ==================== 个人端停车场接口模型 ====================
// 后端实际返回结构：
// { "status":"success", "parking_lots": { "parking_xxx": { "lot_id","available","capacity","name","lat","lng","role" } } }

data class PersonalParkingItem(
    @SerializedName("lot_id") val lotId: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val capacity: Int = 0,           // 总车位
    val available: Int = 0,          // 剩余车位
    val status: String? = null,
    val role: String = ""            // "personal" 或 "professional"
)

// GET /api/parking/all?role=personal
// parking_lots 是 Map<id, item>，不是数组
data class PersonalParkingAllResponse(
    val status: String? = null,
    @SerializedName("parking_lots") val parkingLots: Map<String, PersonalParkingItem>? = null
)

// GET /api/parking/best?role=personal
data class PersonalParkingBestResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: PersonalParkingItem? = null
)

// ==================== 货运仓库 POI ====================

data class WarehouseItem(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val type: String = ""
)

data class WarehousesResponse(
    val warehouses: List<WarehouseItem>? = null
)
// ==================== 闸口和停车场 POI ====================

data class GatePoiItem(
    val id: Int? = null,           // 后端返回整数 id（1-57）
    val name: String? = null,
    val lat: Double = 0.0,
    val lon: Double? = null,
    val lng: Double? = null,
    val ref: String? = null,       // 如 "B37/E37"
    val status: String? = null,
    @SerializedName("queue_count") val queueCount: Int? = null
) {
    val longitude: Double get() = lon ?: lng ?: 0.0
    val idStr: String get() = id?.toString() ?: ""
}

data class GatesResponse(
    val status: String? = null,
    val gates: List<GatePoiItem>? = null
)

data class ParkingPoiItem(
    val id: String? = null,
    val name: String? = null,
    val lat: Double = 0.0,
    val lon: Double? = null,
    val lng: Double? = null,
    @SerializedName("total_spots") val totalSpots: Int? = null,
    @SerializedName("available_spots") val availableSpots: Int? = null,
    val status: String? = null
) {
    val longitude: Double get() = lon ?: lng ?: 0.0
}

data class ParkingLotsResponse(
    val status: String? = null,
    @SerializedName("parking_lots") val parkingLots: List<ParkingPoiItem>? = null
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
    val status: String?,
    val success: Boolean = false,
    @SerializedName("match_result") val matchResult: ParkingMatchResult?,
    // 旧字段兼容
    @SerializedName("match_confidence") val matchConfidence: Float?,
    val location: ParkingLocationInfo?,
    val navigation: ParkingNavigation?,
    val message: String?
)

data class ParkingMatchResult(
    val distance: Float?,
    val confidence: String?,   // "high" / "medium" / "low"
    val floor: Int?,
    @SerializedName("spot_code") val spotCode: String?,
    @SerializedName("parking_area") val parkingArea: String?,
    @SerializedName("parked_time") val parkedTime: String?,
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