package com.example.smartlogistics.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 停车记录管理器
 * 用于持久化保存停车记录（照片、位置）
 */
class ParkingManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "parking_assistant_prefs"
        private const val KEY_PARKING_RECORDS = "parking_records"
    }

    /**
     * 停车记录数据类（用于JSON序列化）
     */
    data class ParkingRecordData(
        val id: Long,
        val photoUriString: String?,  // Uri转String保存
        val latitude: Double?,
        val longitude: Double?,
        val address: String?,
        val timestamp: Long,
        val type: String
    )

    /**
     * 保存停车记录列表
     */
    fun saveRecords(records: List<ParkingRecordData>) {
        val json = gson.toJson(records)
        prefs.edit().putString(KEY_PARKING_RECORDS, json).apply()
    }

    /**
     * 获取停车记录列表
     */
    fun getRecords(): List<ParkingRecordData> {
        val json = prefs.getString(KEY_PARKING_RECORDS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ParkingRecordData>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 添加一条记录
     */
    fun addRecord(record: ParkingRecordData) {
        val records = getRecords().toMutableList()
        records.add(0, record)  // 添加到最前面
        saveRecords(records)
    }

    /**
     * 删除一条记录
     */
    fun deleteRecord(id: Long) {
        val records = getRecords().filter { it.id != id }
        saveRecords(records)
    }

    /**
     * 清空所有记录
     */
    fun clearRecords() {
        prefs.edit().remove(KEY_PARKING_RECORDS).apply()
    }
}