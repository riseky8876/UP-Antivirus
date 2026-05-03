package com.unplugged.up_antivirus.domain.updates

import android.util.Log
import com.unplugged.hypatia_extensions.HypatiaAccessPoint
import com.unplugged.up_antivirus.common.notifications.NotificationManager
import com.unplugged.upantiviruscommon.datastore.RemoteDataStore
import com.unplugged.upantiviruscommon.model.ScannerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import us.spotco.malwarescanner.Database.UpdateListener
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DefaultDatabaseRepository @Inject constructor(
    private val remoteDataStore: RemoteDataStore,
    private val hypatia: HypatiaAccessPoint,
    private val notificationManager: NotificationManager
) : DatabaseRepository {

    // Gunakan token kosong — database publik Hypatia tidak butuh auth
    private val EMPTY_TOKEN = ""

    override suspend fun getDatabaseVersion(module: ScannerType): Int {
        return try {
            remoteDataStore.getDatabaseVersion(module).version
        } catch (e: Exception) {
            Log.d("DefaultDatabaseRepository", "getDatabaseVersion failed, returning 0: $e")
            // Kembalikan 0 agar update selalu dicoba jika server version tidak tersedia
            0
        }
    }

    override fun errorNotification(title: String, subtitle: String) {
        notificationManager.errorNotification(title, subtitle)
    }

    override suspend fun isDatabaseLoaded(): Boolean {
        return hypatia.isDatabaseLoaded()
    }

    override suspend fun updateDatabase(token: String): Boolean {
        return withContext(Dispatchers.IO) {
            var isResumed = false
            suspendCoroutine { continuation ->
                // Kirim token kosong — hypatia-core AAR akan download dari
                // DATABASE_URL_DEFAULT (divested.dev) tanpa Authorization header
                hypatia.updateDatabase(EMPTY_TOKEN, object : UpdateListener {
                    override fun onSuccess() {
                        synchronized(this) {
                            if (!isResumed) {
                                isResumed = true
                                Log.d("DefaultDatabaseRepository", "Database update SUCCESS")
                                continuation.resume(true)
                            }
                        }
                    }

                    override fun onFailure() {
                        synchronized(this) {
                            if (!isResumed) {
                                isResumed = true
                                Log.d("DefaultDatabaseRepository", "Database update FAILED")
                                continuation.resume(false)
                            }
                        }
                    }
                })
            }
        }
    }

    override suspend fun loadDatabase() {
        hypatia.loadDatabase()
    }

    override suspend fun isDatabaseAvailable(): Boolean {
        return hypatia.isDatabaseAvailable()
    }

    override suspend fun downloadHypatiaFiles() {
        // no-op
    }
}
