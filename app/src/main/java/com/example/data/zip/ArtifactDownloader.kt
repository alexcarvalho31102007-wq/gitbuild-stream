package com.example.data.zip

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.api.GitHubApiClient
import com.example.data.model.ArtifactItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ArtifactDownloader {

    suspend fun downloadAndExtractApk(
        context: Context,
        apiClient: GitHubApiClient,
        artifact: ArtifactItem,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        try {
            val cacheZip = File(context.cacheDir, "artifact_${artifact.id}.zip")
            if (cacheZip.exists()) {
                cacheZip.delete()
            }

            val downloaded = apiClient.downloadArtifactZip(artifact.archiveDownloadUrl, cacheZip, onProgress)
            if (!downloaded || !cacheZip.exists() || cacheZip.length() == 0L) {
                return@withContext null
            }

            // Extract .apk from the downloaded zip artifact
            var extractedApk: File? = null
            val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads").apply { mkdirs() }

            try {
                ZipInputStream(cacheZip.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                            val cleanFileName = File(entry.name).name
                            val targetApk = File(destDir, cleanFileName)
                            if (targetApk.exists()) {
                                targetApk.delete()
                            }

                            FileOutputStream(targetApk).use { fos ->
                                zis.copyTo(fos)
                            }

                            if (targetApk.exists() && targetApk.length() > 0L) {
                                extractedApk = targetApk
                                break
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val finalFile = extractedApk ?: run {
                // If no .apk found inside the zip, save the full zip as fallback
                val zipFallback = File(destDir, "${artifact.name}.zip")
                cacheZip.copyTo(zipFallback, overwrite = true)
                zipFallback
            }

            // Copy to system MediaStore Downloads so it is visible in the device file manager
            copyToPublicDownloads(context, finalFile)

            finalFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun copyToPublicDownloads(context: Context, file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(
                        MediaStore.Downloads.MIME_TYPE,
                        if (file.name.endsWith(".apk", ignoreCase = true))
                            "application/vnd.android.package-archive"
                        else
                            "application/zip"
                    )
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(file).use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
            } else {
                @Suppress("DEPRECATION")
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (publicDir.exists() || publicDir.mkdirs()) {
                    val publicFile = File(publicDir, file.name)
                    file.copyTo(publicFile, overwrite = true)
                }
            }
        } catch (_: Exception) {
            // Internal file is still available for FileProvider
        }
    }

    fun installOrOpenApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = if (file.name.endsWith(".apk", ignoreCase = true)) {
                "application/vnd.android.package-archive"
            } else {
                "application/zip"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Arquivo salvo em Downloads: ${file.name}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
