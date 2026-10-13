package com.example.compiler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BuildStatus {
    PREPARING,
    UPLOADING,
    DISPATCHED,
    QUEUED,
    BUILDING,
    DOWNLOADING_ARTIFACT,
    SUCCESS,
    FAILED,
    CANCELLED;

    val isFinished: Boolean
        get() = this == SUCCESS || this == FAILED || this == CANCELLED

    val isActive: Boolean
        get() = !isFinished

    val displayLabel: String
        get() = when (this) {
            PREPARING -> "Preparando Projeto"
            UPLOADING -> "Enviando para o GitHub"
            DISPATCHED -> "Iniciando Workflow"
            QUEUED -> "Na fila do Runner"
            BUILDING -> "Compilando Release APK"
            DOWNLOADING_ARTIFACT -> "Baixando APK"
            SUCCESS -> "Concluída com Sucesso"
            FAILED -> "Falha na Compilação"
            CANCELLED -> "Cancelada"
        }
}

@Entity(tableName = "build_records")
data class BuildRecordEntity(
    @PrimaryKey val id: String, // ex: BLD-20261012-A1B2
    val projectName: String,
    val packageName: String,
    val fileCount: Int,
    val zipSizeBytes: Long,
    val createdAtTimestamp: Long,
    val finishedAtTimestamp: Long? = null,
    val status: BuildStatus,
    val statusDetail: String,
    val repoOwner: String,
    val repoName: String,
    val branchName: String,
    val commitSha: String? = null,
    val workflowRunId: Long? = null,
    val runNumber: Int? = null,
    val htmlUrl: String? = null,
    val artifactId: Long? = null,
    val apkFileName: String? = null,
    val apkLocalPath: String? = null,
    val apkSizeBytes: Long? = null,
    val downloadUrl: String? = null,
    val logs: String? = null,
    val errorMessage: String? = null
)
