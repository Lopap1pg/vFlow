// 文件：FindModule.kt
// 描述：定义了屏幕查找相关的模块（FindTextModule）以及支持的数据结构（ScreenElement, Coordinate）。

package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.types.complex.VScreenElement
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.util.regex.Pattern

/**
 * "查找文本"模块。
 * 使用无障碍服务在当前屏幕上根据文本内容查找UI元素。
 * 支持多种匹配模式和输出格式。
 */
class FindTextModule : BaseModule() {
    companion object {
        private const val MATCH_EXACT = "exact"
        private const val MATCH_CONTAINS = "contains"
        private const val MATCH_REGEX = "regex"
        private const val OUTPUT_ELEMENT = "element"
        private const val OUTPUT_COORDINATE = "coordinate"
        private const val OUTPUT_VIEW_ID = "view_id"
    }

    override val id = "vflow.device.find.text"
    override val metadata = ActionMetadata(
        name = "查找文本",
        description = "在屏幕上查找元素",
        nameStringRes = R.string.module_vflow_device_find_text_name,
        descriptionStringRes = R.string.module_vflow_device_find_text_desc,
        iconRes = R.drawable.rounded_feature_search_24,
        category = "界面交互",
        categoryId = "interaction"
    )
    override val aiMetadata = AiModuleMetadata(
        usageScopes = setOf(AiModuleUsageScope.TEMPORARY_WORKFLOW),
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        workflowStepDescription = "Find visible text on the current screen through accessibility and return an element, coordinate, or view id.",
        inputHints = mapOf(
            "matchMode" to "Text match mode: exact, contains, or regex.",
            "targetText" to "Target text to search for on screen.",
            "outputFormat" to "Choose whether the result should be an element, coordinate, or view id.",
        ),
        requiredInputIds = setOf("targetText"),
    )

    override val requiredPermissions = listOf(PermissionManager.ACCESSIBILITY)

