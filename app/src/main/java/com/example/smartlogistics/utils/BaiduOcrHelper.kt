package com.example.smartlogistics.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 百度OCR工具类（通用文字识别版）
 * 可识别任何包含车次/航班信息的图片
 * 支持：12306截图、携程截图、纸质车票、电子行程单等
 */
object BaiduOcrHelper {
    
    private const val TAG = "BaiduOCR"
    
    // 你的百度OCR API Key
    private const val API_KEY = "GuCoePLMYlDViVorDayYHW2z"
    private const val SECRET_KEY = "zrK52pLuC8LlQA5YjvjllA3Z3mi11cBI"
    
    // 百度API地址 - 使用通用文字识别（高精度版）
    private const val TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
    private const val GENERAL_OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // 缓存access_token
    private var accessToken: String? = null
    private var tokenExpireTime: Long = 0
    
    /**
     * OCR识别结果
     */
    data class OcrResult(
        val success: Boolean,
        val tripType: String,
        val tripNumber: String,
        val tripDate: String,
        val departureStation: String,
        val arrivalStation: String,
        val departureTime: String,
        val seatInfo: String,
        val passengerName: String,
        val confidence: Float,
        val errorMsg: String? = null
    )
    
    /**
     * 识别火车票或机票（通用识别）
     */
    suspend fun recognizeTicket(
        context: Context,
        imageUri: Uri,
        tripType: String
    ): OcrResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始通用识别, tripType=$tripType")
            
            // 1. 获取access_token
            val token = getAccessToken()
            if (token == null) {
                Log.e(TAG, "获取token失败")
                return@withContext createErrorResult(tripType, "获取access_token失败，请检查API Key")
            }
            Log.d(TAG, "获取token成功")
            
            // 2. 读取图片并压缩转base64
            val imageBase64 = imageUriToBase64(context, imageUri)
            if (imageBase64 == null) {
                Log.e(TAG, "图片转base64失败")
                return@withContext createErrorResult(tripType, "读取图片失败")
            }
            Log.d(TAG, "图片转base64成功, 长度=${imageBase64.length}")
            
            // 3. 调用通用OCR API
            val apiUrl = "$GENERAL_OCR_URL?access_token=$token"
            Log.d(TAG, "调用通用OCR API")
            
            val formBody = FormBody.Builder()
                .add("image", imageBase64)
                .build()
            
            val request = Request.Builder()
                .url(apiUrl)
                .post(formBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "OCR响应: $responseBody")
            
            if (responseBody == null) {
                return@withContext createErrorResult(tripType, "OCR请求失败")
            }
            
            // 4. 解析结果 - 提取所有文字
            val allText = parseOcrText(responseBody)
            Log.d(TAG, "识别文字: $allText")
            
            if (allText.isBlank()) {
                return@withContext createErrorResult(tripType, "未识别到文字")
            }
            
