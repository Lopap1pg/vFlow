// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/WifiModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class WifiModule : BaseModule() {
    companion object {
        private const val STATE_ON = "on"
        private const val STATE_OFF = "off"
        private const val STATE_TOGGLE = "toggle"
    }

    override val id = "vflow.system.wifi"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_system_wifi_name,
        descriptionStringRes = R.string.module_vflow_system_wifi_desc,
        name = "Wi-Fi 设置",
        description = "开启、关闭或切换Wi-Fi状态。",
        iconRes = R.drawable.rounded_android_wifi_3_bar_24,
        category = "应用与系统",
        categoryId = "device"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Turn Wi-Fi on, off, or toggle it.",
        workflowStepDescription = "Change Wi-Fi state.",
        inputHints = mapOf(
            "state" to "Canonical values are on, off, or toggle.",
        ),
        requiredInputIds = setOf("state"),
    )

    private val stateOptions = listOf(STATE_ON, STATE_OFF, STATE_TOGGLE)

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "state",
            nameStringRes = R.string.param_vflow_system_wifi_state_name,
            name = "状态",
            staticType = ParameterType.ENUM,
            defaultValue = STATE_TOGGLE,
            options = stateOptions,
            acceptsMagicVariable = false,
            optionsStringRes = listOf(
                R.string.option_vflow_system_wifi_state_on,
                R.string.option_vflow_system_wifi_state_off,
                R.string.option_vflow_system_wifi_state_toggle
            ),
            legacyValueMap = mapOf(
                "开启" to STATE_ON,
                "On" to STATE_ON,
                "关闭" to STATE_OFF,
                "Off" to STATE_OFF,
                "切换" to STATE_TOGGLE,
                "Toggle" to STATE_TOGGLE
            )
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_system_wifi_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val statePill = PillUtil.createPillFromParam(
            step.parameters["state"],
            getInputs().find { it.id == "state" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, statePill, context.getString(R.string.summary_vflow_system_wifi_suffix))
    }

    @Suppress("DEPRECATION")
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val stateInput = getInputs().first { it.id == "state" }
        val rawState = context.getVariableAsString("state", STATE_TOGGLE)
        val state = stateInput.normalizeEnumValue(rawState) ?: rawState
        val localizedState = when (state) {
            STATE_ON -> appContext.getString(R.string.option_vflow_system_wifi_state_on)
            STATE_OFF -> appContext.getString(R.string.option_vflow_system_wifi_state_off)
            STATE_TOGGLE -> appContext.getString(R.string.option_vflow_system_wifi_state_toggle)
            else -> state
        }
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_wifi_trying, localizedState)))

        val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val targetState = when (state) {
            STATE_ON -> true
            STATE_OFF -> false
            STATE_TOGGLE -> !wifiManager.isWifiEnabled
            else -> return ExecutionResult.Failure(
                appContext.getString(R.string.module_vflow_system_wifi_name),
                appContext.getString(R.string.error_vflow_system_wifi_invalid_state, state)
            )
        }
        val command = if (targetState) "svc wifi enable" else "svc wifi disable"

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_wifi_shell)))
        val shellResult = ShellManager.execShellCommand(appContext, command, ShellManager.ShellMode.AUTO)

        if (!shellResult.startsWith("Error")) {
            return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        }

        DebugLogger.w("WifiModule", "Shell 执行失败: $shellResult，尝试回退到标准 API。")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_wifi_panel)))
            val intent = Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            val success = wifiManager.setWifiEnabled(targetState)
            return ExecutionResult.Success(mapOf("success" to VBoolean(success)))
        }
    }
}
