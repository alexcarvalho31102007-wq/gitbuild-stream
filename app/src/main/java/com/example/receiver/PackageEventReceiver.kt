package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.UninstalledAppEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class PackageEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageEventReceiver"
        private val _packageEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
        val packageEvents = _packageEvents.asSharedFlow()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val packageName = intent.data?.schemeSpecificPart ?: return
        val action = intent.action ?: return
        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

        Log.d(TAG, "Recebida ação $action para o pacote $packageName (isReplacing=$isReplacing)")

        when (action) {
            Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                if (!isReplacing) {
                    val pendingResult = goAsync()
                    scope.launch {
                        try {
                            val db = AppDatabase.getInstance(context)
                            val dao = db.appDao()
                            val snapshot = dao.getSnapshot(packageName)
                            
                            val appName = snapshot?.appName ?: packageName
                            val versionName = snapshot?.versionName ?: ""
                            val versionCode = snapshot?.versionCode ?: 0L
                            val isSystem = snapshot?.isSystemApp ?: false
                            val origin = snapshot?.originLabel ?: "Desconhecido"
                            val installer = snapshot?.installerPackageName

                            val uninstalledEntry = UninstalledAppEntity(
                                packageName = packageName,
                                appName = appName,
                                versionName = versionName,
                                versionCode = versionCode,
                                isSystemApp = isSystem,
                                originLabel = origin,
                                installerPackageName = installer,
                                uninstalledTimestamp = System.currentTimeMillis(),
                                uninstalledReason = "Desinstalado por ferramenta externa (detectado via Broadcast)",
                                isRestorableViaShizuku = isSystem
                            )
                            dao.insertUninstalledApp(uninstalledEntry)
                            dao.deleteSnapshot(packageName)
                            _packageEvents.tryEmit(packageName)
                            Log.d(TAG, "Pacote $packageName registrado na lixeira.")
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao processar remoção de $packageName", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            Intent.ACTION_PACKAGE_ADDED -> {
                if (!isReplacing) {
                    val pendingResult = goAsync()
                    scope.launch {
                        try {
                            val db = AppDatabase.getInstance(context)
                            val dao = db.appDao()
                            // Se foi reinstalado, remover da lixeira
                            dao.deleteUninstalledApp(packageName)
                            _packageEvents.tryEmit(packageName)
                            Log.d(TAG, "Pacote $packageName reinstalado, removido da lixeira.")
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao processar adição de $packageName", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
