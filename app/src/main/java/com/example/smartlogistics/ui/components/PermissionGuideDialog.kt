package com.example.smartlogistics.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smartlogistics.ui.theme.TextSecondary

/**
 * 通用权限引导对话框
 *
 * @param showDialog 是否显示对话框
 * @param onDismiss 关闭对话框回调
 * @param permissionType 权限类型
 * @param context 上下文（用于跳转设置）
 * @param primaryColor 主题色
 */
@Composable
fun PermissionGuideDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    permissionType: PermissionType,
    context: Context,
    primaryColor: Color = Color(0xFF4CAF50)
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = permissionType.icon,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = permissionType.title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = permissionType.description,
                    textAlign = TextAlign.Center,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismiss()
                        // 跳转到应用设置页面
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("稍后再说", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * 权限类型枚举
 */
enum class PermissionType(
    val icon: ImageVector,
    val title: String,
    val description: String
) {
    LOCATION(
        icon = Icons.Default.LocationOn,
        title = "需要位置权限",
        description = "为了显示您的实时位置并提供精准导航服务，请允许访问位置权限。\n\n请前往设置 → 权限 → 位置，选择「始终允许」或「仅在使用时允许」。"
    ),
    CAMERA(
        icon = Icons.Default.CameraAlt,
        title = "需要相机权限",
        description = "为了拍照识别车牌或扫描二维码，请允许访问相机权限。\n\n请前往设置 → 权限 → 相机，开启权限。"
    ),
    STORAGE(
        icon = Icons.Default.Folder,
        title = "需要存储权限",
        description = "为了读取和保存图片文件，请允许访问存储权限。\n\n请前往设置 → 权限 → 存储，开启权限。"
    ),
    MICROPHONE(
        icon = Icons.Default.Mic,
        title = "需要麦克风权限",
        description = "为了使用语音识别功能，请允许访问麦克风权限。\n\n请前往设置 → 权限 → 麦克风，开启权限。"
    ),
    NOTIFICATION(
        icon = Icons.Default.Notifications,
        title = "需要通知权限",
        description = "为了及时推送航班、火车状态更新和重要提醒，请允许通知权限。\n\n请前往设置 → 通知，开启通知权限。"
    )
}