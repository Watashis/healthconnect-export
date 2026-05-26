package com.healthconnect.export.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GoogleDriveRepository(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveRepo"
    }

    private val gsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = NetHttpTransport()

    /** Allows injecting a mock Drive service for testing. */
    internal var testDrive: Drive? = null

    /**
     * Check if user is signed in to Google
     */
    fun isSignedIn(): Boolean {
        return getLastAccount() != null
    }

    /**
     * Get last signed in account
     */
    fun getLastAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Build Google Sign-In options
     */
    fun getSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder()
            .requestEmail()
            .requestIdToken("730530422387-dveo97h089iesh4etmj74q9dn8j221f1.apps.googleusercontent.com")
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
            .build()
    }

    /**
     * Get Drive service for the signed-in account
     */
    private fun getDriveService(account: GoogleSignInAccount): Drive {
        testDrive?.let { return it }

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(httpTransport, gsonFactory, credential)
            .setApplicationName("HealthConnect Export")
            .build()
    }

    /**
     * Upload a file to Google Drive at the specified path
     */
    suspend fun uploadFile(
        localFile: File,
        drivePath: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val account = getLastAccount() ?: run {
                Log.w(TAG, "uploadFile: no account signed in")
                return@withContext null
            }
            val drive = getDriveService(account)

            // Get or create the folder structure
            val folderId = getOrCreateFolder(drive, "HealthConnectExport")
            if (folderId == null) {
                Log.w(TAG, "uploadFile: failed to get or create folder")
                return@withContext null
            }

            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = localFile.name
                parents = listOf(folderId)
                mimeType = "application/json"
            }

            val mediaContent = FileContent("application/json", localFile)

            // Delete all existing files with the same name before creating a new one
            val existingFiles = drive.files().list()
                .setQ("name='${localFile.name}' and '$folderId' in parents and trashed=false")
                .setSpaces("drive")
                .execute()
                .files

            if (existingFiles.isNotEmpty()) {
                Log.i(TAG, "uploadFile: deleting ${existingFiles.size} existing file(s) for ${localFile.name}")
                existingFiles.forEach { file ->
                    drive.files().delete(file.id).execute()
                    Log.i(TAG, "uploadFile: deleted existing file ${file.id}")
                }
            } else {
                Log.i(TAG, "uploadFile: no existing file to overwrite, creating new")
            }

            // Always create a new file
            val driveFile = drive.files().create(fileMetadata, mediaContent).execute()

            Log.i(TAG, "uploadFile: success - ${driveFile.id}")
            driveFile.id
        } catch (e: Exception) {
            Log.e(TAG, "uploadFile: error uploading ${localFile.name}", e)
            null
        }
    }

    /**
     * Sync multiple local files to Drive
     */
    suspend fun syncFiles(files: List<File>): List<String?> {
        return files.map { uploadFile(it, "HealthConnectExport/${it.name}") }
    }

    /**
     * List files already on Drive
     */
    suspend fun listDriveFiles(): List<String> = withContext(Dispatchers.IO) {
        try {
            val account = getLastAccount() ?: return@withContext emptyList()
            val drive = getDriveService(account)

            val folderId = findFolder(drive, "HealthConnectExport") ?: return@withContext emptyList()

            drive.files().list()
                .setQ("'$folderId' in parents and trashed=false")
                .setSpaces("drive")
                .execute()
                .files
                .map { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun findFolder(drive: Drive, name: String): String? {
        val result = drive.files().list()
            .setQ("mimeType='application/vnd.google-apps.folder' and name='$name' and trashed=false")
            .setSpaces("drive")
            .execute()
        return result.files.firstOrNull()?.id
    }

    private fun getOrCreateFolder(drive: Drive, name: String): String? {
        val existing = findFolder(drive, name)
        if (existing != null) return existing

        val metadata = com.google.api.services.drive.model.File().apply {
            this.name = name
            mimeType = "application/vnd.google-apps.folder"
        }

        return drive.files().create(metadata).execute().id
    }
}
