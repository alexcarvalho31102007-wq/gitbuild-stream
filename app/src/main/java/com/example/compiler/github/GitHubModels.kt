package com.example.compiler.github

data class GitHubUser(
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val publicRepos: Int
)

data class GitHubRepo(
    val name: String,
    val fullName: String,
    val owner: String,
    val isPrivate: Boolean,
    val defaultBranch: String
)

data class GitHubWorkflowRun(
    val id: Long,
    val name: String,
    val status: String, // "queued", "in_progress", "completed"
    val conclusion: String?, // "success", "failure", "cancelled", null
    val runNumber: Int,
    val htmlUrl: String,
    val createdAt: String,
    val updatedAt: String
)

data class GitHubWorkflowJob(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val currentStepName: String?
)

data class GitHubArtifact(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val archiveDownloadUrl: String,
    val expired: Boolean
)

data class TreeItem(
    val path: String,
    val mode: String = "100644", // "100644" for normal file, "100755" for executable (e.g. gradlew)
    val type: String = "blob",
    val sha: String
)
