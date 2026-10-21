// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/DarkModeModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 深色模式切换模块
 * 支持切换系统深色模式：自动/深色/浅色
 * 需要 Shizuku 或 Root 权限
 */
class DarkModeModule : BaseModule() {

    override val id = "vflow.system.darkmode"

    override val metadata = ActionMetadata(
        name = "深色模式",
        nameStringRes = R.string.module_vflow_system_darkmode_name,
        description = "切换系统的深色/浅色模式（自动/深色/浅色）",
        descriptionStringRes = R.string.module_vflow_system_darkmode_desc,
        iconRes = R.drawable.rounded_contrast_24,
        category = "应用与系统",
        categoryId = "device"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Set Android dark mode directly to toggle, dark, light, or auto. For requests such as switching to light mode or dark mode, use this tool instead of navigating through Settings screens.",
        workflowStepDescription = "Change Android dark mode.",
        inputHints = mapOf(
            "mode" to "Use canonical values: toggle, dark, light, or auto. Prefer dark/light/auto when the user names the target state explicitly.",
        ),
        requiredInputIds = setOf("mode"),
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(appContext)
    }

    companion object {
        const val MODE_AUTO = "auto"
        const val MODE_DARK = "dark"
        const val MODE_LIGHT = "light"
        const val MODE_TOGGLE = "toggle"
        private val MODE_LEGACY_MAP = mapOf(
            "切换" to MODE_TOGGLE,
            "深色" to MODE_DARK,
            "浅色" to MODE_LIGHT,
            "自动" to MODE_AUTO
        )
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "mode",
            name = "模式",
            nameStringRes = R.string.param_vflow_system_darkmode_mode_name,
            staticType = ParameterType.ENUM,
            defaultValue = MODE_TOGGLE,
            options = listOf(MODE_TOGGLE, MODE_DARK, MODE_LIGHT, MODE_AUTO),
            optionsStringRes = listOf(
                R.string.option_vflow_system_darkmode_toggle,
                R.string.option_vflow_system_darkmode_dark,
                R.string.option_vflow_system_darkmode_light,
                R.string.option_vflow_system_darkmode_auto
            ),
            legacyValueMap = MODE_LEGACY_MAP,
            inputStyle = InputStyle.CHIP_GROUP
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_darkmode_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = getInputs().normalizeEnumValue("mode", step.parameters["mode"] as? String, MODE_AUTO) ?: MODE_AUTO

        val displayText = when (mode) {
            MODE_DARK -> context.getString(R.string.option_vflow_system_darkmode_dark)
            MODE_LIGHT -> context.getString(R.string.option_vflow_system_darkmode_light)
            MODE_TOGGLE -> context.getString(R.string.option_vflow_system_darkmode_toggle)
            else -> context.getString(R.string.option_vflow_system_darkmode_auto)
        }

        val modePill = PillUtil.Pill(displayText, "mode", isModuleOption = true)
        return PillUtil.buildSpannable(context, "${metadata.getLocalizedName(context)}: ", modePill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val mode = getInputs().normalizeEnumValue("mode", context.getVariable("mode").asString(), MODE_AUTO) ?: MODE_AUTO

        val command = when (mode) {
            MODE_DARK -> "cmd uimode night yes"
            MODE_LIGHT -> "cmd uimode night no"
            MODE_AUTO -> "cmd uimode night auto"
            MODE_TOGGLE -> {
                val currentResult = ShellManager.execShellCommand(
                    appContext,
                    "settings get secure ui_night_mode"
                )
                if (currentResult.startsWith("Error")) {
                    "cmd uimode night yes"
                } else {
                    val currentValue = currentResult.trim().toIntOrNull() ?: 0
                    when (currentValue) {
                        1 -> "cmd uimode night yes"
                        2, 0, -1 -> "cmd uimode night no"
                        else -> "cmd uimode night yes"
                    }
                }
            }
            else -> null
        }

        if (command == null) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.dark_mode_parameter_error_title),
                appContext.getString(R.string.dark_mode_unsupported_mode, mode)
            )
        }

        val modeDisplayName = when (mode) {
            MODE_DARK -> appContext.getString(R.string.option_vflow_system_darkmode_dark)
            MODE_LIGHT -> appContext.getString(R.string.option_vflow_system_darkmode_light)
            MODE_TOGGLE -> appContext.getString(R.string.option_vflow_system_darkmode_toggle)
            else -> appContext.getString(R.string.option_vflow_system_darkmode_auto)
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.dark_mode_applying, modeDisplayName)))

        val result = ShellManager.execShellCommand(appContext, command)

        return if (result.startsWith("Error:")) {
            ExecutionResult.Failure(appContext.getString(R.string.dark_mode_switch_failed_title), result)
        } else {
            onProgress(ProgressUpdate(appContext.getString(R.string.dark_mode_completed)))
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        }
    }
}
