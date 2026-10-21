package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import android.util.Base64
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import java.io.File

/**
 * 截屏模块（Core）。
 * 使用 vFlow Core 捕获屏幕截图，基于scrcpy原理实现。
 * 截图会自动保存到工作流临时目录，并返回VImage对象。
 */
class CoreCaptureScreenModule : BaseModule() {

    override val id = "vflow.core.capture_screen"
    override val metadata = ActionMetadata(
        name = "截屏",
        nameStringRes = R.string.module_vflow_core_capture_screen_name,
        description = "使用 vFlow Core 捕获屏幕截图，基于scrcpy原理实现。",
        descriptionStringRes = R.string.module_vflow_core_capture_screen_desc,
        iconRes = R.drawable.rounded_fullscreen_portrait_24,
        category = "Core (Beta)",
        categoryId = "core"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        directToolDescription = "Capture a screenshot through vFlow Core. Prefer this when the user explicitly wants the Core capture path.",
        workflowStepDescription = "Capture a screenshot through vFlow Core.",
    )

    private val outputFormatOptions = listOf("PNG", "JPEG")

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> = listOf(PermissionManager.CORE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("output_format", "输出格式", ParameterType.ENUM, "PNG", options = outputFormatOptions, acceptsMagicVariable = false, nameStringRes = R.string.param_vflow_core_capture_screen_output_format_name),
        InputDefinition("quality", "JPEG质量", ParameterType.NUMBER, 90.0, acceptsMagicVariable = false, isHidden = false, nameStringRes = R.string.param_vflow_core_capture_screen_quality_name),
        InputDefinition("max_width", "最大宽度", ParameterType.NUMBER, 0.0, acceptsMagicVariable = false, isHidden = false, nameStringRes = R.string.param_vflow_core_capture_screen_max_width_name),
        InputDefinition("max_height", "最大高度", ParameterType.NUMBER, 0.0, acceptsMagicVariable = false, isHidden = false, nameStringRes = R.string.param_vflow_core_capture_screen_max_height_name)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_capture_screen_success_name),
        OutputDefinition("image", "截图图片", VTypeRegistry.IMAGE.id, nameStringRes = R.string.output_vflow_core_capture_screen_image_name),
        OutputDefinition("width", "图像宽度", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_core_capture_screen_width_name),
        OutputDefinition("height", "图像高度", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_core_capture_screen_height_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val format = step.parameters["output_format"] as? String ?: "PNG"
        return context.getString(R.string.summary_vflow_core_capture_screen, format)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val connected = withContext(Dispatchers.IO) { VFlowCoreBridge.connect(appContext) }
        if (!connected) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.core_runtime_not_connected_title),
                appContext.getString(R.string.core_runtime_not_connected_detail)
            )
        }

        val outputFormat = context.getVariableAsString("output_format", "PNG")
        val quality = context.getVariableAsInt("quality") ?: 90
        val maxWidth = context.getVariableAsInt("max_width") ?: 0
        val maxHeight = context.getVariableAsInt("max_height") ?: 0
        val format = outputFormat.lowercase()

        onProgress(ProgressUpdate(appContext.getString(R.string.core_capture_screen_progress)))

        val result = withContext(Dispatchers.IO) {
            captureAndSaveToWorkDir(appContext, format, quality, maxWidth, maxHeight, context.workDir)
        }

        return when (result) {
            is CaptureResult.Success -> {
                onProgress(ProgressUpdate(appContext.getString(R.string.core_capture_screen_success)))
                val imageUri = "file://${result.filePath}"
                ExecutionResult.Success(
                    mapOf(
                        "success" to VBoolean(true),
                        "image" to VImage(imageUri),
                        "width" to VNumber(result.width.toDouble()),
                        "height" to VNumber(result.height.toDouble())
                    )
                )
            }
            is CaptureResult.Error -> ExecutionResult.Failure(
                appContext.getString(R.string.core_capture_screen_failed_title),
                result.message
            )
        }
    }

    private fun captureAndSaveToWorkDir(
        context: Context,
        format: String,
        quality: Int,
        maxWidth: Int,
        maxHeight: Int,
        workDir: File
    ): CaptureResult {
        return try {
            val base64Data = VFlowCoreBridge.captureScreenEx(format, quality, maxWidth, maxHeight)
            if (base64Data.isEmpty()) {
                return CaptureResult.Error(context.getString(R.string.core_capture_screen_no_data))
            }

            val fileName = "screenshot_${System.currentTimeMillis()}.$format"
            val file = File(workDir, fileName)
            val imageBytes = Base64.decode(base64Data, Base64.NO_WRAP)
            file.writeBytes(imageBytes)
            val dimensions = VFlowCoreBridge.getScreenSize()

            CaptureResult.Success(
                width = dimensions.first,
                height = dimensions.second,
                filePath = file.absolutePath
            )
        } catch (e: Exception) {
            CaptureResult.Error(
                context.getString(
                    R.string.core_capture_screen_failed_detail,
                    e.localizedMessage ?: context.getString(R.string.error_unknown_error)
                )
            )
        }
    }

    private sealed class CaptureResult {
        data class Success(val width: Int, val height: Int, val filePath: String) : CaptureResult()
        data class Error(val message: String) : CaptureResult()
    }
}
