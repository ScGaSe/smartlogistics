// =====================================================
// 危化品识别功能
// =====================================================

package com.example.smartlogistics.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/**
 * 危化品标识识别辅助类
 * 复用 TFLite 框架识别危化品标识
 */
class HazmatRecognitionHelper(private val context: Context) {

    private val tfliteHelper = TFLiteHelper(context)

    companion object {
        // 危化品分类
        val HAZMAT_CLASSES = mapOf(
            1 to HazmatClass("1", "爆炸品", "explosive", 0xFFFF6B6B.toInt()),
            2 to HazmatClass("2", "压缩气体", "compressed_gas", 0xFF4ECDC4.toInt()),
            3 to HazmatClass("3", "易燃液体", "flammable_liquid", 0xFFFF4757.toInt()),
            4 to HazmatClass("4", "易燃固体", "flammable_solid", 0xFFFF6348.toInt()),
            5 to HazmatClass("5", "氧化剂", "oxidizer", 0xFFFFA502.toInt()),
            6 to HazmatClass("6", "有毒物质", "toxic", 0xFF5F27CD.toInt()),
            7 to HazmatClass("7", "放射性物质", "radioactive", 0xFFFFD93D.toInt()),
            8 to HazmatClass("8", "腐蚀品", "corrosive", 0xFF1E90FF.toInt()),
            9 to HazmatClass("9", "杂项危险品", "miscellaneous", 0xFF2ED573.toInt())
        )
    }

    /**
     * 从URI加载图片
     */
    fun loadImageFromUri(uri: Uri): Bitmap? {
        return tfliteHelper.loadImageFromUri(uri)
    }

    /**
     * 识别危化品标识
     */
    fun recognizeHazmat(bitmap: Bitmap): HazmatRecognitionResult {
        // TODO: 当模型文件准备好后，使用真实的推理逻辑
        /*
        val input = tfliteHelper.preprocessImage(bitmap)
        val output = Array(1) { FloatArray(9) } // 9类危化品

        interpreter?.run(input, output)

        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: 0
        val confidence = output[0][maxIndex]

        return HazmatRecognitionResult(
            hazmatClass = HAZMAT_CLASSES[maxIndex + 1],
            confidence = confidence,
            isHazardous = confidence > 0.7f
        )
        */

        // 临时模拟识别结果（用于测试UI）
        return generateMockHazmatResult()
    }

    /**
     * 生成模拟识别结果（用于测试）
     */
    private fun generateMockHazmatResult(): HazmatRecognitionResult {
        val randomClass = HAZMAT_CLASSES.values.random()
        val confidence = (0.75f..0.98f).random()

        return HazmatRecognitionResult(
            hazmatClass = randomClass,
            confidence = confidence,
            isHazardous = true
        )
    }

    private fun ClosedFloatingPointRange<Float>.random(): Float {
        return start + (Math.random() * (endInclusive - start)).toFloat()
    }

    /**
     * 释放资源
     */
    fun close() {
        tfliteHelper.close()
    }
}

/**
 * 危化品分类数据类
 */
data class HazmatClass(
    val code: String,           // 分类代码 1-9
    val name: String,           // 中文名称
    val englishName: String,    // 英文标识
    val colorInt: Int           // 显示颜色
)

/**
 * 危化品识别结果
 */
data class HazmatRecognitionResult(
    val hazmatClass: HazmatClass?,  // 识别出的危化品类别
    val confidence: Float,          // 置信度 0-1
    val isHazardous: Boolean        // 是否为危化品
)