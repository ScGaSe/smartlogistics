package com.example.smartlogistics.utils

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.iflytek.sparkchain.core.SparkChain
import com.iflytek.sparkchain.core.SparkChainConfig
import com.iflytek.sparkchain.core.asr.ASR
import com.iflytek.sparkchain.core.asr.AsrCallbacks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 讯飞语音识别工具类
 * 基于SparkChain SDK实现语音转文字功能
 */
class XunfeiSpeechHelper {

    companion object {
        private const val TAG = "XunfeiSpeech"

        // 讯飞应用配置 - 替换为你的APPID
        private const val APP_ID = "a0ede71d"
        private const val API_KEY = "ae2e5348692344a0bc4834e44ec338ff"
        private const val API_SECRET = "ZjM1YWM2OTA5YTRmYTEzMGY5ZWRjNDJk"

        private var isInitialized = false

        /**
         * 初始化SDK（在Application或MainActivity中调用一次）
         */
        fun initSDK(): Boolean {
            if (isInitialized) return true

            try {
                val config = SparkChainConfig.builder()
                    .appID(APP_ID)
                    .apiKey(API_KEY)
                    .apiSecret(API_SECRET)

                val ret = SparkChain.getInst().init(android.app.Application(), config)
                isInitialized = (ret == 0)
                Log.d(TAG, "SDK初始化结果: $ret")
                return isInitialized
            } catch (e: Exception) {
                Log.e(TAG, "SDK初始化失败: ${e.message}")
                return false
            }
        }
    }

    // 语音识别状态
    sealed class SpeechState {
        object Idle : SpeechState()
        object Listening : SpeechState()
        object Processing : SpeechState()
        data class Result(val text: String) : SpeechState()
        data class Error(val message: String) : SpeechState()
    }

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state

    private val _volumeLevel = MutableStateFlow(0f)
    val volumeLevel: StateFlow<Float> = _volumeLevel

    private var mAsr: ASR? = null
    private var audioRecorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private var resultCache = StringBuilder()

    // 音频参数
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private val asrCallbacks = object : AsrCallbacks {
        override fun onResult(asrResult: ASR.ASRResult?, userData: Any?) {
            asrResult?.let { result ->
                val status = result.status
                val text = result.bestMatchText ?: ""

                Log.d(TAG, "识别结果: status=$status, text=$text")

                when (status) {
                    0 -> {
                        // 第一块结果
                        resultCache.clear()
                        resultCache.append(text)
                    }
                    1 -> {
                        // 中间结果
                        resultCache.clear()
                        resultCache.append(text)
                    }
                    2 -> {
                        // 最终结果
                        resultCache.clear()
                        resultCache.append(text)
                        _state.value = SpeechState.Result(resultCache.toString())
                        stopListening()
                    }
                    else -> {
                        // 其他状态，忽略
                    }
                }
            }
        }

        override fun onError(asrError: ASR.ASRError?, userData: Any?) {
            asrError?.let { error ->
                val code = error.code
                val msg = error.errMsg ?: "未知错误"
                Log.e(TAG, "识别错误: code=$code, msg=$msg")
                _state.value = SpeechState.Error("识别失败: $msg")
                stopListening()
            }
        }

        override fun onBeginOfSpeech() {
            Log.d(TAG, "检测到语音开始")
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "检测到语音结束")
            _state.value = SpeechState.Processing
        }
    }

    /**
     * 开始语音识别
     */
    fun startListening() {
        if (isRecording.get()) {
            Log.w(TAG, "正在录音中，请勿重复开启")
            return
        }

        try {
            // 初始化ASR
            if (mAsr == null) {
                mAsr = ASR()
                mAsr?.registerCallbacks(asrCallbacks)
            }

            // 配置ASR参数
            mAsr?.apply {
                language("zh_cn")      // 中文
                domain("iat")          // 日常用语
                accent("mandarin")     // 普通话
                vinfo(true)            // 返回端点信息
                dwa("wpgs")            // 动态修正
            }

            // 启动ASR
            val ret = mAsr?.start(System.currentTimeMillis().toString()) ?: -1
            if (ret != 0) {
                _state.value = SpeechState.Error("启动识别失败，错误码: $ret")
                return
            }

            // 启动录音
            startRecording()
            _state.value = SpeechState.Listening
            resultCache.clear()

        } catch (e: Exception) {
            Log.e(TAG, "启动语音识别失败: ${e.message}")
            _state.value = SpeechState.Error("启动失败: ${e.message}")
        }
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        try {
            stopRecording()
            mAsr?.stop(false)

            if (_state.value is SpeechState.Listening) {
                _state.value = SpeechState.Processing
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止识别失败: ${e.message}")
        }
    }

    /**
     * 取消语音识别
     */
    fun cancel() {
        try {
            stopRecording()
            mAsr?.stop(true)
            _state.value = SpeechState.Idle
        } catch (e: Exception) {
            Log.e(TAG, "取消识别失败: ${e.message}")
        }
    }

    /**
     * 重置状态
     */
    fun resetState() {
        _state.value = SpeechState.Idle
    }

    /**
     * 释放资源
     */
    fun destroy() {
        cancel()
        mAsr = null
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        try {
            audioRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecorder?.state != AudioRecord.STATE_INITIALIZED) {
                _state.value = SpeechState.Error("录音器初始化失败")
                return
            }

            audioRecorder?.startRecording()
            isRecording.set(true)

            recordingThread = Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording.get()) {
                    val readSize = audioRecorder?.read(buffer, 0, bufferSize) ?: 0
                    if (readSize > 0) {
                        // 发送音频数据到ASR
                        mAsr?.write(buffer.copyOf(readSize))

                        // 计算音量
                        val volume = calculateVolume(buffer, readSize)
                        _volumeLevel.value = volume
                    }
                }
            }
            recordingThread?.start()

        } catch (e: Exception) {
            Log.e(TAG, "启动录音失败: ${e.message}")
            _state.value = SpeechState.Error("录音失败: ${e.message}")
        }
    }

    private fun stopRecording() {
        isRecording.set(false)
        _volumeLevel.value = 0f

        try {
            recordingThread?.join(500)
            recordingThread = null

            audioRecorder?.stop()
            audioRecorder?.release()
            audioRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "停止录音失败: ${e.message}")
        }
    }

    private fun calculateVolume(buffer: ByteArray, size: Int): Float {
        var sumSquares = 0.0
        val sampleCount = size / 2

        for (i in 0 until size step 2) {
            if (i + 1 < size) {
                val sample = ((buffer[i].toInt() and 0xFF) or
                        ((buffer[i + 1].toInt() and 0xFF) shl 8)).toShort()
                sumSquares += sample.toDouble() * sample.toDouble()
            }
        }

        val rms = kotlin.math.sqrt(sumSquares / sampleCount)

        // 转换为0-1范围
        return if (rms > 1e-10) {
            val db = 20 * kotlin.math.log10(rms / 32767.0)
            ((db + 60) / 40.0).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }
    }
}