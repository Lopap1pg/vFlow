// 文件: QuickViewModule.kt
// 描述: 定义了在工作流执行期间快速查看内容的模块。

package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * “快速查看”模块。
 * 弹出一个悬浮窗来显示文本、数字或任何其他变量的值。
 */
class QuickViewModule : BaseModule() {

    override val id = "vflow.data.quick_view"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_quick_view_name,
        descriptionStringRes = R.string.module_vflow_data_quick_view_desc,
        name = "快速查看",
        description = "在悬浮窗中显示文本、数字、图片等各种类型的内容。",
        iconRes = R.drawable.rounded_preview_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Show text, numbers, images, or other content in a quick overlay preview.",
        workflowStepDescription = "Show text, numbers, images, or other content in a quick overlay preview.",
        inputHints = mapOf(
            "content" to "Content to preview. It may be plain text, a variable value, or an image object."
        ),
        requiredInputIds = setOf("content")
    )

    override val requiredPermissions = listOf(PermissionManager.OVERLAY)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "content",
            nameStringRes = R.string.param_vflow_data_quick_view_content_name,
            name = "内容",
            staticType = ParameterType.ANY,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "success",
            "是否成功",
            VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_data_quick_view_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val rawContent = step.parameters["content"]?.toString() ?: ""

        if (VariableResolver.isComplex(rawContent)) {
            return PillUtil.buildSpannable(
                context,
                metadata.getLocalizedName(context),
                PillUtil.richTextPreview(rawContent)
            )
        }

        val contentPill = PillUtil.createPillFromParam(
            step.parameters["content"],
            getInputs().find { it.id == "content" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_system_quick_view_prefix), contentPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val uiService = context.services.get(ExecutionUIService::class)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_quick_view_service_missing),
                appContext.getString(R.string.error_vflow_data_quick_view_cannot_get_ui)
            )

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_data_quick_view_showing)))

        val contentRaw = context.getVariable("content")
        val title = metadata.getLocalizedName(appContext)

        if (contentRaw is VImage) {
            uiService.showQuickViewImage(title, contentRaw.uriString)
        } else {
            val contentAsString = contentRaw.asString()
            val resolvedContent = VariableResolver.resolve(contentAsString, context)
            val displayText = if (resolvedContent.isNullOrBlank()) {
                appContext.getString(R.string.quick_view_empty_value)
            } else {
                resolvedContent
            }
            uiService.showQuickView(title, displayText)
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_data_quick_view_closed)))
        return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
    }
}
