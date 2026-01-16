package com.example.smartlogistics.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 相机和相册工具类
 * 处理 Android 7.0+ 的 FileProvider 和权限问题
 */
object CameraUtils {

    /**
     * 创建用于拍照的临时文件 Uri
     * 统一使用 FileProvider，兼容性最好
     */
    fun createImageUri(context: Context): Uri? {
        return try {
            val imageFile = createImageFile(context)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // 备用方案：使用缓存目录
            try {
                val cacheFile = File(context.cacheDir, "IMG_${System.currentTimeMillis()}.jpg")
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
            } catch (e2: Exception) {
                e2.printStackTrace()
                null
            }
        }
    }

    /**
     * 创建图片文件
     */
    private fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "IMG_${timeStamp}_"
        
        // 优先使用应用的外部文件目录
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        
        return if (storageDir != null && (storageDir.exists() || storageDir.mkdirs())) {
            File.createTempFile(imageFileName, ".jpg", storageDir)
        } else {
            // 备用：使用内部缓存目录
            File.createTempFile(imageFileName, ".jpg", context.cacheDir)
        }
    }

    /**
     * 检查相机权限
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查存储权限（用于相册）
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
