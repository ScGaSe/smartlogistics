package com.example.smartlogistics.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ==================== 品牌主色 ====================
val BrandBlue = Color(0xFF4285F4)
val BrandBlueDark = Color(0xFF1A73E8)
val BrandBlueLight = Color(0xFFE8F0FE)

// ==================== 专业模式 - 货车橙色系 ====================
val TruckOrange = Color(0xFFDC8619)
val TruckOrangeDark = Color(0xFFDC8619)
val TruckOrangeLight = Color(0xFFFFE8E0)
val TruckOrangeSurface = Color(0xFFFFF4F0)
val TruckYellow = Color(0xFFFFB800)
val TruckAmber = Color(0xFFFF9500)

// ==================== 个人模式 - 私家车绿色系 ====================
val CarGreen = Color(0xFF0DA192)
val CarGreenDark = Color(0xFF0DA192)
val CarGreenLight = Color(0xFFE6F4EA)
val CarGreenSurface = Color(0xFFF0FDF4)
val CarTeal = Color(0xFF00BFA5)
val CarMint = Color(0xFF4DD0A1)

// ==================== 功能色 ====================
val WarningYellow = Color(0xFFFFC107)
val WarningYellowLight = Color(0xFFFFF8E1)
val ErrorRed = Color(0xFFEA4335)
val ErrorRedLight = Color(0xFFFDECEA)
val SuccessGreen = Color(0xFF34A853)
val SuccessGreenLight = Color(0xFFE6F4EA)
val InfoBlue = Color(0xFF1A73E8)
val InfoBlueLight = Color(0xFFE8F0FE)

// ==================== 拥堵等级色 ====================
val CongestionSevere = Color(0xFFDC3545)
val CongestionModerate = Color(0xFFFF9800)
val CongestionLight = Color(0xFFFFEB3B)
val CongestionFree = Color(0xFF4CAF50)

// ==================== 中性色 ====================
val TextPrimary = Color(0xFF1F2937)
val TextSecondary = Color(0xFF6B7280)
val TextTertiary = Color(0xFF9CA3AF)
val TextOnDark = Color(0xFFFFFFFF)

val BackgroundPrimary = Color(0xFFF8FAFC)
val BackgroundSecondary = Color(0xFFF1F5F9)
val BackgroundCard = Color(0xFFFFFFFF)

val BorderLight = Color(0xFFE2E8F0)
val BorderMedium = Color(0xFFCBD5E1)
val DividerColor = Color(0xFFE5E7EB)

// ==================== 渐变色 ====================
val TruckGradient = Brush.linearGradient(
    colors = listOf(TruckOrange, TruckOrangeDark)
)

val TruckGradientHorizontal = Brush.horizontalGradient(
    colors = listOf(TruckOrange, TruckYellow)
)

val CarGradient = Brush.linearGradient(
    colors = listOf(CarGreen, CarGreenDark)
)

val CarGradientHorizontal = Brush.horizontalGradient(
    colors = listOf(CarGreen, CarTeal)
)

val AIGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
)

val AIGradientHorizontal = Brush.horizontalGradient(
    colors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF))
)

val BluePurpleGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF4285F4), Color(0xFF9B51E0))
)

// ==================== 状态色 ====================
val StatusOnTime = Color(0xFF34A853)
val StatusDelayed = Color(0xFFFF9800)
val StatusCancelled = Color(0xFFEA4335)
val StatusPending = Color(0xFF9E9E9E)
val StatusApproved = Color(0xFF4CAF50)
val StatusRejected = Color(0xFFF44336)

// ==================== POI类型色 ====================
val POIFood = Color(0xFFFF7043)
val POIParking = Color(0xFF42A5F5)
val POIGas = Color(0xFFFFCA28)
val POIHotel = Color(0xFFAB47BC)
val POIWarehouse = Color(0xFF8D6E63)
val POIWeighStation = Color(0xFF78909C)

// ==================== 兼容旧色 ====================
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
