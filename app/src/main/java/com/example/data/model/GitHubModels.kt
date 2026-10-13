package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUser(
    @Json(name = "login") val login: String = "",
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "avatar_url") val avatarUrl: String = "",
    @Json(name = "name") val name: String? = null,
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "public_repos") val publicRepos: Int = 0,
    @Json(name = "total_private_repos") val totalPrivateRepos: Int? = null,
    @Json(name = "bio") val bio: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubRepo(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "name") val name: String = "",
    @Json(name = "full_name") val fullName: String = "",
    @Json(name = "private") val isPrivate: Boolean = false,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "description") val description: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String = "Repositório criado pelo GitCompile para compilações em nuvem",
    @Json(name = "private") val private: Boolean = true,
    @Json(name = "auto_init") val autoInit: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GitBlobRequest(
    @Json(name = "content") val content: String,
    @Json(name = "encoding") val encoding: String = "base64"
)

@JsonClass(generateAdapter = true)
data class GitBlobResponse(
    @Json(name = "sha") val sha: String = "",
    @Json(name = "url") val url: String = ""
)

@JsonClass(generateAdapter = true)
data class GitTreeItem(
    @Json(name = "path") val path: String,
    @Json(name = "mode") val mode: String = "100644",
    @Json(name = "type") val type: String = "blob",
    @Json(name = "sha") val sha: String? = null,
    @Json(name = "content") val content: String? = null
)

@JsonClass(generateAdapter = true)
data class GitTreeRequest(
    @Json(name = "base_tree") val baseTree: String? = null,
    @Json(name = "tree") val tree: List<GitTreeItem>
)

@JsonClass(generateAdapter = true)
data class GitTreeResponse(
    @Json(name = "sha") val sha: String = "",
    @Json(name = "url") val url: String = ""
)

@JsonClass(generateAdapter = true)
data class GitCommitRequest(
    @Json(name = "message") val message: String,
    @Json(name = "tree") val tree: String,
    @Json(name = "parents") val parents: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GitCommitResponse(
    @Json(name = "sha") val sha: String = "",
    @Json(name = "url") val url: String = ""
)

@JsonClass(generateAdapter = true)
data class GitRefResponse(
    @Json(name = "ref") val ref: String = "",
    @Json(name = "node_id") val nodeId: String = "",
    @Json(name = "url") val url: String = "",
    @Json(name = "object") val obj: GitRefObject? = null
)

@JsonClass(generateAdapter = true)
data class GitRefObject(
    @Json(name = "sha") val sha: String = "",
    @Json(name = "type") val type: String = "",
    @Json(name = "url") val url: String = ""
)

@JsonClass(generateAdapter = true)
data class GitUpdateRefRequest(
    @Json(name = "sha") val sha: String,
    @Json(name = "force") val force: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GitCreateRefRequest(
    @Json(name = "ref") val ref: String,
    @Json(name = "sha") val sha: String
)

@JsonClass(generateAdapter = true)
data class WorkflowRunsResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    @Json(name = "workflow_runs") val workflowRuns: List<WorkflowRun> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkflowRun(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "name") val name: String = "",
    @Json(name = "status") val status: String = "queued", // queued, in_progress, completed
    @Json(name = "conclusion") val conclusion: String? = null, // success, failure, neutral, cancelled, timed_out, action_required
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "run_number") val runNumber: Int = 0,
    @Json(name = "event") val event: String = "",
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "updated_at") val updatedAt: String = "",
    @Json(name = "head_branch") val headBranch: String = "",
    @Json(name = "head_sha") val headSha: String = ""
)

@JsonClass(generateAdapter = true)
data class WorkflowJobsResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    @Json(name = "jobs") val jobs: List<WorkflowJob> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkflowJob(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "run_id") val runId: Long = 0L,
    @Json(name = "name") val name: String = "",
    @Json(name = "status") val status: String = "queued", // queued, in_progress, completed
    @Json(name = "conclusion") val conclusion: String? = null, // success, failure, cancelled
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null,
    @Json(name = "steps") val steps: List<WorkflowStep> = emptyList(),
    @Json(name = "html_url") val htmlUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class WorkflowStep(
    @Json(name = "name") val name: String = "",
    @Json(name = "status") val status: String = "queued", // queued, in_progress, completed
    @Json(name = "conclusion") val conclusion: String? = null, // success, failure, skipped
    @Json(name = "number") val number: Int = 0,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ArtifactsResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    @Json(name = "artifacts") val artifacts: List<ArtifactItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ArtifactItem(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "name") val name: String = "",
    @Json(name = "size_in_bytes") val sizeInBytes: Long = 0L,
    @Json(name = "archive_download_url") val archiveDownloadUrl: String = "",
    @Json(name = "expired") val expired: Boolean = false
)

@JsonClass(generateAdapter = true)
data class WorkflowDispatchRequest(
    @Json(name = "ref") val ref: String,
    @Json(name = "inputs") val inputs: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class WorkflowsResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    @Json(name = "workflows") val workflows: List<WorkflowItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkflowItem(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "name") val name: String = "",
    @Json(name = "path") val path: String = "",
    @Json(name = "state") val state: String = ""
)

