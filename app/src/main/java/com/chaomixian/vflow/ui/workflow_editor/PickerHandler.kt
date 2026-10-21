// 文件: PickerHandler.kt
// 描述: 处理各种 PickerType 的选择逻辑
package com.chaomixian.vflow.ui.workflow_editor

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.PickerType
import com.chaomixian.vflow.core.utils.StorageManager
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.app_picker.AppPickerMode
import com.chaomixian.vflow.ui.app_picker.UnifiedAppPickerSheet
import com.chaomixian.vflow.ui.overlay.RegionSelectionOverlay
import com.chaomixian.vflow.ui.shortcut_picker.UnifiedShortcutPickerSheet
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Picker 类型处理Handler
 */
class PickerHandler(
    private val activity: AppCompatActivity,
    private val appPickerLauncher: ActivityResultLauncher<Intent>,
    private val filePickerLauncher: ActivityResultLauncher<Intent>,
    private val mediaPickerLauncher: ActivityResultLauncher<Intent>,
    private val directoryPickerLauncher: ActivityResultLauncher<Uri?>,
    private val generalIntentLauncher: ActivityResultLauncher<Intent>,
    private val onUpdateParameters: (Map<String, Any?>) -> Unit
) {
    // 当前正在处理的输入定义，用于结果回调
    private var currentInputDef: InputDefinition? = null

    // 文件选择器的 pending 输入定义（防止被其他操作清空）
    private var pendingFileInputDef: InputDefinition? = null

    // 通用 Intent 的回调（用于 ModuleUIProvider 启动的系统选择器）
    private var generalIntentCallback: ((resultCode: Int, data: Intent?) -> Unit)? = null

    /**
     * 处理 Picker 请求
     */
    fun handle(inputDef: InputDefinition) {
        currentInputDef = inputDef
        when (inputDef.pickerType) {
            PickerType.APP -> handleAppPicker()
            PickerType.ACTIVITY -> handleActivityPicker()
            PickerType.DATE -> handleDatePicker(inputDef)
            PickerType.TIME -> handleTimePicker(inputDef)
            PickerType.DATETIME -> handleDateTimePicker(inputDef)
            PickerType.FILE -> handleFilePicker(inputDef)
            PickerType.DIRECTORY -> handleDirectoryPicker(inputDef)
            PickerType.MEDIA -> handleMediaPicker(inputDef)
            PickerType.SCREEN_REGION -> handleScreenRegionPicker(inputDef)
            PickerType.NONE -> { currentInputDef = null }
        }
    }

    /**
     * 启动 Intent 并获取结果（统一入口）
     *
     * 用途：处理 ModuleUIProvider 中需要启动选择器的场景
     * - 自动检测应用选择器 Intent（包含 EXTRA_MODE）
     * - 自动处理系统标准 Intent（ACTION_PICK、ACTION_GET_CONTENT 等）
     *
     * @param intent 要启动的 Intent
     * @param callback 结果回调 (resultCode, data)
     */
    fun launchIntentForResult(
        intent: Intent,
        callback: (resultCode: Int, data: Intent?) -> Unit
    ) {
        // 检查是否为应用选择器 Intent
        if (intent.hasExtra(UnifiedAppPickerSheet.EXTRA_MODE)) {
            val mode = try {
                AppPickerMode.valueOf(intent.getStringExtra(UnifiedAppPickerSheet.EXTRA_MODE) ?: "SELECT_ACTIVITY")
            } catch (e: Exception) {
                AppPickerMode.SELECT_ACTIVITY
            }

            val pickerSheet = UnifiedAppPickerSheet.newInstance(mode)
            pickerSheet.setOnResultCallback { result ->
                handleAppPickerResult(Activity.RESULT_OK, result)
                callback(Activity.RESULT_OK, result)
            }
            pickerSheet.show(activity.supportFragmentManager, "UnifiedAppPicker")
        } else if (intent.getBooleanExtra("shortcut_picker", false)) {
            val pickerSheet = UnifiedShortcutPickerSheet.newInstance()
            pickerSheet.setOnResultCallback { result ->
                callback(Activity.RESULT_OK, result)
            }
            pickerSheet.show(activity.supportFragmentManager, "UnifiedShortcutPicker")
        } else {
            // 系统标准 Intent（图片选择、音频选择等）
            generalIntentCallback = callback
            generalIntentLauncher.launch(intent)
        }
    }

    /**
     * 处理应用选择结果
     */
    fun handleAppPickerResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            // 兼容新旧选择器的 Extra Key
            val packageName = data.getStringExtra(UnifiedAppPickerSheet.EXTRA_SELECTED_PACKAGE_NAME)
            val activityName = data.getStringExtra(UnifiedAppPickerSheet.EXTRA_SELECTED_ACTIVITY_NAME)

            when (currentInputDef?.pickerType) {
                PickerType.ACTIVITY -> {
                    if (packageName != null && activityName != null) {
                        val value = "$packageName/$activityName"
                        onUpdateParameters(mapOf(currentInputDef!!.id to value))
                    }
                }
                PickerType.APP -> {
                    if (packageName != null) {
                        onUpdateParameters(mapOf(currentInputDef!!.id to packageName))
                    }
                }
                else -> {}
            }
        }
        currentInputDef = null
    }

    /**
     * 处理文件选择结果
     */
    fun handleFilePickerResult(uri: Uri?, data: Intent? = null) {
        // 优先使用 currentInputDef，如果为空则使用 pendingFileInputDef
        val inputDef = currentInputDef ?: pendingFileInputDef
        if (uri != null && inputDef != null) {
            StableFilePathResolver.persistReadPermissionIfPossible(activity, uri, data)
            val value = StableFilePathResolver.resolveFilePathOrUri(activity, uri)
            onUpdateParameters(mapOf(inputDef.id to value))
        } else if (uri == null) {
            android.util.Log.d("PickerHandler", "文件选择已取消或失败")
        } else if (inputDef == null) {
            android.util.Log.e("PickerHandler", "无法找到输入定义来处理文件选择结果")
        }
        // 清理状态
        currentInputDef = null
        pendingFileInputDef = null
    }

    /**
     * 处理目录选择结果
     */
    fun handleDirectoryPickerResult(uri: Uri?) {
        val inputDef = currentInputDef ?: pendingFileInputDef
        if (uri != null && inputDef != null) {
            val value = StableFilePathResolver.resolveDirectoryPath(activity, uri)
            if (value != null) {
                onUpdateParameters(mapOf(inputDef.id to value))
                android.util.Log.d("PickerHandler", "目录选择成功: $value")
            } else {
                Toast.makeText(activity, R.string.picker_local_directory_only, Toast.LENGTH_SHORT).show()
            }
        } else if (uri == null) {
            android.util.Log.d("PickerHandler", "目录选择已取消")
        } else if (inputDef == null) {
            android.util.Log.e("PickerHandler", "无法找到输入定义来处理目录选择结果")
        }
        // 清理状态
        currentInputDef = null
        pendingFileInputDef = null
    }

    /**
     * 处理媒体选择结果
     */
    fun handleMediaPickerResult(uri: Uri?) {
        // 优先使用 currentInputDef，如果为空则使用 pendingFileInputDef
        val inputDef = currentInputDef ?: pendingFileInputDef
        if (uri != null && inputDef != null) {
            val value = uri.toString()
            onUpdateParameters(mapOf(inputDef.id to value))
        } else if (uri == null) {
            android.util.Log.d("PickerHandler", "媒体选择已取消或失败")
        } else if (inputDef == null) {
            android.util.Log.e("PickerHandler", "无法找到输入定义来处理媒体选择结果")
        }
        // 清理状态
        currentInputDef = null
        pendingFileInputDef = null
    }

    private fun handleAppPicker() {
        // 使用新的统一选择器 BottomSheet
        val pickerSheet = UnifiedAppPickerSheet.newInstance(AppPickerMode.SELECT_APP)
        pickerSheet.setOnResultCallback { result ->
            handleAppPickerResult(Activity.RESULT_OK, result)
        }
        pickerSheet.show(activity.supportFragmentManager, "UnifiedAppPicker")
    }

    private fun handleActivityPicker() {
        // 使用新的统一选择器 BottomSheet
        val pickerSheet = UnifiedAppPickerSheet.newInstance(AppPickerMode.SELECT_ACTIVITY)
        pickerSheet.setOnResultCallback { result ->
            handleAppPickerResult(Activity.RESULT_OK, result)
        }
        pickerSheet.show(activity.supportFragmentManager, "UnifiedAppPicker")
    }

    private fun handleDatePicker(inputDef: InputDefinition) {
        val currentDate = parseDate(getCurrentValue(inputDef))
        val fragmentManager = activity.supportFragmentManager

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(activity.getString(R.string.picker_select_date))
            .setSelection(currentDate?.toEpochDay()?.times(24 * 60 * 60 * 1000))
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = java.time.Instant.ofEpochMilli(selection)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            onUpdateParameters(mapOf(inputDef.id to date.toString()))
        }

        datePicker.show(fragmentManager, "DatePicker")
    }

    private fun handleTimePicker(inputDef: InputDefinition) {
        val currentTime = parseTime(getCurrentValue(inputDef))
        val fragmentManager = activity.supportFragmentManager

        val timePicker = MaterialTimePicker.Builder()
            .setHour(currentTime?.hour ?: 12)
            .setMinute(currentTime?.minute ?: 0)
            .setTitleText(activity.getString(R.string.picker_select_time))
            .build()

        timePicker.addOnPositiveButtonClickListener {
            val time = String.format("%02d:%02d", timePicker.hour, timePicker.minute)
            onUpdateParameters(mapOf(inputDef.id to time))
        }

        timePicker.show(fragmentManager, "TimePicker")
    }

    private fun handleDateTimePicker(inputDef: InputDefinition) {
        val currentDateTime = parseDateTime(getCurrentValue(inputDef))
        val fragmentManager = activity.supportFragmentManager

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(activity.getString(R.string.picker_select_datetime))
            .setSelection(
                (currentDateTime?.toLocalDate()?.toEpochDay() ?: LocalDate.now().toEpochDay())
                    .times(24 * 60 * 60 * 1000)
            )
            .build()

        datePicker.addOnPositiveButtonClickListener { dateSelection ->
            val localDate = java.time.Instant.ofEpochMilli(dateSelection)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            val timePicker = MaterialTimePicker.Builder()
                .setHour(currentDateTime?.hour ?: 12)
                .setMinute(currentDateTime?.minute ?: 0)
                .setTitleText(activity.getString(R.string.picker_select_time))
                .build()

            timePicker.addOnPositiveButtonClickListener {
                val dateTime = localDate.atTime(timePicker.hour, timePicker.minute)
                onUpdateParameters(mapOf(inputDef.id to dateTime.toString()))
            }

            timePicker.show(fragmentManager, "TimePicker")
        }

        datePicker.show(fragmentManager, "DatePicker")
    }

    private fun handleFilePicker(inputDef: InputDefinition) {
        // 保存 pending 输入定义，防止在文件选择过程中被其他操作清空
        pendingFileInputDef = inputDef
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        filePickerLauncher.launch(intent)
    }

    private fun handleDirectoryPicker(inputDef: InputDefinition) {
        // 保存 pending 输入定义，防止在目录选择过程中被其他操作清空
        pendingFileInputDef = inputDef
        // 使用 OpenDocumentTree 选择目录
        directoryPickerLauncher.launch(null)
    }

    private fun handleMediaPicker(inputDef: InputDefinition) {
        // 保存 pending 输入定义，防止在媒体选择过程中被其他操作清空
        pendingFileInputDef = inputDef
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        mediaPickerLauncher.launch(intent)
    }

    private fun handleScreenRegionPicker(inputDef: InputDefinition) {
        if (!PermissionManager.isGranted(activity, PermissionManager.OVERLAY)) {
            Toast.makeText(activity, R.string.toast_vflow_system_capture_screen_overlay_permission_required, Toast.LENGTH_SHORT).show()
            currentInputDef = null
            return
        }

        val shellPermissions = ShellManager.getRequiredPermissions(activity)
        val hasShellPermission = shellPermissions.all { PermissionManager.isGranted(activity, it) }
        if (!hasShellPermission) {
            Toast.makeText(activity, R.string.toast_vflow_system_capture_screen_shell_permission_required, Toast.LENGTH_SHORT).show()
            currentInputDef = null
            return
        }

        activity.lifecycleScope.launch {
            try {
                val overlay = RegionSelectionOverlay(activity, StorageManager.tempDir)
                val result = overlay.captureAndSelectRegion()
                if (result != null) {
                    onUpdateParameters(mapOf(inputDef.id to result.region))
                }
            } catch (e: Exception) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.toast_vflow_system_capture_screen_region_select_failed, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                currentInputDef = null
            }
        }
    }

    private var getCurrentValue: (InputDefinition) -> Any? = { null }

    fun setGetCurrentValueCallback(callback: (InputDefinition) -> Any?) {
        getCurrentValue = callback
    }

    private fun parseDate(value: Any?): LocalDate? {
        return try {
            (value as? String)?.let { LocalDate.parse(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTime(value: Any?): LocalTime? {
        return try {
            (value as? String)?.let { LocalTime.parse(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDateTime(value: Any?): LocalDateTime? {
        return try {
            (value as? String)?.let { LocalDateTime.parse(it) }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val REQUEST_FILE_PICKER = 1001
        const val REQUEST_MEDIA_PICKER = 1002
    }

    /**
     * 处理通用 Intent 的结果
     */
    fun handleIntentResult(resultCode: Int, data: Intent?) {
        generalIntentCallback?.invoke(resultCode, data)
        generalIntentCallback = null
    }
}
