// 文件: ImportImageModule.kt
// 描述: 定义了从设备存储中导入图片的模块。

package com.chaomixian.vflow.core.workflow.module.file

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ExecutionUIService

/**
 * “导入图片”模块。
 * 唤起系统图片选择器，让用户选择一张图片，并将其作为 ImageVariable 输出。
 */
class ImportImageModule : BaseModule() {

    override val id = "vflow.file.import_image"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_file_import_image_name,
        descriptionStringRes = R.string.module_vflow_file_import_image_desc,
        name = "导入图片",
        description = "从相册或文件中选择一张图片。",
        iconRes = R.drawable.rounded_add_photo_alternate_24,
        category = "文件",
        categoryId = "file"
    )

    override val requiredPermissions = listOf(PermissionManager.STORAGE)

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "image",
            "选择的图片",
            VTypeRegistry.IMAGE.id,
            nameStringRes = R.string.output_vflow_file_import_image_image_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return context.getString(R.string.summary_vflow_file_import_image)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val uiService = context.services.get(ExecutionUIService::class)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.import_image_service_missing_title),
                appContext.getString(R.string.import_image_service_missing_detail)
            )

        onProgress(ProgressUpdate(appContext.getString(R.string.import_image_waiting_for_selection)))

        val imageUri = uiService.requestImage()
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.import_image_cancelled_title),
                appContext.getString(R.string.import_image_cancelled_detail)
            )

        val resultVariable = VImage(imageUri)

        onProgress(ProgressUpdate(appContext.getString(R.string.import_image_received)))
        return ExecutionResult.Success(mapOf("image" to resultVariable))
    }
}