    private val matchModeOptions = listOf(MATCH_EXACT, MATCH_CONTAINS, MATCH_REGEX)
    private val outputFormatOptions = listOf(OUTPUT_ELEMENT, OUTPUT_COORDINATE, OUTPUT_VIEW_ID)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "matchMode",
            name = "匹配模式",
            nameStringRes = R.string.param_vflow_device_find_text_matchMode_name,
            staticType = ParameterType.ENUM,
            defaultValue = MATCH_EXACT,
            options = matchModeOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_device_find_text_match_exact,
                R.string.option_vflow_device_find_text_match_contains,
                R.string.option_vflow_device_find_text_match_regex
            ),
            legacyValueMap = mapOf(
                "完全匹配" to MATCH_EXACT,
                "Exact Match" to MATCH_EXACT,
                "包含" to MATCH_CONTAINS,
                "Contains" to MATCH_CONTAINS,
                "正则" to MATCH_REGEX,
                "Regex" to MATCH_REGEX
            ),
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "targetText",
            name = "目标文本",
            nameStringRes = R.string.param_vflow_device_find_text_targetText_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id)
        ),
        InputDefinition(
            id = "outputFormat",
            name = "输出格式",
            nameStringRes = R.string.param_vflow_device_find_text_outputFormat_name,
            staticType = ParameterType.ENUM,
            defaultValue = OUTPUT_ELEMENT,
            options = outputFormatOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_device_find_text_output_element,
                R.string.option_vflow_device_find_text_output_coordinate,
                R.string.option_vflow_device_find_text_output_viewid
            ),
            legacyValueMap = mapOf(
                "元素" to OUTPUT_ELEMENT,
                "Element" to OUTPUT_ELEMENT,
                "坐标" to OUTPUT_COORDINATE,
                "Coordinate" to OUTPUT_COORDINATE,
                "视图ID" to OUTPUT_VIEW_ID,
                "View ID" to OUTPUT_VIEW_ID
            ),
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val outputFormatInput = getInputs().first { it.id == "outputFormat" }
        val rawFormat = step?.parameters?.get("outputFormat") as? String ?: OUTPUT_ELEMENT
        val format = outputFormatInput.normalizeEnumValue(rawFormat) ?: rawFormat
        val conditions = listOf(
            ConditionalOption("存在", "存在"),
            ConditionalOption("不存在", "不存在")
        )

        val resultTypeName = when (format) {
            OUTPUT_COORDINATE -> VTypeRegistry.COORDINATE.id
            OUTPUT_VIEW_ID -> VTypeRegistry.STRING.id
            else -> VTypeRegistry.SCREEN_ELEMENT.id
        }

        return listOf(
            OutputDefinition("first_result", "第一个结果", resultTypeName, conditions, nameStringRes = R.string.output_vflow_device_find_text_first_result_name),
            OutputDefinition("all_results", "所有结果", VTypeRegistry.LIST.id, conditions, listElementType = resultTypeName, nameStringRes = R.string.output_vflow_device_find_text_all_results_name),
            OutputDefinition("count", "结果数量", VTypeRegistry.NUMBER.id, conditions, nameStringRes = R.string.output_vflow_device_find_text_count_name)
        )
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val modePill = PillUtil.createPillFromParam(
            step.parameters["matchMode"],
            inputs.find { it.id == "matchMode" },
            isModuleOption = true
        )
        val targetPill = PillUtil.createPillFromParam(
            step.parameters["targetText"],
            inputs.find { it.id == "targetText" }
        )
        val formatPill = PillUtil.createPillFromParam(
            step.parameters["outputFormat"],
            inputs.find { it.id == "outputFormat" },
            isModuleOption = true
        )

        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_find_text_prefix),
            modePill,
            context.getString(R.string.summary_find_text_middle),
            targetPill,
            context.getString(R.string.summary_find_text_suffix),
            formatPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val service = context.services.get(VFlowAccessibilityService::class)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.find_text_service_not_running_title),
                appContext.getString(R.string.error_vflow_device_find_text_need_service)
            )

        val targetText = context.getVariableAsString("targetText")
        if (targetText.isNullOrBlank()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.find_text_parameter_missing_title),
                appContext.getString(R.string.error_vflow_device_find_text_empty)
            )
        }

        val rootNode = service.rootInActiveWindow
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.find_text_root_unavailable_title),
                appContext.getString(R.string.find_text_root_unavailable_detail)
            )

        try {
            val inputsById = getInputs().associateBy { it.id }
            val rawMatchMode = context.getVariableAsString("matchMode", MATCH_EXACT)
            val matchModeStr = inputsById["matchMode"]?.normalizeEnumValue(rawMatchMode) ?: rawMatchMode
            val matchModeDisplayName = getMatchModeDisplayName(matchModeStr)
            onProgress(
                ProgressUpdate(
                    appContext.getString(
                        R.string.msg_vflow_device_find_text_searching,
                        matchModeDisplayName,
                        targetText
                    )
                )
            )

            val nodes = findNodesByText(rootNode, targetText, matchModeStr)
            val count = nodes.size
            val rawOutputFormat = context.getVariableAsString("outputFormat", OUTPUT_ELEMENT)
            val outputFormat = inputsById["outputFormat"]?.normalizeEnumValue(rawOutputFormat) ?: rawOutputFormat
            val outputs = mutableMapOf<String, Any?>()

            outputs["count"] = VNumber(count.toDouble())

            if (nodes.isEmpty()) {
                return ExecutionResult.Failure(
                    appContext.getString(R.string.find_text_no_match_title),
                    appContext.getString(
                        R.string.find_text_no_match_detail,
                        targetText,
                        matchModeDisplayName
                    ),
                    partialOutputs = mapOf(
                        "count" to VNumber(0.0),
                        "all_results" to emptyList<Any>(),
                        "first_result" to VNull
                    )
                )
            }

            val allResultsList = nodes.map { node ->
                when (outputFormat) {
                    OUTPUT_COORDINATE -> {
                        val bounds = Rect()
                        node.getBoundsInScreen(bounds)
                        VCoordinate(bounds.centerX(), bounds.centerY())
                    }
                    OUTPUT_VIEW_ID -> VString(node.viewIdResourceName ?: "")
                    else -> VScreenElement.fromAccessibilityNode(node)
                }
            }

            outputs["all_results"] = allResultsList
            outputs["first_result"] = allResultsList.firstOrNull() ?: VNull
            nodes.forEach { it.recycle() }

            return ExecutionResult.Success(outputs)
        } catch (e: Exception) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_find_text_exception),
                e.localizedMessage ?: appContext.getString(R.string.find_text_unknown_error)
            )
        } finally {
            rootNode.recycle()
        }
    }

    private fun findNodesByText(rootNode: AccessibilityNodeInfo, text: String, matchModeStr: String): List<AccessibilityNodeInfo> {
        val matchedNodes = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(rootNode))

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val nodeText = node.text?.toString()
            val nodeDesc = node.contentDescription?.toString()

            val checkMatch = { source: String ->
                when (matchModeStr) {
                    MATCH_CONTAINS -> source.contains(text, ignoreCase = true)
                    MATCH_REGEX -> try { Pattern.compile(text).matcher(source).find() } catch (e: Exception) { false }
                    else -> source == text
                }
            }

            var matchedThisNode = false
            if (nodeText != null && checkMatch(nodeText)) {
                matchedNodes.add(AccessibilityNodeInfo.obtain(node))
                matchedThisNode = true
            }
            if (!matchedThisNode && nodeDesc != null && checkMatch(nodeDesc)) {
                matchedNodes.add(AccessibilityNodeInfo.obtain(node))
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child -> queue.add(child) }
            }
            node.recycle()
        }
        return matchedNodes
    }

    private fun getMatchModeDisplayName(matchMode: String): String {
        return when (matchMode) {
            MATCH_CONTAINS -> appContext.getString(R.string.option_vflow_device_find_text_match_contains)
            MATCH_REGEX -> appContext.getString(R.string.option_vflow_device_find_text_match_regex)
            else -> appContext.getString(R.string.option_vflow_device_find_text_match_exact)
        }
    }
}
