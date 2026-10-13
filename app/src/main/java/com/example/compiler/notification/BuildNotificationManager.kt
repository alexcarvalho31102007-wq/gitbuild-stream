package com.example.compiler.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.compiler.data.CompilerPreferences

class BuildNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val preferences = CompilerPreferences(context)

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal de progresso (silencioso, em andamento)
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS,
                "Compilação em Andamento",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificações do andamento do processo de compilação"
                setSound(null, null)
                enableVibration(false)
            }

            // Canal de sucesso (som positivo, vibração curta)
            val successSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val successChannel = NotificationChannel(
                CHANNEL_SUCCESS,
                "Compilação Concluída com Sucesso",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de sucesso quando o APK Release for gerado"
                setSound(successSound, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 200)
            }

            // Canal de falha (som de alerta, padrão de vibração duplo forte)
            val failureSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val failureAudioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            val failureChannel = NotificationChannel(
                CHANNEL_FAILURE,
                "Falhas de Compilação",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações com alerta sonoro quando uma compilação falhar"
                setSound(failureSound, failureAudioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 200, 350)
            }

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(successChannel)
            notificationManager.createNotificationChannel(failureChannel)
        }
    }

    fun buildForegroundNotification(
        buildId: String,
        projectName: String,
        statusDetail: String
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            buildId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_build)
            .setContentTitle("Compilando: $projectName")
            .setContentText(statusDetail)
            .setSubText("#$buildId")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notifyStarted(buildId: String, projectName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_build)
            .setContentTitle("Compilação Iniciada")
            .setContentText("Enviando código de $projectName para compilação Release...")
            .setSubText("#$buildId")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(buildId.hashCode(), notification)
    }

    fun notifySuccess(buildId: String, projectName: String, apkFileName: String?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            buildId.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundEnabled = preferences.isSoundEnabled
        val vibEnabled = preferences.isVibrationEnabled

        val builder = NotificationCompat.Builder(context, CHANNEL_SUCCESS)
            .setSmallIcon(R.drawable.ic_build)
            .setContentTitle("Compilação Concluída!")
            .setContentText("APK Release de $projectName pronto para download.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "O projeto $projectName foi compilado com sucesso no GitHub Actions.\nArquivo: ${apkFileName ?: "app-release.apk"}"
                )
            )
            .setSubText("#$buildId")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (!soundEnabled) {
            builder.setSilent(true)
        }
        if (!vibEnabled) {
            builder.setVibrate(null)
        }

        notificationManager.notify(buildId.hashCode() + 10, builder.build())
    }

    fun notifyFailure(buildId: String, projectName: String, errorMessage: String?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            buildId.hashCode() + 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundEnabled = preferences.isSoundEnabled
        val vibEnabled = preferences.isVibrationEnabled

        val displayError = errorMessage?.take(150) ?: "Falha na tarefa assembleRelease do Gradle."

        val builder = NotificationCompat.Builder(context, CHANNEL_FAILURE)
            .setSmallIcon(R.drawable.ic_build)
            .setContentTitle("Falha na Compilação: $projectName")
            .setContentText(displayError)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "A compilação do projeto $projectName falhou no GitHub Actions.\n$displayError\nToque para inspecionar os logs completos."
                )
            )
            .setSubText("#$buildId")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (!soundEnabled) {
            builder.setSilent(true)
        }
        if (!vibEnabled) {
            builder.setVibrate(null)
        }

        notificationManager.notify(buildId.hashCode() + 20, builder.build())
    }

    fun cancelProgressNotification(buildId: String) {
        notificationManager.cancel(buildId.hashCode())
    }

    companion object {
        const val CHANNEL_PROGRESS = "build_channel_progress"
        const val CHANNEL_SUCCESS = "build_channel_success"
        const val CHANNEL_FAILURE = "build_channel_failure"
    }
}
