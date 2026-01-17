package com.example.smartlogistics.ui.components

// =====================================================
// 拥堵预测图表组件
// =====================================================

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartlogistics.network.CongestionData
import com.example.smartlogistics.network.CongestionPrediction

// ==================== 拥堵数据模型 ====================
data class CongestionDataPoint(
    val time: String,      // 时间点 "08:00"
    val ttiIndex: Float,   // TTI指数 1.0-3.0
    val level: CongestionLevel
)

enum class CongestionLevel(val label: String, val color: Color) {
    FREE("畅通", Color(0xFF22C55E)),
    LIGHT("缓行", Color(0xFFFBBF24)),
    MODERATE("拥堵", Color(0xFFF97316)),
    SEVERE("严重", Color(0xFFEF4444))
}

// 根据TTI指数判断拥堵等级
fun getTTILevel(tti: Float): CongestionLevel {
    return when {
        tti < 1.3f -> CongestionLevel.FREE
        tti < 1.6f -> CongestionLevel.LIGHT
        tti < 2.0f -> CongestionLevel.MODERATE
        else -> CongestionLevel.SEVERE
    }
}

// ==================== 从API响应转换数据 ====================

/**
 * 将后端返回的拥堵预测数据转换为图表数据点
 * @param congestionData 后端返回的CongestionData
 * @return 图表数据点列表
 */
fun convertApiDataToCongestionPoints(congestionData: CongestionData?): List<CongestionDataPoint> {
    if (congestionData == null) return emptyList()

    val dataPoints = mutableListOf<CongestionDataPoint>()

    // 添加当前TTI数据点
    val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date())
    dataPoints.add(
        CongestionDataPoint(
            time = currentTime,
            ttiIndex = congestionData.currentTti,
            level = getTTILevel(congestionData.currentTti)
        )
    )

    // 添加预测数据点
    congestionData.predictions?.forEach { prediction ->
        dataPoints.add(
            CongestionDataPoint(
                time = prediction.time,
                ttiIndex = prediction.tti,
                level = getTTILevel(prediction.tti)
            )
        )
    }

    return dataPoints.sortedBy { it.time }
}

/**
 * 生成占位数据（用于API加载前显示）
 * 注意：这不是模拟数据，只是UI占位
 */
fun generatePlaceholderData(): List<CongestionDataPoint> {
    return listOf(
        CongestionDataPoint("--:--", 1.0f, CongestionLevel.FREE)
    )
}

