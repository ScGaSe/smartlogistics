package com.example.smartlogistics.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 语音识别工具类
 * 封装Android SpeechRecognizer，提供简单的语音转文字功能
 */
class SpeechHelper(private val context: Context) {

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

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = SpeechState.Error("设备不支持语音识别")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = SpeechState.Listening
            }

            override fun onBeginningOfSpeech() {
                // 用户开始说话
            }

            override fun onRmsChanged(rmsdB: Float) {
                // 音量变化，用于显示动画
                // rmsdB 范围大约 -2 到 10
                val normalized = ((rmsdB + 2) / 12f).coerceIn(0f, 1f)
                _volumeLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = SpeechState.Processing
                _volumeLevel.value = 0f
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "录音错误"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足，请授予录音权限"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_NO_MATCH -> "未能识别语音"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务繁忙"
                    SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音输入"
                    else -> "未知错误"
                }
                _state.value = SpeechState.Error(errorMessage)
                _volumeLevel.value = 0f
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                _state.value = if (text.isNotEmpty()) {
                    SpeechState.Result(text)
                } else {
                    SpeechState.Error("未能识别语音")
                }
                _volumeLevel.value = 0f
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // 可选：处理部分识别结果，实现实时显示
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotEmpty()) {
                    // 可以通过另一个StateFlow发送部分结果
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    /**
     * 开始语音识别
     */
    fun startListening() {
        if (speechRecognizer == null) {
            initRecognizer()
        }
        _state.value = SpeechState.Listening
        speechRecognizer?.startListening(recognizerIntent)
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        _volumeLevel.value = 0f
    }

    /**
     * 取消语音识别
     */
    fun cancel() {
        speechRecognizer?.cancel()
        _state.value = SpeechState.Idle
        _volumeLevel.value = 0f
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
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    companion object {
        // 检查是否支持语音识别
        fun isAvailable(context: Context): Boolean {
            return SpeechRecognizer.isRecognitionAvailable(context)
        }
    }
}