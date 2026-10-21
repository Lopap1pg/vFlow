// 文件: ShareModule.kt
// 描述: 定义了调用系统分享功能的模块。
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * “分享”模块。
 * 调用安卓系统的分享功能，可以分享文本或图片。
 */
class ShareModule : BaseModule() {

    override val id = "vflow.system.share"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_system_share_name,
        descriptionStringRes = R.string.module_vflow_system_share_desc,
        name = "分享",  // Fallback
        description = "调用系统分享功能来分享文本或图片。",  // Fallback
        iconRes = R.drawable.rounded_ios_share_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Open the Android share sheet for text or image content.",
        workflowStepDescription = "Open the Android share sheet for text or image content.",
        inputHints = mapOf(
            "content" to "Provide shareable text or an image object from a previous step."
        ),
        requiredInputIds = setOf("content")
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "content",
            nameStringRes = R.string.param_vflow_system_share_content_name,
            name = "分享内容",
            staticType = ParameterType.ANY,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id, VTypeRegistry.IMAGE.id),
            supportsRichText = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_system_share_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val contentPill = PillUtil.createPillFromParam(
            step.parameters["content"],
            getInputs().find { it.id == "content" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_system_share_prefix), contentPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val uiService = context.services.get(ExecutionUIService::class)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_share_service_missing),
                appContext.getString(R.string.error_vflow_system_share_cannot_get_ui)
            )

        val rawContent = context.getVariable("content")
        val content: Any? = when (rawContent) {
            is VImage -> rawContent
            is VNull -> null
            else -> {
                val strValue = context.getVariableAsString("content")
                if (strValue.isEmpty()) null else strValue
            }
        }

        if (content == null) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_share_empty),
                appContext.getString(R.string.error_vflow_system_share_no_content)
            )
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_share_preparing)))

        val success = uiService.requestShare(content)

        if (success == true) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_share_sent)))
        } else {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_share_failed)))
        }

        return ExecutionResult.Success(mapOf("success" to VBoolean(success ?: false)))
    }
}
