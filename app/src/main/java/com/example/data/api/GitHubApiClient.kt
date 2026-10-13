package com.example.data.api

import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface GitHubApiService {

    @GET("user")
    suspend fun getCurrentUser(): GitHubUser

    @GET("user/repos")
    suspend fun getUserRepos(
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100,
        @Query("affiliation") affiliation: String = "owner,collaborator"
    ): List<GitHubRepo>

    @POST("user/repos")
    suspend fun createRepo(
        @Body request: CreateRepoRequest
    ): GitHubRepo

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepo

    @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
    suspend fun getBranchRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): Response<GitRefResponse>

    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: GitBlobRequest
    ): GitBlobResponse

    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: GitTreeRequest
    ): GitTreeResponse

    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: GitCommitRequest
    ): GitCommitResponse

    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateBranchRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Body request: GitUpdateRefRequest
    ): GitRefResponse

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: GitCreateRefRequest
    ): GitRefResponse

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("branch") branch: String? = null,
        @Query("per_page") perPage: Int = 10
    ): WorkflowRunsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}")
    suspend fun getWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): WorkflowRun

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun getWorkflowRunJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): WorkflowJobsResponse

    @Streaming
    @GET("repos/{owner}/{repo}/actions/jobs/{job_id}/logs")
    suspend fun getJobLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("job_id") jobId: Long
    ): Response<ResponseBody>

    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/cancel")
    suspend fun cancelWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun getWorkflowArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): ArtifactsResponse

    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun getWorkflows(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): WorkflowsResponse

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Body request: WorkflowDispatchRequest
    ): Response<Unit>
}

class GitHubApiClient(private val tokenProvider: () -> String) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val token = tokenProvider().trim()
            val requestBuilder = original.newBuilder()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "GitCompile-Android-App")

            if (token.isNotEmpty() && original.url.host.contains("github.com")) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: GitHubApiService = retrofit.create(GitHubApiService::class.java)

    /**
     * Download text logs for a job safely
     */
    suspend fun fetchJobLogsText(owner: String, repo: String, jobId: Long): String = withContext(Dispatchers.IO) {
        try {
            val response = api.getJobLogs(owner, repo, jobId)
            if (response.isSuccessful) {
                response.body()?.string() ?: "Nenhum log disponível para esta etapa ainda."
            } else {
                "Logs em preparação pelo GitHub Actions... (${response.code()})"
            }
        } catch (e: Exception) {
            "Carregando logs do GitHub Actions..."
        }
    }

    /**
     * Download artifact zip directly, following 302 redirects safely without leaking Authorization header to storage hosts
     */
    suspend fun downloadArtifactZip(
        downloadUrl: String,
        outputFile: java.io.File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = tokenProvider().trim()

            // 1. Initial request to GitHub API (without automatic redirect to capture Location)
            val noRedirectClient = okHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val initialRequest = okhttp3.Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "GitCompile-Android-App")
                .apply {
                    if (token.isNotEmpty()) {
                        header("Authorization", "Bearer $token")
                    }
                }
                .build()

            val initialResponse = noRedirectClient.newCall(initialRequest).execute()
            val code = initialResponse.code
            val location = initialResponse.header("Location")

            val targetResponse: okhttp3.Response
            if (code in 300..399 && !location.isNullOrBlank()) {
                initialResponse.close()

                // 2. Fetch the actual content from the presigned URL (S3 / Azure Blob) WITHOUT Authorization header
                val storageClient = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val storageRequest = okhttp3.Request.Builder()
                    .url(location)
                    .header("User-Agent", "GitCompile-Android-App")
                    .build()

                targetResponse = storageClient.newCall(storageRequest).execute()
            } else if (initialResponse.isSuccessful) {
                targetResponse = initialResponse
            } else {
                initialResponse.close()
                return@withContext false
            }

            if (!targetResponse.isSuccessful) {
                targetResponse.close()
                return@withContext false
            }

            val body = targetResponse.body ?: run {
                targetResponse.close()
                return@withContext false
            }

            val contentLength = body.contentLength()
            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            onProgress((totalRead.toFloat() / contentLength).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
            targetResponse.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
