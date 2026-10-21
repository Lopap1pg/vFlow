package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.VFlowCoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CoreHotspotStateModule : BaseModule() {
    override val id = "vflow.core.hotspot_state"
    override val metadata = ActionMetadata(
        name = "读取热点状态",
        nameStringRes = R.string.module_vflow_core_hotspot_state_name,
        description = "使用 vFlow Core 读取当前 Wi-Fi 热点开关状态。",
        descriptionStringRes = R.string.module_vflow_core_hotspot_state_desc,
        iconRes = R.drawable.rounded_wifi_tethering_24,
        category = "Core (Beta)",
        categoryId = "core"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> = listOf(PermissionManager.CORE_ROOT)
    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("enabled", "热点状态", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_hotspot_state_enabled_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence =
        context.getString(R.string.summary_vflow_core_hotspot_state)

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

        if (VFlowCoreBridge.privilegeMode != VFlowCoreBridge.PrivilegeMode.ROOT) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_core_hotspot_permission_denied),
                appContext.getString(R.string.error_vflow_core_hotspot_root_required)
            )
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.core_hotspot_state_reading)))
        val enabled = VFlowCoreBridge.isHotspotEnabled()
        val stateText = if (enabled) {
            appContext.getString(R.string.core_runtime_state_enabled)
        } else {
            appContext.getString(R.string.core_runtime_state_disabled)
        }
        onProgress(ProgressUpdate(appContext.getString(R.string.core_hotspot_state_result, stateText)))
        return ExecutionResult.Success(mapOf("enabled" to VBoolean(enabled)))
    }
}
