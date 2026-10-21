package com.chaomixian.vflow.core.workflow.module.core

import android.app.KeyguardManager
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
 * 读取屏幕状态模块（Beta）。
 * 使用 vFlow Core 读取当前屏幕唤醒状态，并结合系统服务判断是否已解锁。
 */
class CoreScreenStatusModule : BaseModule() {

    override val id = "vflow.core.screen_status"
    override val metadata = ActionMetadata(
        name = "读取屏幕状态",
        nameStringRes = R.string.module_vflow_core_screen_status_name,
        description = "使用 vFlow Core 获取屏幕是否已唤醒，以及系统是否已解锁。",
        descriptionStringRes = R.string.module_vflow_core_screen_status_desc,
        iconRes = R.drawable.rounded_fullscreen_portrait_24,
        category = "Core (Beta)",
        categoryId = "core"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        directToolDescription = "Read whether the screen is awake and whether the device is unlocked.",
        workflowStepDescription = "Read screen wake and unlock state.",
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> = listOf(PermissionManager.CORE)
    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("enabled", "屏幕状态", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_screen_status_enabled_name),
        OutputDefinition("screen_on", "屏幕是否已唤醒", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_screen_status_screen_on_name),
        OutputDefinition("unlocked", "系统是否已解锁", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_screen_status_unlocked_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence =
        context.getString(R.string.summary_vflow_core_screen_status)

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

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_screen_status_reading)))
        val isScreenOn = VFlowCoreBridge.isInteractive()
        val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isUnlocked = !keyguardManager.isDeviceLocked

        onProgress(
            ProgressUpdate(
                appContext.getString(
                    R.string.msg_vflow_core_screen_status_result,
                    if (isScreenOn) appContext.getString(R.string.value_vflow_core_screen_status_screen_on) else appContext.getString(R.string.value_vflow_core_screen_status_screen_off),
                    if (isUnlocked) appContext.getString(R.string.value_vflow_core_screen_status_unlocked) else appContext.getString(R.string.value_vflow_core_screen_status_locked)
                )
            )
        )
        return ExecutionResult.Success(
            mapOf(
                "enabled" to VBoolean(isScreenOn),
                "screen_on" to VBoolean(isScreenOn),
                "unlocked" to VBoolean(isUnlocked)
            )
        )
    }
}
