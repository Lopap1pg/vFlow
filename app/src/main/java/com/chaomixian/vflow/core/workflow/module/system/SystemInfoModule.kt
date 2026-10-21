package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.os.Build
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class SystemInfoModule : BaseModule() {
    companion object {
        private const val INFO_MODEL = "model"
        private const val INFO_BRAND = "brand"
        private const val INFO_ANDROID_VERSION = "android_version"
        private const val INFO_SDK = "sdk"
        private const val INFO_SECURITY_PATCH = "security_patch"
    }

    override val id = "vflow.system.systeminfo"

    override val metadata: ActionMetadata = ActionMetadata(
        name = "获取系统信息",
        nameStringRes = R.string.module_vflow_system_systeminfo_name,
        description = "获取当前系统的信息。",
        descriptionStringRes = R.string.module_vflow_system_systeminfo_desc,
        iconRes = R.drawable.baseline_perm_device_information_24,
        category = "应用与系统",
        categoryId = "device"
    )
    override val aiMetadata = AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        workflowStepDescription = "Read a device system info field such as model, brand, Android version, SDK, or security patch.",
        requiredInputIds = setOf("infotype"),
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "infotype",
            name = "信息类型",
            nameStringRes = R.string.param_vflow_system_systeminfo_type_name,
            staticType = ParameterType.ENUM,
            options = listOf(INFO_MODEL, INFO_BRAND, INFO_ANDROID_VERSION, INFO_SDK, INFO_SECURITY_PATCH),
            optionsStringRes = listOf(
                R.string.option_vflow_system_systeminfo_model,
                R.string.option_vflow_system_systeminfo_brand,
                R.string.option_vflow_system_systeminfo_android_version,
                R.string.option_vflow_system_systeminfo_sdk,
                R.string.option_vflow_system_systeminfo_security_patch
            ),
            legacyValueMap = mapOf(
                "设备型号" to INFO_MODEL,
                "Model" to INFO_MODEL,
                "设备厂商" to INFO_BRAND,
                "Brand" to INFO_BRAND,
                "安卓版本" to INFO_ANDROID_VERSION,
                "Android Version" to INFO_ANDROID_VERSION,
                "SDK版本" to INFO_SDK,
                "SDK Version" to INFO_SDK,
                "安全补丁" to INFO_SECURITY_PATCH,
                "Security Patch" to INFO_SECURITY_PATCH
            ),
            defaultValue = INFO_MODEL,
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "sysinfo",
            name = "系统信息",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_system_systeminfo_value_name
        )
    )

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val infoTypeInput = getInputs().first { it.id == "infotype" }
        val rawInfoType = context.getVariableAsString("infotype", INFO_MODEL)
        val infoType = infoTypeInput.normalizeEnumValue(rawInfoType) ?: rawInfoType
        val resultValue: String = when (infoType) {
            INFO_MODEL -> Build.MODEL
            INFO_BRAND -> Build.BRAND
            INFO_ANDROID_VERSION -> Build.VERSION.RELEASE
            INFO_SDK -> Build.VERSION.SDK_INT.toString()
            INFO_SECURITY_PATCH -> Build.VERSION.SECURITY_PATCH
            else -> return ExecutionResult.Failure(
                appContext.getString(R.string.system_info_fetch_failed_title),
                appContext.getString(R.string.system_info_invalid_value, infoType)
            )
        }
        return ExecutionResult.Success(mapOf("sysinfo" to VString(resultValue)))
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        return ValidationResult(true)
    }

    override fun createSteps(): List<ActionStep> = listOf(ActionStep(moduleId = this.id, parameters = emptyMap()))

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()

        val pillInfoType = PillUtil.createPillFromParam(
            step.parameters["infotype"],
            inputs.find { it.id == "infotype" },
            isModuleOption = true
        )

        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_system_get_info_prefix), pillInfoType)
    }
}
