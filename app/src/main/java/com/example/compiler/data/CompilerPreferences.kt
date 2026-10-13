package com.example.compiler.data

import android.content.Context
import android.content.SharedPreferences

class CompilerPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("compiler_prefs", Context.MODE_PRIVATE)

    var githubToken: String
        get() = prefs.getString(KEY_GITHUB_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GITHUB_TOKEN, value.trim()).apply()

    var repoOwner: String
        get() = prefs.getString(KEY_REPO_OWNER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_REPO_OWNER, value.trim()).apply()

    var repoName: String
        get() = prefs.getString(KEY_REPO_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_REPO_NAME, value.trim()).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var isAutoDownloadApk: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DOWNLOAD_APK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_APK, value).apply()

    val isConfigured: Boolean
        get() = githubToken.isNotBlank() && repoOwner.isNotBlank() && repoName.isNotBlank()

    companion object {
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_REPO_OWNER = "repo_owner"
        private const val KEY_REPO_NAME = "repo_name"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_AUTO_DOWNLOAD_APK = "auto_download_apk"
    }
}
