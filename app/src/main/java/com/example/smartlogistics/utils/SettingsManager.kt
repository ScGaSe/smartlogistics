package com.example.smartlogistics.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 设置管理器
 * 管理用户本地设置（昵称、头像等）
 */
class SettingsManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    companion object {
        private const val PREFS_NAME = "app_settings"
        
        // 用户设置
        private const val KEY_NICKNAME = "user_nickname"
        private const val KEY_AVATAR_PATH = "user_avatar_path"
        
        // 系统设置
        private const val KEY_AUTO_UPDATE = "auto_update"
        
        @Volatile
        private var INSTANCE: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // ==================== 用户信息 ====================
    
    /**
     * 保存昵称
     */
    fun saveNickname(nickname: String) {
        prefs.edit().putString(KEY_NICKNAME, nickname).apply()
    }
    
    /**
     * 获取昵称（默认为空）
     */
    fun getNickname(): String? {
        return prefs.getString(KEY_NICKNAME, null)
    }
    
    /**
     * 保存头像（从 Uri 复制到应用私有目录）
     */
    fun saveAvatar(uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap != null) {
                val avatarFile = File(context.filesDir, "avatar.jpg")
                val outputStream = FileOutputStream(avatarFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.close()
                bitmap.recycle()
                
                prefs.edit().putString(KEY_AVATAR_PATH, avatarFile.absolutePath).apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 获取头像文件路径
     */
    fun getAvatarPath(): String? {
        val path = prefs.getString(KEY_AVATAR_PATH, null)
        if (path != null && File(path).exists()) {
            return path
        }
        return null
    }
    
    /**
     * 获取头像 Bitmap
     */
    fun getAvatarBitmap(): android.graphics.Bitmap? {
        val path = getAvatarPath() ?: return null
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
    }
    
    // ==================== 系统设置 ====================
    
    var autoUpdate: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UPDATE, value).apply()
    
    // ==================== 缓存管理 ====================
    
    fun getCacheSize(): String {
        val cacheDir = context.cacheDir
        val externalCacheDir = context.externalCacheDir
        
        var totalSize = getFolderSize(cacheDir)
        externalCacheDir?.let { totalSize += getFolderSize(it) }
        
        return formatFileSize(totalSize)
    }
    
    fun clearCache(): Boolean {
        return try {
            deleteDir(context.cacheDir)
            context.externalCacheDir?.let { deleteDir(it) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun getFolderSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return size
    }
    
    private fun deleteDir(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return true
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { deleteDir(it) }
        }
        return dir.delete()
    }
    
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> String.format("%.1fKB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1fMB", size / (1024.0 * 1024))
            else -> String.format("%.1fGB", size / (1024.0 * 1024 * 1024))
        }
    }
    
    fun clearUserSettings() {
        getAvatarPath()?.let { File(it).delete() }
        prefs.edit()
            .remove(KEY_NICKNAME)
            .remove(KEY_AVATAR_PATH)
            .apply()
    }
}
