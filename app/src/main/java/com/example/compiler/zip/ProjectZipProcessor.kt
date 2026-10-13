package com.example.compiler.zip

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

data class ParsedProject(
    val projectName: String,
    val packageName: String,
    val fileCount: Int,
    val zipSizeBytes: Long,
    val files: Map<String, ByteArray>,
    val hasGradlew: Boolean,
    val hasSettingsGradle: Boolean,
    val hasBuildGradle: Boolean
)

class ProjectZipProcessor(private val context: Context) {

    suspend fun parseUserProjectZip(uri: Uri): Result<ParsedProject> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val displayName = queryDisplayName(uri) ?: "ProjetoAndroid"

            val rawEntries = mutableMapOf<String, ByteArray>()
            var totalBytes = 0L

            contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && shouldIncludeFile(name)) {
                            val buffer = ByteArrayOutputStream()
                            val data = ByteArray(8192)
                            var read: Int
                            while (zip.read(data).also { read = it } != -1) {
                                buffer.write(data, 0, read)
                            }
                            val bytes = buffer.toByteArray()
                            rawEntries[name] = bytes
                            totalBytes += bytes.size
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return@withContext Result.failure(Exception("Não foi possível abrir o arquivo ZIP selecionado."))

            if (rawEntries.isEmpty()) {
                return@withContext Result.failure(Exception("O arquivo ZIP está vazio ou não contém arquivos legíveis."))
            }

            // Normaliza estrutura de diretórios caso o ZIP tenha uma pasta raiz (ex: MeuProjeto/...)
            val normalizedFiles = normalizeRootDirectory(rawEntries)

            var hasBuildGradle = false
            var hasSettingsGradle = false
            var hasGradlew = false
            var detectedProjectName = displayName.removeSuffix(".zip").removeSuffix(".ZIP")
            var detectedPackageName = "com.app.release"

            normalizedFiles.forEach { (path, content) ->
                val fileName = path.substringAfterLast('/')
                if (fileName == "build.gradle" || fileName == "build.gradle.kts") {
                    hasBuildGradle = true
                    // Tenta detectar packageName / namespace
                    val text = String(content, Charsets.UTF_8)
                    val namespaceMatch = Regex("""namespace\s*=?\s*["']([^"']+)["']""").find(text)
                    val appIdMatch = Regex("""applicationId\s*=?\s*["']([^"']+)["']""").find(text)
                    val foundPackage = namespaceMatch?.groupValues?.get(1) ?: appIdMatch?.groupValues?.get(1)
                    if (foundPackage != null) {
                        detectedPackageName = foundPackage
                    }
                }
                if (fileName == "settings.gradle" || fileName == "settings.gradle.kts") {
                    hasSettingsGradle = true
                    val text = String(content, Charsets.UTF_8)
                    val nameMatch = Regex("""rootProject\.name\s*=\s*["']([^"']+)["']""").find(text)
                    if (nameMatch != null) {
                        detectedProjectName = nameMatch.groupValues[1]
                    }
                }
                if (fileName == "gradlew") {
                    hasGradlew = true
                }
                if (fileName == "AndroidManifest.xml") {
                    val text = String(content, Charsets.UTF_8)
                    val packageMatch = Regex("""package\s*=\s*["']([^"']+)["']""").find(text)
                    if (packageMatch != null && detectedPackageName == "com.app.release") {
                        detectedPackageName = packageMatch.groupValues[1]
                    }
                }
            }

            if (!hasBuildGradle && !hasSettingsGradle) {
                return@withContext Result.failure(
                    Exception("O arquivo selecionado não parece ser um projeto Android válido (arquivos build.gradle ou settings.gradle não encontrados no ZIP).")
                )
            }

            val finalFiles = normalizedFiles.toMutableMap()

            // Injeta gradlew e wrapper se não existirem no projeto
            if (!hasGradlew) {
                finalFiles["gradlew"] = StandardGradleWrapper.getGradlewScript().toByteArray(Charsets.UTF_8)
                if (!finalFiles.containsKey("gradle/wrapper/gradle-wrapper.properties")) {
                    finalFiles["gradle/wrapper/gradle-wrapper.properties"] =
                        StandardGradleWrapper.getWrapperProperties().toByteArray(Charsets.UTF_8)
                }
            }

            Result.success(
                ParsedProject(
                    projectName = detectedProjectName,
                    packageName = detectedPackageName,
                    fileCount = finalFiles.size,
                    zipSizeBytes = totalBytes,
                    files = finalFiles,
                    hasGradlew = hasGradlew,
                    hasSettingsGradle = hasSettingsGradle,
                    hasBuildGradle = hasBuildGradle
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("Erro ao processar projeto ZIP: ${e.localizedMessage ?: e.message}"))
        }
    }

    private fun shouldIncludeFile(path: String): Boolean {
        val clean = path.replace('\\', '/')
        if (clean.startsWith("__MACOSX/") || clean.contains("/__MACOSX/")) return false
        if (clean.contains(".DS_Store") || clean.contains("Thumbs.db")) return false
        if (clean.startsWith(".git/") || clean.contains("/.git/")) return false
        if (clean.startsWith(".gradle/") || clean.contains("/.gradle/")) return false
        if (clean.startsWith("build/") || clean.contains("/build/")) return false
        if (clean.startsWith(".idea/") || clean.contains("/.idea/")) return false
        if (clean.endsWith(".iml")) return false
        return true
    }

    private fun normalizeRootDirectory(entries: Map<String, ByteArray>): Map<String, ByteArray> {
        val paths = entries.keys.map { it.replace('\\', '/') }
        if (paths.isEmpty()) return entries

        // Se houver algum arquivo diretamente na raiz, já está correto
        val hasDirectFiles = paths.any { !it.contains('/') }
        if (hasDirectFiles) {
            return entries.mapKeys { it.key.replace('\\', '/') }
        }

        // Verifica se todas as entradas compartilham o mesmo diretório raiz comum
        val firstSegments = paths.map { it.substringBefore('/') }.toSet()
        if (firstSegments.size == 1) {
            val rootPrefix = "${firstSegments.first()}/"
            val stripped = mutableMapOf<String, ByteArray>()
            entries.forEach { (key, value) ->
                val cleanKey = key.replace('\\', '/')
                val newKey = cleanKey.removePrefix(rootPrefix)
                if (newKey.isNotBlank()) {
                    stripped[newKey] = value
                }
            }
            return stripped
        }

        return entries.mapKeys { it.key.replace('\\', '/') }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment
    }
}
