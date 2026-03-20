// =====================================================
// 危化品识别功能 - 更新版
// 支持13类危险品分类
// =====================================================

package com.example.smartlogistics.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/**
 * 危化品标识识别辅助类
 * 复用 TFLite 框架识别危化品标识
 * 支持13类危险品分类
 */
class HazmatRecognitionHelper(private val context: Context) {

    private val tfliteHelper = TFLiteHelper(context)

    companion object {
        /**
         * 危化品分类 - 对应模型输出的13个类别
         * 索引与模型输出一一对应
         */
        val HAZMAT_CLASSES = mapOf(
            0 to HazmatClass("0", "poison", "有毒物", 0xFF5F27CD.toInt(), "☠️"),
            1 to HazmatClass("1", "oxygen", "氧气", 0xFF4ECDC4.toInt(), "💨"),
            2 to HazmatClass("2", "flammable", "易燃气体/液体", 0xFFFF4757.toInt(), "🔥"),
            3 to HazmatClass("3", "flammable-solid", "易燃固体", 0xFFFF6348.toInt(), "🧱"),
            4 to HazmatClass("4", "corrosive", "腐蚀性物质", 0xFF1E90FF.toInt(), "🧪"),
            5 to HazmatClass("5", "dangerous", "危险品", 0xFFFF6B6B.toInt(), "⚠️"),
            6 to HazmatClass("6", "non-flammable-gas", "非易燃气体", 0xFF4ECDC4.toInt(), "💨"),
            7 to HazmatClass("7", "organic-peroxide", "有机过氧化物", 0xFFFFA502.toInt(), "⚗️"),
            8 to HazmatClass("8", "explosive", "爆炸物", 0xFFFF6B6B.toInt(), "💥"),
            9 to HazmatClass("9", "radioactive", "放射性物质", 0xFFFFD93D.toInt(), "☢️"),
            10 to HazmatClass("10", "inhalation-hazard", "吸入危害物", 0xFF5F27CD.toInt(), "😷"),
            11 to HazmatClass("11", "spontaneously-combustible", "自燃物质", 0xFFFF6348.toInt(), "🔥"),
            12 to HazmatClass("12", "infectious-substance", "感染性物质", 0xFF5F27CD.toInt(), "🦠")
        )

        /**
         * 根据索引获取危化品类别
         */
        fun getClassByIndex(index: Int): HazmatClass? {
            return HAZMAT_CLASSES[index]
        }

        /**
         * 根据英文代码获取危化品类别
         */
        fun getClassByCode(code: String): HazmatClass? {
            return HAZMAT_CLASSES.values.find { it.englishName == code }
        }

        /**
         * 根据 cls 整数索引获取危化品类别（最可靠，直接对应模型输出）
         */
        fun getClassByCls(cls: Int): HazmatClass? {
            return HAZMAT_CLASSES[cls]
        }

        /**
         * 根据中文名称获取危化品类别
         * 后端返回 hazmat_labels 是中文，如"易燃液体"
         */
        fun getClassByChinese(chineseName: String): HazmatClass? {
            return HAZMAT_CLASSES.values.find {
                it.name == chineseName || it.name.contains(chineseName) || chineseName.contains(it.name)
            }
        }

        /**
         * 通用查找：先英文代码，再中文名，再cls整数
         */
        fun getClassByName(name: String): HazmatClass? {
            return getClassByCode(name) ?: getClassByChinese(name)
        }
    }

    /**
     * 从URI加载图片
     */
    fun loadImageFromUri(uri: Uri): Bitmap? {
        return tfliteHelper.loadImageFromUri(uri)
    }

    /**
     * 识别危化品标识
     * @param bitmap 输入图片
     * @return 识别结果
     */
    fun recognizeHazmat(bitmap: Bitmap): HazmatRecognitionResult {
        // TODO: 当模型文件准备好后，使用真实的推理逻辑
        /*
        val input = tfliteHelper.preprocessImage(bitmap)
        val output = Array(1) { FloatArray(13) } // 13类危化品

        interpreter?.run(input, output)

        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: 0
        val confidence = output[0][maxIndex]

        return HazmatRecognitionResult(
            hazmatClass = HAZMAT_CLASSES[maxIndex],
            confidence = confidence,
            isHazardous = confidence > 0.7f,
            classIndex = maxIndex
        )
        */

        // 临时模拟识别结果（用于测试UI）
        return generateMockHazmatResult()
    }

    /**
     * 批量识别（用于多目标检测场景）
     */
    fun recognizeMultipleHazmat(bitmap: Bitmap): List<HazmatRecognitionResult> {
        // TODO: 实现多目标检测
        return listOf(recognizeHazmat(bitmap))
    }

    /**
     * 生成模拟识别结果（用于测试）
     */
    private fun generateMockHazmatResult(): HazmatRecognitionResult {
        val randomIndex = (0..12).random()
        val randomClass = HAZMAT_CLASSES[randomIndex]
        val confidence = (0.75f..0.98f).random()

        return HazmatRecognitionResult(
            hazmatClass = randomClass,
            confidence = confidence,
            isHazardous = true,
            classIndex = randomIndex
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
    val code: String,           // 分类代码索引 0-12
    val englishName: String,    // 英文标识（与模型标签对应）
    val name: String,           // 中文名称
    val colorInt: Int,          // 显示颜色
    val icon: String = "⚠️"    // 图标表情
)

/**
 * 危化品识别结果
 */
data class HazmatRecognitionResult(
    val hazmatClass: HazmatClass?,  // 识别出的危化品类别
    val confidence: Float,          // 置信度 0-1
    val isHazardous: Boolean,       // 是否为危化品
    val classIndex: Int = -1        // 类别索引（对应模型输出）
)