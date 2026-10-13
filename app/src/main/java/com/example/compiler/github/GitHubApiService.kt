package com.example.compiler.github

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class GitHubApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun buildRequest(token: String, url: String): Request.Builder {
        val cleanToken = token.trim()
        val authHeader = if (cleanToken.startsWith("Bearer ") || cleanToken.startsWith("token ")) {
            cleanToken
        } else {
            "Bearer $cleanToken"
        }
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", authHeader)
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .addHeader("User-Agent", "Android-Release-Builder-App")
    }

    suspend fun getUser(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(token, "https://api.github.com/user").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorBody = response.body?.string().orEmpty()
                    return@withContext Result.failure(
                        Exception(if (code == 401) "Token do GitHub inválido ou expirado." else "Erro na autenticação ($code): $errorBody")
                    )
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val user = GitHubUser(
                    login = json.optString("login", ""),
                    name = if (json.isNull("name")) null else json.optString("name"),
                    avatarUrl = if (json.isNull("avatar_url")) null else json.optString("avatar_url"),
                    publicRepos = json.optInt("public_repos", 0) + json.optInt("total_private_repos", 0)
                )
                Result.success(user)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserRepos(token: String): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(token, "https://api.github.com/user/repos?sort=updated&per_page=50")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Falha ao listar repositórios (${response.code})"))
                }
                val body = response.body?.string().orEmpty()
                val array = JSONArray(body)
                val list = mutableListOf<GitHubRepo>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val ownerObj = obj.optJSONObject("owner")
                    list.add(
                        GitHubRepo(
                            name = obj.optString("name"),
                            fullName = obj.optString("full_name"),
                            owner = ownerObj?.optString("login") ?: "",
                            isPrivate = obj.optBoolean("private", false),
                            defaultBranch = obj.optString("default_branch", "main")
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRepo(token: String, name: String, isPrivate: Boolean): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("name", name)
                put("description", "Repositório dedicado para compilações de Release Android")
                put("private", isPrivate)
                put("auto_init", true)
            }
            val request = buildRequest(token, "https://api.github.com/user/repos")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    return@withContext Result.failure(Exception("Erro ao criar repositório (${response.code}): $errorBody"))
                }
                val body = response.body?.string().orEmpty()
                val obj = JSONObject(body)
                val ownerObj = obj.optJSONObject("owner")
                val repo = GitHubRepo(
                    name = obj.optString("name"),
                    fullName = obj.optString("full_name"),
                    owner = ownerObj?.optString("login") ?: "",
                    isPrivate = obj.optBoolean("private", isPrivate),
                    defaultBranch = obj.optString("default_branch", "main")
                )
                Result.success(repo)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ensureWorkflowFile(token: String, owner: String, repo: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val path = ".github/workflows/android-release.yml"
            val checkUrl = "https://api.github.com/repos/$owner/$repo/contents/$path"
            val checkRequest = buildRequest(token, checkUrl).get().build()
            var existingSha: String? = null

            client.newCall(checkRequest).execute().use { checkResponse ->
                if (checkResponse.isSuccessful) {
                    val body = checkResponse.body?.string().orEmpty()
                    existingSha = JSONObject(body).optString("sha")
                }
            }

            val workflowYaml = getReleaseWorkflowYaml()
            val encodedContent = Base64.encodeToString(workflowYaml.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            val payload = JSONObject().apply {
                put("message", "Configura workflow oficial de compilação Release do Android")
                put("content", encodedContent)
                if (existingSha != null) {
                    put("sha", existingSha)
                }
            }

            val putRequest = buildRequest(token, checkUrl)
                .put(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(putRequest).execute().use { putResponse ->
                if (putResponse.isSuccessful || putResponse.code == 422) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Erro ao configurar workflow (${putResponse.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDefaultBranchSha(token: String, owner: String, repo: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val repoUrl = "https://api.github.com/repos/$owner/$repo"
            val repoRequest = buildRequest(token, repoUrl).get().build()
            var defaultBranch = "main"

            client.newCall(repoRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string().orEmpty())
                    defaultBranch = json.optString("default_branch", "main")
                }
            }

            val refUrl = "https://api.github.com/repos/$owner/$repo/git/ref/heads/$defaultBranch"
            val refRequest = buildRequest(token, refUrl).get().build()
            client.newCall(refRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Não foi possível encontrar a branch padrão $defaultBranch"))
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val commitObj = json.getJSONObject("object")
                val sha = commitObj.getString("sha")
                Result.success(Pair(defaultBranch, sha))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBlob(token: String, owner: String, repo: String, content: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base64 = Base64.encodeToString(content, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("content", base64)
                put("encoding", "base64")
            }
            val request = buildRequest(token, "https://api.github.com/repos/$owner/$repo/git/blobs")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erro ao criar blob (${response.code}): ${response.body?.string()}"))
                }
                val resJson = JSONObject(response.body?.string().orEmpty())
                Result.success(resJson.getString("sha"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTree(token: String, owner: String, repo: String, items: List<TreeItem>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val treeArray = JSONArray()
            items.forEach { item ->
                treeArray.put(
                    JSONObject().apply {
                        put("path", item.path)
                        put("mode", item.mode)
                        put("type", item.type)
                        put("sha", item.sha)
                    }
                )
            }
            val json = JSONObject().apply {
                put("tree", treeArray)
            }
            val request = buildRequest(token, "https://api.github.com/repos/$owner/$repo/git/trees")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erro ao criar árvore Git (${response.code}): ${response.body?.string()}"))
                }
                val resJson = JSONObject(response.body?.string().orEmpty())
                Result.success(resJson.getString("sha"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createCommit(token: String, owner: String, repo: String, message: String, treeSha: String, parentSha: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("message", message)
                put("tree", treeSha)
                if (parentSha != null) {
                    put("parents", JSONArray().put(parentSha))
                } else {
                    put("parents", JSONArray())
                }
            }
            val request = buildRequest(token, "https://api.github.com/repos/$owner/$repo/git/commits")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erro ao criar commit (${response.code}): ${response.body?.string()}"))
                }
                val resJson = JSONObject(response.body?.string().orEmpty())
                Result.success(resJson.getString("sha"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createOrUpdateBranch(token: String, owner: String, repo: String, branchName: String, commitSha: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val checkUrl = "https://api.github.com/repos/$owner/$repo/git/ref/heads/$branchName"
            val checkRequest = buildRequest(token, checkUrl).get().build()
            var branchExists = false
            client.newCall(checkRequest).execute().use { response ->
                branchExists = response.isSuccessful
            }

            if (branchExists) {
                val updateJson = JSONObject().apply {
                    put("sha", commitSha)
                    put("force", true)
                }
                val patchRequest = buildRequest(token, "https://api.github.com/repos/$owner/$repo/git/refs/heads/$branchName")
                    .patch(updateJson.toString().toRequestBody(jsonMediaType))
                    .build()
                client.newCall(patchRequest).execute().use { res ->
                    if (!res.isSuccessful) return@withContext Result.failure(Exception("Erro ao atualizar branch (${res.code})"))
                }
            } else {
                val createJson = JSONObject().apply {
                    put("ref", "refs/heads/$branchName")
                    put("sha", commitSha)
                }
                val postRequest = buildRequest(token, "https://api.github.com/repos/$owner/$repo/git/refs")
                    .post(createJson.toString().toRequestBody(jsonMediaType))
                    .build()
                client.newCall(postRequest).execute().use { res ->
                    if (!res.isSuccessful) return@withContext Result.failure(Exception("Erro ao criar branch (${res.code})"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dispatchWorkflow(
        token: String,
        owner: String,
        repo: String,
        workflowFileName: String,
        ref: String,
        inputs: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val inputsJson = JSONObject()
            inputs.forEach { (k, v) -> inputsJson.put(k, v) }

            val json = JSONObject().apply {
                put("ref", ref)
                put("inputs", inputsJson)
            }
            val url = "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFileName/dispatches"
            val request = buildRequest(token, url)
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 204 || response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errorBody = response.body?.string().orEmpty()
                    Result.failure(Exception("Erro ao iniciar fluxo do GitHub Actions (${response.code}): $errorBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWorkflowRuns(
        token: String,
        owner: String,
        repo: String,
        branch: String?
    ): Result<List<GitHubWorkflowRun>> = withContext(Dispatchers.IO) {
        try {
            val url = if (branch != null) {
                "https://api.github.com/repos/$owner/$repo/actions/runs?branch=$branch&per_page=10"
            } else {
                "https://api.github.com/repos/$owner/$repo/actions/runs?per_page=10"
            }
            val request = buildRequest(token, url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erro ao consultar execuções (${response.code})"))
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val runsArray = json.optJSONArray("workflow_runs") ?: JSONArray()
                val list = mutableListOf<GitHubWorkflowRun>()
                for (i in 0 until runsArray.length()) {
                    val runObj = runsArray.getJSONObject(i)
                    list.add(
                        GitHubWorkflowRun(
                            id = runObj.getLong("id"),
                            name = runObj.optString("name", "Build"),
                            status = runObj.getString("status"),
                            conclusion = if (runObj.isNull("conclusion")) null else runObj.getString("conclusion"),
                            runNumber = runObj.optInt("run_number", 0),
                            htmlUrl = runObj.optString("html_url", ""),
                            createdAt = runObj.optString("created_at", ""),
                            updatedAt = runObj.optString("updated_at", "")
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWorkflowRun(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<GitHubWorkflowRun> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/runs/$runId"
            val request = buildRequest(token, url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erro ao consultar execução $runId (${response.code})"))
                }
                val body = response.body?.string().orEmpty()
                val runObj = JSONObject(body)
                val run = GitHubWorkflowRun(
                    id = runObj.getLong("id"),
                    name = runObj.optString("name", "Build"),
                    status = runObj.getString("status"),
                    conclusion = if (runObj.isNull("conclusion")) null else runObj.getString("conclusion"),
                    runNumber = runObj.optInt("run_number", 0),
                    htmlUrl = runObj.optString("html_url", ""),
                    createdAt = runObj.optString("created_at", ""),
                    updatedAt = runObj.optString("updated_at", "")
                )
                Result.success(run)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRunJobs(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<List<GitHubWorkflowJob>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs"
            val request = buildRequest(token, url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erro ao obter jobs (${response.code})"))
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val jobsArray = json.optJSONArray("jobs") ?: JSONArray()
                val list = mutableListOf<GitHubWorkflowJob>()
                for (i in 0 until jobsArray.length()) {
                    val jobObj = jobsArray.getJSONObject(i)
                    val stepsArray = jobObj.optJSONArray("steps")
                    var currentStepName: String? = null
                    if (stepsArray != null) {
                        for (j in 0 until stepsArray.length()) {
                            val step = stepsArray.getJSONObject(j)
                            if (step.optString("status") == "in_progress") {
                                currentStepName = step.optString("name")
                                break
                            }
                        }
                    }
                    list.add(
                        GitHubWorkflowJob(
                            id = jobObj.getLong("id"),
                            name = jobObj.optString("name", "Job"),
                            status = jobObj.optString("status", ""),
                            conclusion = if (jobObj.isNull("conclusion")) null else jobObj.optString("conclusion"),
                            currentStepName = currentStepName
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getJobLogs(
        token: String,
        owner: String,
        repo: String,
        jobId: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/jobs/$jobId/logs"
            val request = buildRequest(token, url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Não foi possível carregar logs do job $jobId (${response.code})"))
                }
                val raw = response.body?.string().orEmpty()
                val cleanLogs = stripAnsiCodes(raw)
                Result.success(cleanLogs)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRunArtifacts(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<List<GitHubArtifact>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts"
            val request = buildRequest(token, url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erro ao buscar artefatos (${response.code})"))
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val artifactsArray = json.optJSONArray("artifacts") ?: JSONArray()
                val list = mutableListOf<GitHubArtifact>()
                for (i in 0 until artifactsArray.length()) {
                    val art = artifactsArray.getJSONObject(i)
                    list.add(
                        GitHubArtifact(
                            id = art.getLong("id"),
                            name = art.getString("name"),
                            sizeInBytes = art.optLong("size_in_bytes", 0L),
                            archiveDownloadUrl = art.getString("archive_download_url"),
                            expired = art.optBoolean("expired", false)
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadArtifactZip(
        token: String,
        owner: String,
        repo: String,
        artifactId: Long,
        destinationFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/artifacts/$artifactId/zip"
            val request = buildRequest(token, url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erro ao baixar arquivo do artefato (${response.code})"))
                }
                val inputStream = response.body?.byteStream()
                    ?: return@withContext Result.failure(Exception("Corpo da resposta vazio ao baixar APK."))

                destinationFile.parentFile?.mkdirs()
                FileOutputStream(destinationFile).use { output ->
                    inputStream.copyTo(output)
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelWorkflowRun(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/cancel"
            val request = buildRequest(token, url)
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 202) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Erro ao cancelar execução (${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun stripAnsiCodes(text: String): String {
        return text.replace(Regex("\u001B\\[[;\\d]*m"), "")
    }

    fun getReleaseWorkflowYaml(): String {
        return """
name: Android Release Build

on:
  workflow_dispatch:
    inputs:
      build_id:
        description: 'Identificador único da compilação'
        required: true
        default: 'manual'
      project_name:
        description: 'Nome do Projeto'
        required: false
        default: 'AndroidApp'

concurrency:
  group: android-release-${'$'}{{ inputs.build_id }}
  cancel-in-progress: false

jobs:
  build:
    name: Compilar APK Release
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Baixar Código-Fonte
        uses: actions/checkout@v4

      - name: Configurar Java 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'gradle'

      - name: Permissões de Execução
        run: |
          chmod +x ./gradlew || true

      - name: Executar assembleRelease
        run: |
          echo "=== INICIANDO COMPILAÇÃO RELEASE ==="
          if [ -f "./gradlew" ]; then
            ./gradlew assembleRelease --no-daemon --stacktrace
          else
            gradle assembleRelease --no-daemon --stacktrace
          fi

      - name: Localizar APK Release
        id: locate_apk
        run: |
          mkdir -p build_output
          FOUND_APK=${'$'}(find . -type f -path "*/build/outputs/apk/release/*.apk" | head -n 1)
          if [ -z "${'$'}FOUND_APK" ]; then
            FOUND_APK=${'$'}(find . -type f -name "*.apk" ! -name "*debug*" | head -n 1)
          fi
          
          if [ -z "${'$'}FOUND_APK" ]; then
            echo "::error::Nenhum APK de Release foi encontrado após o assembleRelease."
            exit 1
          fi
          
          echo "APK encontrado em: ${'$'}FOUND_APK"
          cp "${'$'}FOUND_APK" build_output/app-release.apk
          ls -lh build_output/app-release.apk

      - name: Publicar Artefato do APK Release
        uses: actions/upload-artifact@v4
        with:
          name: release-apk-${'$'}{{ inputs.build_id }}
          path: build_output/app-release.apk
          retention-days: 14
          if-no-files-found: error
""".trimIndent()
    }
}
