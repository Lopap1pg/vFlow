// 文件: main/java/com/chaomixian/vflow/data/update/GitHubRelease.kt
package com.chaomixian.vflow.data.update

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.R
import kotlinx.parcelize.Parcelize
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GitHub Release 数据模型
 */
@Parcelize
data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val isPrerelease: Boolean,
    val publishedAt: String,
    val htmlUrl: String
) : Parcelable {

    /**
     * 获取版本号（去除v前缀）
     */
    fun getVersionWithoutPrefix(): String {
        return tagName.removePrefix("v")
    }

    /**
     * 判断是否为稳定版本（不包含-pr、-alpha、-beta等）
     */
    fun isStable(): Boolean {
        return !isPrerelease && !tagName.contains(Regex("-(alpha|beta|rc|pr)"))
    }

    /**
     * 获取格式化后的发布时间
     */
    fun getFormattedDate(locale: Locale = Locale.getDefault()): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val outputFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
            val date = inputFormat.parse(publishedAt)
            date?.let { outputFormat.format(it) } ?: publishedAt
        } catch (e: Exception) {
            publishedAt
        }
    }

    /**
     * 获取本地化后的相对时间。
     */
    fun getRelativeTime(context: Context): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val publishDate = inputFormat.parse(publishedAt) ?: return publishedAt
            val now = Date()
            val diffInMillis = now.time - publishDate.time
            val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)

            when {
                diffInDays == 0L -> context.getString(R.string.changelog_relative_today)
                diffInDays == 1L -> context.getString(R.string.changelog_relative_yesterday)
                diffInDays < 30L -> context.getString(R.string.changelog_relative_days_ago, diffInDays)
                diffInDays < 365L -> context.getString(R.string.changelog_relative_months_ago, diffInDays / 30)
                else -> context.getString(R.string.changelog_relative_years_ago, diffInDays / 365)
            }
        } catch (e: Exception) {
            publishedAt
        }
    }
}

/**
 * 版本更新信息
 */
data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val release: GitHubRelease?
)