            // 5. 从文字中提取行程信息
            extractTripInfo(allText, tripType)
            
        } catch (e: Exception) {
            Log.e(TAG, "OCR异常: ${e.message}", e)
            createErrorResult(tripType, "识别出错: ${e.message}")
        }
    }
    
    private fun createErrorResult(tripType: String, errorMsg: String): OcrResult {
        return OcrResult(
            success = false,
            tripType = tripType,
            tripNumber = "",
            tripDate = "",
            departureStation = "",
            arrivalStation = "",
            departureTime = "",
            seatInfo = "",
            passengerName = "",
            confidence = 0f,
            errorMsg = errorMsg
        )
    }
    
    /**
     * 获取百度access_token
     */
    private fun getAccessToken(): String? {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            Log.d(TAG, "使用缓存的token")
            return accessToken
        }
        
        try {
            val url = "$TOKEN_URL?grant_type=client_credentials&client_id=$API_KEY&client_secret=$SECRET_KEY"
            
            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "Token响应: ${responseBody?.take(100)}...")
            
            if (responseBody != null) {
                val json = JSONObject(responseBody)
                if (json.has("access_token")) {
                    accessToken = json.getString("access_token")
                    val expiresIn = json.optLong("expires_in", 2592000)
                    tokenExpireTime = System.currentTimeMillis() + (expiresIn - 60) * 1000
                    return accessToken
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取token异常: ${e.message}", e)
        }
        
        return null
    }
    
    /**
     * 将图片Uri转为Base64
     */
    private fun imageUriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap == null) {
                Log.e(TAG, "解码图片失败")
                return null
            }
            
            val compressedBitmap = compressBitmap(bitmap, 1024)
            
            val outputStream = ByteArrayOutputStream()
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val imageBytes = outputStream.toByteArray()
            
            Log.d(TAG, "图片大小: ${imageBytes.size / 1024}KB")
            
            Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            
        } catch (e: Exception) {
            Log.e(TAG, "图片转base64异常: ${e.message}", e)
            null
        }
    }
    
    private fun compressBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }
        
        val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * 解析OCR返回的文字
     */
    private fun parseOcrText(responseBody: String): String {
        try {
            val json = JSONObject(responseBody)
            
            if (json.has("error_code")) {
                val errorMsg = json.optString("error_msg", "未知错误")
                Log.e(TAG, "API错误: $errorMsg")
                return ""
            }
            
            val wordsResult = json.optJSONArray("words_result") ?: return ""
            
            val textBuilder = StringBuilder()
            for (i in 0 until wordsResult.length()) {
                val item = wordsResult.getJSONObject(i)
                val words = item.optString("words", "")
                textBuilder.append(words).append(" ")
            }
            
            return textBuilder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "解析OCR文字异常: ${e.message}", e)
            return ""
        }
    }
    
    /**
     * 从文字中提取行程信息
     */
    private fun extractTripInfo(text: String, tripType: String): OcrResult {
        Log.d(TAG, "开始提取行程信息, tripType=$tripType")
        
        return if (tripType == "train") {
            extractTrainInfo(text)
        } else {
            extractFlightInfo(text)
        }
    }
    
    /**
     * 提取火车信息
     */
    private fun extractTrainInfo(text: String): OcrResult {
        // 转大写，方便匹配字母
        val upperText = text.uppercase()
        
        // 提取车次号: G/D/C/K/Z/T/L/Y + 数字
        val trainRegex = Regex("([GDCKZTLY]\\d{1,5})")
        val trainMatch = trainRegex.find(upperText)
        val trainNumber = trainMatch?.value ?: ""
        
        // 提取日期
        val date = extractDate(text)
        
        // 提取时间 (如 11:38, 08:30)
        val timeRegex = Regex("(\\d{1,2}[::：]\\d{2})")
        val times = timeRegex.findAll(text).map { it.value.replace("：", ":") }.toList()
        val departureTime = times.firstOrNull() ?: ""
        
        // 提取站点
        val stations = extractStations(text)
        val departureStation = stations.firstOrNull() ?: ""
        val arrivalStation = stations.getOrNull(1) ?: ""
        
        // 提取座位信息
        val seatRegex = Regex("(\\d{1,2}车\\d{1,3}[A-F]号?座?|[一二]等座?|商务座?|无座|硬座|软座|硬卧|软卧)")
        val seatMatch = seatRegex.find(text)
        val seatInfo = seatMatch?.value ?: ""
        
        Log.d(TAG, "火车提取结果: 车次=$trainNumber, 日期=$date, 出发=$departureStation, 到达=$arrivalStation, 时间=$departureTime")
        
        return OcrResult(
            success = trainNumber.isNotBlank(),
            tripType = "train",
            tripNumber = trainNumber,
            tripDate = date,
            departureStation = departureStation,
            arrivalStation = arrivalStation,
            departureTime = departureTime,
            seatInfo = seatInfo,
            passengerName = "",
            confidence = if (trainNumber.isNotBlank()) 0.90f else 0f,
            errorMsg = if (trainNumber.isBlank()) "未找到车次信息" else null
        )
    }
    
    /**
     * 提取航班信息
     */
    private fun extractFlightInfo(text: String): OcrResult {
        // 转大写，方便匹配
        val upperText = text.uppercase()
        Log.d(TAG, "航班识别文本(大写): $upperText")
        
        // 提取航班号
        var flightNumber = ""
        
        // 常见航司代码列表
        val airlines = listOf(
            "CA", "MU", "CZ", "HU", "ZH", "FM", "MF", "SC", "GJ", "PN", 
            "GS", "BK", "KN", "JD", "HO", "TV", "QW", "EU", "DR", "G5", 
            "8L", "CN", "3U", "9C", "NS", "Y8", "KY", "AQ", "DZ", "GT"
        )
        
        // 方法1: 查找 航司代码+数字 (优先)
        for (airline in airlines) {
            // 匹配航司代码后紧跟3-4位数字
            val regex = Regex("$airline\\s?(\\d{3,4})")
            val match = regex.find(upperText)
            if (match != null) {
                val num = match.groupValues[1]
                flightNumber = airline + num
                Log.d(TAG, "方法1匹配: $flightNumber")
                break
            }
        }
        
        // 方法2: 通用格式 2字母+3-4数字（排除超长数字串）
        if (flightNumber.isBlank()) {
            val regex = Regex("([A-Z]{2})(\\d{3,4})(?![0-9])")
            val match = regex.find(upperText)
            if (match != null) {
                flightNumber = match.groupValues[1] + match.groupValues[2]
                Log.d(TAG, "方法2匹配: $flightNumber")
            }
        }
        
        // 方法3: 数字开头的航司 (3U, 9C, 8L等)
        if (flightNumber.isBlank()) {
            val regex = Regex("([0-9][A-Z])(\\d{3,4})(?![0-9])")
            val match = regex.find(upperText)
            if (match != null) {
                flightNumber = match.groupValues[1] + match.groupValues[2]
                Log.d(TAG, "方法3匹配: $flightNumber")
            }
        }
        
        Log.d(TAG, "最终航班号: '$flightNumber'")
        
        // 提取日期 - 航班用专门的逻辑
        val date = extractFlightDate(text)
        
        // 提取时间
        val timeRegex = Regex("(\\d{1,2}[::：]\\d{2})")
        val times = timeRegex.findAll(text).map { it.value.replace("：", ":") }.toList()
        val departureTime = times.firstOrNull() ?: ""
        
        // 提取城市/机场
        val cities = extractCities(text)
        val departureCity = cities.firstOrNull() ?: ""
        val arrivalCity = cities.getOrNull(1) ?: ""
        
        // 提取座位
        val seatRegex = Regex("(\\d{1,2}[A-K]|经济舱|商务舱|头等舱)")
        val seatMatch = seatRegex.find(text)
        val seatInfo = seatMatch?.value ?: ""
        
        Log.d(TAG, "航班提取结果: 航班=$flightNumber, 日期=$date, 出发=$departureCity, 到达=$arrivalCity, 时间=$departureTime")
        
        return OcrResult(
            success = flightNumber.isNotBlank(),
            tripType = "flight",
            tripNumber = flightNumber,
            tripDate = date,
            departureStation = departureCity,
            arrivalStation = arrivalCity,
            departureTime = departureTime,
            seatInfo = seatInfo,
            passengerName = "",
            confidence = if (flightNumber.isNotBlank()) 0.90f else 0f,
            errorMsg = if (flightNumber.isBlank()) "未找到航班信息" else null
        )
    }
    
    /**
     * 提取日期（火车用）
     */
    private fun extractDate(text: String): String {
        // 格式1: 2026-01-20 或 2026/01/20
        val dateRegex1 = Regex("(20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2})")
        val match1 = dateRegex1.find(text)
        if (match1 != null) {
            return match1.value.replace("/", "-")
        }
        
        // 格式2: 2026年01月20日
        val dateRegex2 = Regex("(20\\d{2})年(\\d{1,2})月(\\d{1,2})日?")
        val match2 = dateRegex2.find(text)
        if (match2 != null) {
            val (year, month, day) = match2.destructured
            return "$year-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
        }
        
        // 格式3: 01月20日 (没有年份，用当前年)
        val dateRegex3 = Regex("(\\d{1,2})月(\\d{1,2})日")
        val match3 = dateRegex3.find(text)
        if (match3 != null) {
            val (month, day) = match3.destructured
            val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            return "$year-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
        }
        
        // 格式4: 09-07 或 01/20 (MM-DD格式，用当前年)
        // 注意：要排除时间格式如 11:45
        val dateRegex4 = Regex("(?<!\\d)(\\d{2})[-/](\\d{2})(?!\\d|:)")
        val match4 = dateRegex4.find(text)
        if (match4 != null) {
            val part1 = match4.groupValues[1].toIntOrNull() ?: 0
            val part2 = match4.groupValues[2].toIntOrNull() ?: 0
            // 判断是否是合理的月-日格式（月份1-12，日期1-31）
            if (part1 in 1..12 && part2 in 1..31) {
                val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                return "$year-${part1.toString().padStart(2, '0')}-${part2.toString().padStart(2, '0')}"
            }
        }
        
        return ""
    }
    
    /**
     * 提取日期（航班用 - 优先匹配起飞日期）
     */
    private fun extractFlightDate(text: String): String {
        // 航班日期通常是 MM/DD 或 MM-DD 格式，优先匹配
        // 查找 "起飞"、"出发"、"计划" 附近的日期
        
        // 格式1: 优先匹配 MM/DD 或 MM-DD（航班常用格式）
        val dateRegex1 = Regex("(?<!\\d)(\\d{2})[-/](\\d{2})(?!\\d|:)")
        val match1 = dateRegex1.find(text)
        if (match1 != null) {
            val part1 = match1.groupValues[1].toIntOrNull() ?: 0
            val part2 = match1.groupValues[2].toIntOrNull() ?: 0
            if (part1 in 1..12 && part2 in 1..31) {
                val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                Log.d(TAG, "航班日期匹配 MM-DD: ${part1}-${part2}")
                return "$year-${part1.toString().padStart(2, '0')}-${part2.toString().padStart(2, '0')}"
            }
        }
        
        // 格式2: 2026-01-20 或 2026/01/20
        val dateRegex2 = Regex("(20\\d{2})[-/](\\d{1,2})[-/](\\d{1,2})")
        val match2 = dateRegex2.find(text)
        if (match2 != null) {
            val year = match2.groupValues[1]
            val month = match2.groupValues[2].padStart(2, '0')
            val day = match2.groupValues[3].padStart(2, '0')
            return "$year-$month-$day"
        }
        
        // 格式3: 01月20日
        val dateRegex3 = Regex("(\\d{1,2})月(\\d{1,2})日")
        val match3 = dateRegex3.find(text)
        if (match3 != null) {
            val (month, day) = match3.destructured
            val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            return "$year-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
        }
        
        return ""
    }
    
    /**
     * 提取火车站名
     */
    private fun extractStations(text: String): List<String> {
        val stations = mutableListOf<String>()
        
        // 查找 "过 XXX" 模式（出发站 - 12306格式）
        val passRegex = Regex("过\\s*([\\u4e00-\\u9fa5]{2,5})")
        val passMatch = passRegex.find(text)
        if (passMatch != null) {
            stations.add(passMatch.groupValues[1])
        }
        
        // 查找 "终 XXX" 模式（到达站 - 12306格式）
        val endRegex = Regex("终\\s*([\\u4e00-\\u9fa5]{2,5})")
        val endMatch = endRegex.find(text)
        if (endMatch != null) {
            stations.add(endMatch.groupValues[1])
        }
        
        // 如果找到了过/终模式，直接返回
        if (stations.size >= 2) {
            return stations
        }
        
        // 查找常见站名格式: 带"站/南/北/东/西"后缀的
        val stationRegex = Regex("([\\u4e00-\\u9fa5]{2,4}(?:站|南|北|东|西))")
        val matches = stationRegex.findAll(text)
        
        for (match in matches) {
            val station = match.value
            // 过滤掉干扰词
            if (!station.contains("等") && !station.contains("座") && 
                !station.contains("车") && !station.contains("票") &&
                !stations.contains(station)) {
                stations.add(station)
            }
        }
        
        return stations.distinct().take(2)
    }
    
    /**
     * 提取城市/机场名
     */
    private fun extractCities(text: String): List<String> {
        val cities = mutableListOf<String>()
        
        // 优先查找机场名称（如 "长沙黄花机场T2"、"北京大兴机场"）
        val airportRegex = Regex("([\\u4e00-\\u9fa5]{2,6}机场)")
        val airportMatches = airportRegex.findAll(text)
        for (match in airportMatches) {
            val airport = match.value
            // 过滤掉干扰词
            if (!airport.contains("信息") && !airport.contains("贴士")) {
                cities.add(airport)
            }
        }
        
        // 如果找到机场了就返回
        if (cities.size >= 2) {
            return cities.distinct().take(2)
        }
        
        // 查找 "XX → XX" 或 "XX - XX" 或 "XX至XX" 或 "XX飞XX" 格式
        val routeRegex = Regex("([\\u4e00-\\u9fa5]{2,4})\\s*[→\\-—至飞]\\s*([\\u4e00-\\u9fa5]{2,4})")
        val routeMatch = routeRegex.find(text)
        if (routeMatch != null) {
            val dep = routeMatch.groupValues[1]
            val arr = routeMatch.groupValues[2]
            // 过滤干扰词
            if (!dep.contains("信息") && !dep.contains("贴士") && 
                !arr.contains("信息") && !arr.contains("贴士")) {
                cities.clear()
                cities.add(dep)
                cities.add(arr)
                return cities
            }
        }
        
        // 查找常见城市名
        val commonCities = listOf(
            "北京", "上海", "广州", "深圳", "成都", "杭州", "武汉", "西安",
            "重庆", "长沙", "南京", "天津", "青岛", "厦门", "昆明", "大连",
            "三亚", "海口", "郑州", "沈阳", "哈尔滨", "济南", "福州", "南宁",
            "贵阳", "兰州", "银川", "西宁", "拉萨", "乌鲁木齐"
        )
        
        for (city in commonCities) {
            if (text.contains(city) && !cities.contains(city)) {
                cities.add(city)
                if (cities.size >= 2) break
            }
        }
        
        return cities.distinct().take(2)
    }
}
