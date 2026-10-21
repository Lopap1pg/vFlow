package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager

class CoreBluetoothStateModule : BaseModule() {

    override val id = "vflow.core.bluetooth_state"
    override val metadata = ActionMetadata(
        name = "读取蓝牙状态",
        description = "使用 vFlow Core 读取当前蓝牙开关状态。",
        iconRes = R.drawable.rounded_bluetooth_24,
        category = "Core (Beta)",
        categoryId = "core",
        nameStringRes = R.string.module_vflow_core_bluetooth_state_name,
        descriptionStringRes = R.string.module_vflow_core_bluetooth_state_desc
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        directToolDescription = "Read the current Bluetooth enabled state through vFlow Core.",
        workflowStepDescription = "Read Bluetooth state through vFlow Core.",
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> = listOf(PermissionManager.CORE)
    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("enabled", "蓝牙状态", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_bluetooth_state_enabled_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence =
        context.getString(R.string.summary_vflow_core_bluetooth_state)

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

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_bluetooth_state_reading)))
        val enabled = VFlowCoreBridge.isBluetoothEnabled()
        val statusText = if (enabled) {
            appContext.getString(R.string.msg_vflow_core_bluetooth_state_enabled)
        } else {
            appContext.getString(R.string.msg_vflow_core_bluetooth_state_disabled)
        }
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_bluetooth_state_changed, statusText)))
        return ExecutionResult.Success(mapOf("enabled" to VBoolean(enabled)))
    }
}
