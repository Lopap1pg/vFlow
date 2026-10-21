// 文件: main/java/com/chaomixian/vflow/core/workflow/module/shizuku/ShellCommandModule.kt
package com.chaomixian.vflow.core.workflow.module.shizuku
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.*

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * "执行Shell命令" 模块。
 * 支持通过 自动/Shizuku/Root 模式执行。
 */
class ShellCommandModule : BaseModule() {
    companion object {
        private const val MODE_AUTO = "auto"
        private const val MODE_SHIZUKU = "shizuku"
        private const val MODE_ROOT = "root"
        private const val MODE_CORE = "core"
        private const val MODE_CORE_ROOT = "core_root"
    }

    override val id = "vflow.shizuku.shell_command"
    override val metadata = ActionMetadata(
        name = "执行Shell命令",
        nameStringRes = R.string.module_vflow_shizuku_shell_command_name,
        description = "通过 Shell 执行命令 (支持 Root/Shizuku)。",
        descriptionStringRes = R.string.module_vflow_shizuku_shell_command_desc,
        iconRes = R.drawable.rounded_terminal_24,
        category = "Shizuku",
        categoryId = "shizuku"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.HIGH,
        directToolDescription = "Run an arbitrary shell command through auto, Shizuku, root, Core, or Core Root mode. Use only when safer dedicated modules cannot complete the task.",
        workflowStepDescription = "Run an arbitrary shell command.",
        inputHints = mapOf(
            "mode" to "Preferred privilege mode. Leave auto unless the user explicitly requires Shizuku, root, Core, or Core Root.",
            "command" to "Exact shell command string to run.",
        ),
        requiredInputIds = setOf("command"),
    )

    private val modeOptions = listOf(MODE_AUTO, MODE_SHIZUKU, MODE_ROOT, MODE_CORE, MODE_CORE_ROOT)

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        val mode = step?.parameters?.get("mode") as? String ?: MODE_AUTO
        return when (mode) {
            MODE_ROOT -> listOf(PermissionManager.ROOT)
            MODE_SHIZUKU -> listOf(PermissionManager.SHIZUKU)
            MODE_CORE -> listOf(PermissionManager.CORE)
            MODE_CORE_ROOT -> listOf(PermissionManager.CORE_ROOT)
            else -> ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
        }
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "mode",
            name = "执行方式",
            staticType = ParameterType.ENUM,
            defaultValue = MODE_AUTO,
            options = modeOptions,
            acceptsMagicVariable = false,
            nameStringRes = R.string.param_vflow_shizuku_shell_command_mode_name,
            optionsStringRes = listOf(
                R.string.option_vflow_shizuku_shell_command_mode_auto,
                R.string.option_vflow_shizuku_shell_command_mode_shizuku,
                R.string.option_vflow_shizuku_shell_command_mode_root,
                R.string.option_vflow_shizuku_shell_command_mode_core,
                R.string.option_vflow_shizuku_shell_command_mode_core_root
            ),
            legacyValueMap = mapOf(
                "自动" to MODE_AUTO,
                "Auto" to MODE_AUTO,
                "Shizuku" to MODE_SHIZUKU,
                "Root" to MODE_ROOT
            )
        ),
        InputDefinition(
            id = "command",
            name = "命令",
            staticType = ParameterType.STRING,
            defaultValue = "echo 'Hello'",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            supportsRichText = true,
            nameStringRes = R.string.param_vflow_shizuku_shell_command_command_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "命令输出", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_shizuku_shell_command_result_name),
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_shizuku_shell_command_success_name),
        OutputDefinition("exit_code", "返回值", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_shizuku_shell_command_exit_code_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: MODE_AUTO
        val modeDisplay = getInputs()
            .find { it.id == "mode" }
            ?.let { input ->
                val normalizedMode = input.normalizeEnumValue(mode) ?: mode
                val index = input.options.indexOf(normalizedMode)
                input.getLocalizedOptions(context).getOrNull(index) ?: normalizedMode
            } ?: mode
        val commandPill = PillUtil.createPillFromParam(
            step.parameters["command"],
            getInputs().find { it.id == "command" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_shizuku_shell, modeDisplay), commandPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val modeInput = getInputs().first { it.id == "mode" }
        val rawModeStr = context.getVariableAsString("mode", MODE_AUTO)
        val modeStr = modeInput.normalizeEnumValue(rawModeStr) ?: rawModeStr
        val rawCommand = context.getVariableAsString("command", "")
        val command = VariableResolver.resolve(rawCommand, context)

        if (command.isBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.shell_command_parameter_error_title),
                appContext.getString(R.string.error_vflow_shizuku_shell_command_empty)
            )
        }

        val mode = when (modeStr) {
            MODE_ROOT -> ShellManager.ShellMode.ROOT
            MODE_SHIZUKU -> ShellManager.ShellMode.SHIZUKU
            MODE_CORE -> ShellManager.ShellMode.CORE
            MODE_CORE_ROOT -> ShellManager.ShellMode.CORE_ROOT
            else -> ShellManager.ShellMode.AUTO
        }
        val modeIndex = modeInput.options.indexOf(modeStr)
        val modeDisplay = modeInput.getLocalizedOptions(appContext).getOrNull(modeIndex) ?: modeStr

        onProgress(ProgressUpdate(appContext.getString(R.string.shell_command_executing, modeDisplay, command)))

        val result = ShellManager.execShellCommandWithResult(appContext, command, mode)

        return ExecutionResult.Success(mapOf(
            "result" to VString(result.output),
            "success" to VBoolean(result.success),
            "exit_code" to VNumber(result.exitCode)
        ))
    }
}