// ==================== TTI趋势图表（Compose Canvas） ====================
@Composable
fun TTITrendChart(
    data: List<CongestionDataPoint>,
    selectedIndex: Int,
    onPointSelected: (Int) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000, easing = EaseOutCubic),
        label = "chartAnimation"
    )

    // 如果数据为空，显示提示
    if (data.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无拥堵数据",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        return
    }

    Column(modifier = modifier) {
        // 图表区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val pointCount = data.size

                if (pointCount < 2) {
                    // 只有一个点时，绘制单点
                    val point = data.first()
                    val centerX = width / 2
                    val centerY = height / 2

                    drawCircle(
                        color = point.level.color,
                        radius = 8.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                    return@Canvas
                }

                val pointSpacing = width / (pointCount - 1)

                // Y轴范围: TTI 1.0 - 2.5
                val minTTI = 1.0f
                val maxTTI = 2.5f
                val ttiRange = maxTTI - minTTI

                // 绘制网格线
                val gridLines = listOf(1.0f, 1.5f, 2.0f, 2.5f)
                gridLines.forEach { tti ->
                    val y = height - ((tti - minTTI) / ttiRange * height)
                    drawLine(
                        color = Color(0xFFE2E8F0),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 计算点位置
                val points = data.mapIndexed { index, point ->
                    val x = index * pointSpacing
                    val clampedTti = point.ttiIndex.coerceIn(minTTI, maxTTI)
                    val y = height - ((clampedTti - minTTI) / ttiRange * height)
                    Offset(x, y * animatedProgress + height * (1 - animatedProgress))
                }

                // 绘制渐变填充区域
                val fillPath = Path().apply {
                    moveTo(0f, height)
                    points.forEachIndexed { index, point ->
                        if (index == 0) {
                            lineTo(point.x, point.y)
                        } else {
                            val prevPoint = points[index - 1]
                            val controlX1 = prevPoint.x + pointSpacing / 2
                            val controlX2 = point.x - pointSpacing / 2
                            cubicTo(controlX1, prevPoint.y, controlX2, point.y, point.x, point.y)
                        }
                    }
                    lineTo(width, height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.3f),
                            primaryColor.copy(alpha = 0.05f)
                        )
                    )
                )

                // 绘制曲线
                val linePath = Path().apply {
                    points.forEachIndexed { index, point ->
                        if (index == 0) {
                            moveTo(point.x, point.y)
                        } else {
                            val prevPoint = points[index - 1]
                            val controlX1 = prevPoint.x + pointSpacing / 2
                            val controlX2 = point.x - pointSpacing / 2
                            cubicTo(controlX1, prevPoint.y, controlX2, point.y, point.x, point.y)
                        }
                    }
                }

                drawPath(
                    path = linePath,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // 绘制数据点
                points.forEachIndexed { index, point ->
                    val pointColor = data[index].level.color

                    // 外圈
                    drawCircle(
                        color = if (index == selectedIndex) pointColor.copy(alpha = 0.3f) else Color.Transparent,
                        radius = if (index == selectedIndex) 12.dp.toPx() else 0f,
                        center = point
                    )

                    // 内圈
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = pointColor,
                        radius = 4.dp.toPx(),
                        center = point
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // X轴时间标签（可滚动）
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(data) { index, point ->
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .clickable { onPointSelected(index) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = point.time.take(5),  // 取前5个字符 "HH:mm"
                        fontSize = 10.sp,
                        color = if (index == selectedIndex) primaryColor else TextSecondary,
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ==================== 时间选择器 ====================
@Composable
fun TimeRangeSelector(
    selectedRange: String,
    onRangeSelected: (String) -> Unit,
    primaryColor: Color
) {
    val ranges = listOf("实时", "今天", "明天", "后天")

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ranges) { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = {
                    Text(
                        text = range,
                        fontSize = 13.sp,
                        fontWeight = if (selectedRange == range) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = primaryColor.copy(alpha = 0.15f),
                    selectedLabelColor = primaryColor
                )
            )
        }
    }
}

// ==================== 拥堵详情卡片 ====================
@Composable
fun CongestionDetailCard(
    dataPoint: CongestionDataPoint,
    primaryColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 时间和状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(dataPoint.level.color, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = dataPoint.time,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = dataPoint.level.label,
                        fontSize = 14.sp,
                        color = dataPoint.level.color,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // TTI指数
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "TTI指数",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = String.format("%.2f", dataPoint.ttiIndex),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = dataPoint.level.color
                )
            }
        }
    }
}

// ==================== 路段拥堵列表 ====================
@Composable
fun RoadCongestionList(
    roads: List<Triple<String, String, CongestionLevel>>,
    primaryColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        roads.forEach { (name, distance, level) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(level.color, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                text = distance,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = level.color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = level.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = level.color
                        )
                    }
                }
            }
        }
    }
}

// ==================== 加载状态组件 ====================
@Composable
fun CongestionLoadingState(
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = primaryColor,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "加载拥堵数据...",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

// ==================== 错误状态组件 ====================
@Composable
fun CongestionErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = errorMessage,
                color = TextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text("重试", color = primaryColor)
            }
        }
    }
}

// 颜色常量
private val TextPrimary = Color(0xFF1F2937)
private val TextSecondary = Color(0xFF6B7280)