// 文件路径: main/java/com/chaomixian/vflow/core/workflow/module/logic/LoopModule.kt
package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.LoopState
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

private fun parseLoopCountFromString(raw: String): Long? {
    raw.toLongOrNull()?.let { return it }

    val number = raw.toDoubleOrNull() ?: return null
    if (!number.isFinite()) return null

    val asLong = number.toLong()
    return if (number == asLong.toDouble()) asLong else null
}

const val LOOP_PAIRING_ID = "loop"
const val LOOP_START_ID = "vflow.logic.loop.start"
const val LOOP_END_ID = "vflow.logic.loop.end"

class LoopModule : BaseBlockModule() {
    override val id = LOOP_START_ID
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_loop_start_name,
        descriptionStringRes = R.string.module_vflow_logic_loop_start_desc,
        name = "循环",
        description = "重复执行一组操作固定的次数",
        iconRes = R.drawable.rounded_cached_24,
        category = "逻辑控制",
        categoryId = "logic"
    )
    override val aiMetadata = temporaryWorkflowOnlyMetadata(
        riskLevel = AiModuleRiskLevel.LOW,
        workflowStepDescription = "Repeat the enclosed steps a fixed number of times. Pair it with vflow.logic.loop.end.",
        inputHints = mapOf(
            "count" to "Positive repeat count. Use a literal integer or a previous numeric output.",
        ),
        requiredInputIds = setOf("count"),
    )
    override val pairingId = LOOP_PAIRING_ID
    override val stepIdsInBlock = listOf(LOOP_START_ID, LOOP_END_ID)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "count",
            nameStringRes = R.string.param_vflow_logic_loop_start_count_name,
            name = "重复次数",
            staticType = ParameterType.NUMBER,
            defaultValue = 5L,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("loop_index", nameStringRes = R.string.output_vflow_logic_loop_start_loop_index_name, name = "循环索引", typeName = VTypeRegistry.NUMBER.id),
        OutputDefinition("loop_total", nameStringRes = R.string.output_vflow_logic_loop_start_loop_total_name, name = "循环总数", typeName = VTypeRegistry.NUMBER.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val countPill = PillUtil.createPillFromParam(
            step.parameters["count"],
            getInputs().find { it.id == "count" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_logic_loop_prefix), countPill, context.getString(R.string.summary_vflow_logic_loop_suffix))
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val count = step.parameters["count"]
        if (count is String && !count.isMagicVariable()) {
            val countAsLong = parseLoopCountFromString(count)
            if (countAsLong == null) return ValidationResult(false, appContext.getString(R.string.loop_validation_invalid_number_format))
            if (countAsLong <= 0) return ValidationResult(false, appContext.getString(R.string.loop_validation_count_positive))
        } else if (count is Number) {
            if (count.toLong() <= 0) return ValidationResult(false, appContext.getString(R.string.loop_validation_count_positive))
        }
        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val countVar = context.getVariable("count")
        val actualCount: Long? = when (countVar) {
            is VNumber -> countVar.raw.toLong()
            is VString -> parseLoopCountFromString(countVar.raw)
            is VNull -> null
            else -> null
        }

        if (actualCount == null || actualCount <= 0) {
            val errorMsg = when (countVar) {
                is VString -> appContext.getString(R.string.loop_count_parse_failed, countVar.raw)
                is VNull -> appContext.getString(R.string.loop_count_required)
                else -> appContext.getString(R.string.loop_count_must_be_number)
            }
            return ExecutionResult.Failure(
                appContext.getString(R.string.loop_parameter_error_title),
                errorMsg
            )
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.loop_started, actualCount)))
        context.loopStack.push(LoopState.CountLoopState(actualCount))
        return ExecutionResult.Signal(ExecutionSignal.Loop(LoopAction.START))
    }
}

class EndLoopModule : BaseModule() {
    override val id = LOOP_END_ID
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_loop_end_name,
        descriptionStringRes = R.string.module_vflow_logic_loop_end_desc,
        name = "结束循环",
        description = "",
        iconRes = R.drawable.rounded_cached_24,
        category = "逻辑控制",
        categoryId = "logic"
    )
    override val aiMetadata = temporaryWorkflowOnlyMetadata(
        riskLevel = AiModuleRiskLevel.LOW,
        workflowStepDescription = "Close a loop block started by vflow.logic.loop.start.",
    )
    override val blockBehavior = BlockBehavior(BlockType.BLOCK_END, LOOP_PAIRING_ID)

    override fun getSummary(context: Context, step: ActionStep): CharSequence = context.getString(R.string.summary_end_loop)

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val loopState = context.loopStack.peek()
        if (loopState !is LoopState.CountLoopState) {
            return if (loopState == null) {
                ExecutionResult.Success()
            } else {
                ExecutionResult.Failure(
                    appContext.getString(R.string.loop_execution_error_title),
                    appContext.getString(R.string.loop_end_not_in_loop)
                )
            }
        }

        onProgress(
            ProgressUpdate(
                appContext.getString(R.string.loop_iteration_finished, loopState.currentIteration + 1)
            )
        )

        loopState.currentIteration++
        return ExecutionResult.Signal(ExecutionSignal.Loop(LoopAction.END))
    }
}
