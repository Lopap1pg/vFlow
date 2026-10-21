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

/**
 * 读取NFC状态模块（Beta）。
 * 使用 vFlow Core 读取当前NFC开关状态。
 */
class CoreNfcStateModule : BaseModule() {

    override val id = "vflow.core.nfc_state"
    override val metadata = ActionMetadata(
        name = "读取NFC状态",
        nameStringRes = R.string.module_vflow_core_nfc_state_name,
        description = "使用 vFlow Core 读取当前NFC开关状态。",
        descriptionStringRes = R.string.module_vflow_core_nfc_state_desc,
        iconRes = R.drawable.rounded_nfc_24,
        category = "Core (Beta)",
        categoryId = "core"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.CORE)
    }

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("enabled", "NFC状态", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_nfc_state_enabled_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return context.getString(R.string.summary_vflow_core_nfc_state)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val connected = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connect(appContext)
        }
        if (!connected) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.core_nfc_state_not_connected_title),
                appContext.getString(R.string.core_nfc_state_not_connected_detail)
            )
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.core_nfc_state_reading)))

        val enabled = VFlowCoreBridge.isNfcEnabled()
        val stateText = if (enabled) {
            appContext.getString(R.string.core_nfc_state_enabled)
        } else {
            appContext.getString(R.string.core_nfc_state_disabled)
        }
        onProgress(ProgressUpdate(appContext.getString(R.string.core_nfc_state_result, stateText)))
        return ExecutionResult.Success(mapOf("enabled" to VBoolean(enabled)))
    }
}
