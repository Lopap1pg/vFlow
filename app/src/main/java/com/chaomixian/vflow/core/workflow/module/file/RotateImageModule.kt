// 文件: RotateImageModule.kt
// 描述: 旋转图像模块。
package com.chaomixian.vflow.core.workflow.module.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.io.File
import java.io.FileOutputStream
import java.util.*

class RotateImageModule : BaseModule() {

    override val id = "vflow.file.rotate_image"
    override val metadata = ActionMetadata(
        name = "旋转图片",  // Fallback
        nameStringRes = R.string.module_vflow_file_rotate_image_name,
        description = "将图片按指定角度旋转。",  // Fallback
        descriptionStringRes = R.string.module_vflow_file_rotate_image_desc,
        iconRes = R.drawable.rounded_rotate_90_degrees_cw_24,
        category = "文件",
        categoryId = "file"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "image",
            name = "源图像",
            staticType = ParameterType.ANY,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.IMAGE.id),
            nameStringRes = R.string.param_vflow_file_rotate_image_image_name
        ),
        InputDefinition(
            id = "degrees",
            name = "旋转角度",
            staticType = ParameterType.NUMBER,
            defaultValue = 90.0,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            nameStringRes = R.string.param_vflow_file_rotate_image_degrees_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "image",
            "旋转后的图像",
            VTypeRegistry.IMAGE.id,
            nameStringRes = R.string.output_vflow_file_rotate_image_image_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val imagePill = PillUtil.createPillFromParam(
            step.parameters["image"],
            inputs.find { it.id == "image" }
        )
        val degreesPill = PillUtil.createPillFromParam(
            step.parameters["degrees"],
            inputs.find { it.id == "degrees" }
        )

        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_file_rotate_image_prefix),
            imagePill,
            context.getString(R.string.summary_vflow_file_rotate_image_rotate),
            degreesPill,
            context.getString(R.string.summary_vflow_file_rotate_image_degrees)
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val imageVar = context.getVariableAsImage("image")
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.image_processing_param_error_title),
                appContext.getString(R.string.image_processing_image_input_required)
            )

        val degrees = context.getVariableAsNumber("degrees")?.toFloat() ?: 90.0f

        onProgress(ProgressUpdate(appContext.getString(R.string.image_processing_loading)))

        return try {
            val request = ImageRequest.Builder(appContext)
                .data(Uri.parse(imageVar.uriString))
                .allowHardware(false) // 禁止硬件位图
                .build()
            val result = Coil.imageLoader(appContext).execute(request)
            val originalBitmap = result.drawable?.toBitmap()
                ?: return ExecutionResult.Failure(
                    appContext.getString(R.string.image_processing_load_failed_title),
                    appContext.getString(R.string.image_processing_load_failed_uri, imageVar.uriString)
                )

            onProgress(ProgressUpdate(appContext.getString(R.string.image_processing_rotating)))

            val matrix = Matrix().apply { postRotate(degrees) }
            val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)

            onProgress(ProgressUpdate(appContext.getString(R.string.image_processing_saving)))
            // 使用 workDir
            val outputFile = File(context.workDir, "rotated_${UUID.randomUUID()}.png")
            FileOutputStream(outputFile).use {
                rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            // 不回收 originalBitmap
            rotatedBitmap.recycle()

            val outputUri = Uri.fromFile(outputFile).toString()
            ExecutionResult.Success(mapOf("image" to VImage(outputUri)))

        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.image_processing_exception_title),
                e.localizedMessage ?: appContext.getString(R.string.image_processing_unknown_error)
            )
        }
    }
}
