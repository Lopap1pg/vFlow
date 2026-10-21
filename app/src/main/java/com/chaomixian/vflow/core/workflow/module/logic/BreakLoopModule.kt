// 文件: BreakLoopModule.kt
// 描述: 定义了跳出当前循环的模块。

package com.chaomixian.vflow.core.workflow.module.logic

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * "跳出循环" 模块。
 * 立即终止当前循环的执行，并跳转到循环体之后的步骤。
 */
class BreakLoopModule : BaseModule() {
    override val id = "vflow.logic.break_loop"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_logic_break_loop_name,
        descriptionStringRes = R.string.module_vflow_logic_break_loop_desc,
        name = "跳出循环",  // Fallback
        description = "立即终止当前循环的执行，并跳转到循环体之后的步骤",  // Fallback
        iconRes = R.drawable.rounded_logout_24,
        category = "逻辑控制",
        categoryId = "logic"
    )
    override val aiMetadata = temporaryWorkflowOnlyMetadata(
        riskLevel = AiModuleRiskLevel.LOW,
        workflowStepDescription = "Exit the current loop early. Use only inside a loop body.",
    )

    // 跳出循环模块没有输入和输出，它只影响控制流
    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return context.getString(R.string.summary_vflow_logic_break_loop)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        onProgress(ProgressUpdate(appContext.getString(R.string.logic_break_preparing)))

        // 使用 BlockNavigator
        val currentLoopPairingId = BlockNavigator.findCurrentLoopPairingId(context.allSteps, context.currentStepIndex)
        if (currentLoopPairingId == null) {
            onProgress(ProgressUpdate(appContext.getString(R.string.logic_break_not_in_loop_progress)))
            return ExecutionResult.Failure(
                appContext.getString(R.string.logic_invalid_operation_title),
                appContext.getString(R.string.logic_break_must_be_in_loop)
            )
        }

        return ExecutionResult.Signal(ExecutionSignal.Break)
    }
}
