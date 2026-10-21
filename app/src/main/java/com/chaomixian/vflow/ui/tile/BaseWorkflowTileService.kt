package com.chaomixian.vflow.ui.tile

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.workflow.TileManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.services.AccessibilityKeepAliveManager
import com.chaomixian.vflow.ui.main.MainActivity

/**
 * 工作流TileService基类
 * 通过tileIndex区分不同的Tile位置
 */
abstract class BaseWorkflowTileService : TileService() {

    abstract fun getTileIndex(): Int

    private val tileManager: TileManager by lazy { TileManager(applicationContext) }
    private val workflowManager: WorkflowManager by lazy { WorkflowManager(applicationContext) }

    override fun onStartListening() {
        super.onStartListening()
        AccessibilityKeepAliveManager.onQuickSettingsPanelVisible(applicationContext)
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        android.util.Log.d("vFlowTile", "Tile clicked: ${getTileIndex()}")

        val tileIndex = getTileIndex()
        val tile = tileManager.getTile(tileIndex)
        val workflowId = tile?.workflowId

        android.util.Log.d("vFlowTile", "workflowId: $workflowId")

        if (workflowId == null) {
            // 没有分配工作流，打开应用
            openApp()
            return
        }

        executeWorkflow(workflowId)
    }

    private fun updateTileState() {
        val qsTile = qsTile ?: return

        val tileIndex = getTileIndex()
        val tile = tileManager.getTile(tileIndex)
        val workflowId = tile?.workflowId

        android.util.Log.d("vFlowTile", "Update tile $tileIndex, workflowId: $workflowId")

        if (workflowId == null) {
            qsTile.state = Tile.STATE_INACTIVE
            qsTile.label = getString(R.string.tile_label, tileIndex + 1)
        } else {
            val workflow = workflowManager.getWorkflow(workflowId)
            qsTile.state = if (workflow != null && workflow.isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            qsTile.label = workflow?.name ?: getString(R.string.tile_label, tileIndex + 1)
        }

        qsTile.updateTile()
    }

    private fun executeWorkflow(workflowId: String) {
        val workflow = workflowManager.getWorkflow(workflowId)
        android.util.Log.d("vFlowTile", "executeWorkflow: $workflowId, workflow: $workflow")

        if (workflow == null) {
            android.widget.Toast.makeText(
                applicationContext,
                getString(R.string.tile_workflow_not_found),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        // 允许执行禁用的工作流（通过Tile强制执行）
        val isManualTrigger = workflow.hasManualTrigger()
        if (!isManualTrigger) {
            android.widget.Toast.makeText(
                applicationContext,
                getString(R.string.tile_manual_trigger_required),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        android.widget.Toast.makeText(
            applicationContext,
            getString(R.string.tile_executing_workflow, workflow.name),
            android.widget.Toast.LENGTH_SHORT
        ).show()

        // 使用与Shortcut相同的方式执行：通过Activity
        val intent = Intent(applicationContext, com.chaomixian.vflow.ui.common.ShortcutExecutorActivity::class.java).apply {
            action = com.chaomixian.vflow.ui.common.ShortcutExecutorActivity.ACTION_EXECUTE_WORKFLOW
            putExtra(com.chaomixian.vflow.ui.common.ShortcutExecutorActivity.EXTRA_WORKFLOW_ID, workflowId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun openApp() {
        val intent = MainActivity.createAppLaunchIntent(this).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startqsTileService(pendingIntent)
    }

    private fun startqsTileService(pendingIntent: PendingIntent) {
        try {
            pendingIntent.send()
        } catch (e: Exception) {
            android.util.Log.e("vFlowTile", "Failed to start activity", e)
        }
    }
}
