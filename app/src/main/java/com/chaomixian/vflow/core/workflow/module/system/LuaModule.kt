// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/LuaModule.kt
package com.chaomixian.vflow.core.workflow.module.system
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.basic.*

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.LuaExecutor
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * "Lua脚本" 模块。
 * 允许用户编写和执行 Lua 脚本，可以调用其他模块作为函数，
 * 并处理输入输出，实现复杂的自定义逻辑。
 */
class LuaModule : BaseModule() {

    override val id = "vflow.system.lua"
    override val metadata = ActionMetadata(
        name = "Lua脚本",  // Fallback
        nameStringRes = R.string.module_vflow_system_lua_name,
        description = "执行一段Lua脚本，可调用其他模块功能。",  // Fallback
        descriptionStringRes = R.string.module_vflow_system_lua_desc,
        iconRes = R.drawable.rounded_lua_24,
        category = "应用与系统",
        categoryId = "device"
    )
    override val aiMetadata = AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = AiModuleRiskLevel.HIGH,
        workflowStepDescription = "Execute a Lua script with optional inputs and return a dictionary result.",
        inputHints = mapOf(
            "script" to "Full Lua source code.",
            "inputs" to "Optional dictionary of script inputs.",
        ),
        requiredInputIds = setOf("script"),
    )

    override val uiProvider: ModuleUIProvider? = LuaModuleUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "script",
            name = "Lua 脚本",
            nameStringRes = R.string.param_vflow_system_lua_script_name,
            staticType = ParameterType.STRING,
            defaultValue = "vflow.device.toast({ message = 'Hello from Lua!' })\n\n-- Read values from script inputs.\n-- local myVar = inputs.my_variable\n\n-- Return a table as outputs.\nreturn { result = 'some value' }",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "inputs",
            name = "脚本输入",
            nameStringRes = R.string.param_vflow_system_lua_inputs_name,
            staticType = ParameterType.ANY,
            defaultValue = emptyMap<String, Any>(),
            acceptsMagicVariable = true // 这里的接受其实是其value接受魔法变量
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "outputs",
            "脚本返回值",
            VTypeRegistry.DICTIONARY.id,
            nameStringRes = R.string.output_vflow_system_lua_outputs_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val script = step.parameters["script"] as? String ?: "..."
        val firstLine = script.trim().lines().firstOrNull { it.isNotBlank() && !it.trim().startsWith("--") }
            ?: context.getString(R.string.summary_empty_script)

        // 更新 Pill 的构造以匹配新的签名
        val scriptPill = PillUtil.Pill(firstLine, "script")

        return PillUtil.buildSpannable(context,
            context.getString(R.string.summary_vflow_system_lua_prefix),
            scriptPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val script = context.getVariableAsString("script", "")
        if (script.isNullOrBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_lua_script_empty),
                appContext.getString(R.string.error_vflow_system_lua_script_empty_desc)
            )
        }

        val scriptInputs = mutableMapOf<String, Any?>()
        // 现在 variables 是 Map<String, VObject>，inputs 存储为 VDictionary
        val inputsObj = context.getVariable("inputs")
        val inputMappings = when (inputsObj) {
            is VDictionary -> inputsObj.raw.mapValues { it.value.asString() }
            else -> emptyMap()
        }

        inputMappings.forEach { (varName, variableRef) ->
            if (VariableResolver.hasVariableReference(variableRef)) {
                scriptInputs[varName] = VariableResolver.resolveValue(variableRef, context)
            } else {
                scriptInputs[varName] = variableRef
            }
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_lua_preparing)))
        val luaExecutor = LuaExecutor(context)

        return try {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_lua_executing)))
            val resultTable = luaExecutor.execute(script, scriptInputs)
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_lua_completed)))
            // Convert Map<String, Any?> to Map<String, VObject>
            val vMap = resultTable.mapValues { VObjectFactory.from(it.value) }
            ExecutionResult.Success(mapOf("outputs" to VDictionary(vMap)))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_lua_execution_failed),
                e.localizedMessage ?: appContext.getString(R.string.error_unknown_error)
            )
        }
    }
}
