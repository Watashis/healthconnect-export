package com.healthconnect.export.repository

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.healthconnect.export.data.ExportConfig
import com.healthconnect.export.data.ExportFrequency
import com.healthconnect.export.data.HealthDataType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import java.io.File
import java.lang.reflect.Field

@RunWith(MockitoJUnitRunner.Silent::class)
class GoogleDriveRepositoryTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockAccount: GoogleSignInAccount

    @Mock
    private lateinit var mockDrive: Drive

    @Mock
    private lateinit var mockFiles: Drive.Files

    // Drive API request mocks
    @Mock
    private lateinit var mockListRequest: Drive.Files.List

    @Mock
    private lateinit var mockCreateRequest: Drive.Files.Create

    @Mock
    private lateinit var mockUpdateRequest: Drive.Files.Update

    // Response mocks
    @Mock
    private lateinit var mockFolderListResponse: com.google.api.services.drive.model.FileList

    @Mock
    private lateinit var mockFileListResponse: com.google.api.services.drive.model.FileList

    @Mock
    private lateinit var mockEmptyFolderListResponse: com.google.api.services.drive.model.FileList

    @Mock
    private lateinit var mockEmptyFileListResponse: com.google.api.services.drive.model.FileList

    @Mock
    private lateinit var mockFolderFile: com.google.api.services.drive.model.File

    @Mock
    private lateinit var mockExistingFile: com.google.api.services.drive.model.File

    @Mock
    private lateinit var mockCreatedFile: com.google.api.services.drive.model.File

    @Mock
    private lateinit var mockUpdatedFile: com.google.api.services.drive.model.File

    @Mock
    private lateinit var mockDeleteRequest: Drive.Files.Delete

    private lateinit var repo: GoogleDriveRepository
    private lateinit var repoSpy: GoogleDriveRepository

    private val localTestFile = File("test.json")
    private val defaultConfig = ExportConfig(
        enabledTypes = setOf(HealthDataType.STEPS),
        frequency = ExportFrequency.DAILY,
        autoSyncDrive = false
    )

    @Before
    fun setup() {
        repo = GoogleDriveRepository(mockContext)

        // Inject mock Drive via testDrive field
        val driveField: Field = GoogleDriveRepository::class.java.getDeclaredField("testDrive")
        driveField.isAccessible = true
        driveField.set(repo, mockDrive)

        // Spy on the repo to stub getLastAccount()
        repoSpy = spy(repo)
        // Default: no account signed in
        doReturn(null).`when`(repoSpy).getLastAccount()

        // Setup Drive.Files chain
        whenever(mockDrive.files()).thenReturn(mockFiles)

        // Setup list request chain — all setters return this
        whenever(mockListRequest.setQ(any())).thenReturn(mockListRequest)
        whenever(mockListRequest.setSpaces(any())).thenReturn(mockListRequest)

        // Setup create request
        whenever(mockCreatedFile.id).thenReturn("new_file_id")

        // Setup update request
        whenever(mockUpdatedFile.id).thenReturn("updated_file_id")
        whenever(mockUpdateRequest.execute()).thenReturn(mockUpdatedFile)

        // Setup delete request (execute returns Void, default null is fine)
        whenever(mockFiles.delete(any())).thenReturn(mockDeleteRequest)

        // Setup folder response — default: folder exists
        val folderList = listOf(mockFolderFile)
        whenever(mockFolderFile.id).thenReturn("folder_id")
        whenever(mockFolderListResponse.files).thenReturn(folderList)

        // Setup empty responses
        whenever(mockEmptyFolderListResponse.files).thenReturn(emptyList())
        whenever(mockEmptyFileListResponse.files).thenReturn(emptyList())

        // Setup file responses — default: no existing file
        whenever(mockFileListResponse.files).thenReturn(listOf(mockExistingFile))
        whenever(mockExistingFile.id).thenReturn("existing_file_id")
    }

    @After
    fun tearDown() {
    }

    // ============================
    // isSignedIn() Tests
    // ============================

    @Test
    fun `isSignedIn returns true when account exists`() {
        doReturn(mockAccount).`when`(repoSpy).getLastAccount()

        assertTrue(repoSpy.isSignedIn())
    }

    @Test
    fun `isSignedIn returns false when no account`() {
        assertFalse(repoSpy.isSignedIn())
    }

    // ============================
    // getLastAccount() Tests
    // ============================

    @Test
    fun `getLastAccount returns account when signed in`() {
        doReturn(mockAccount).`when`(repoSpy).getLastAccount()

        assertEquals(mockAccount, repoSpy.getLastAccount())
    }

    @Test
    fun `getLastAccount returns null when no account`() {
        assertNull(repoSpy.getLastAccount())
    }

    // ============================
    // getSignInOptions() Tests
    // ============================

    @Test
    fun `getSignInOptions builds options without throwing`() {
        val options = repoSpy.getSignInOptions()

        assertNotNull(options)
    }

    // ============================
    // uploadFile() Tests
    // ============================

    @Test
    fun `uploadFile returns null when no account signed in`() {
        runBlocking {
            // Default: getLastAccount() returns null
            val result = repoSpy.uploadFile(localTestFile, "HealthConnectExport/test.json")

            assertNull(result)
            // Drive service should not be called at all
            verify(mockDrive, never()).files()
        }
    }

    @Test
    fun `uploadFile returns null when folder creation fails`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()

            // No existing folder → try to create → throws
            whenever(mockFiles.list()).thenReturn(mockListRequest)
            whenever(mockListRequest.execute()).thenReturn(mockEmptyFolderListResponse)

            val createFolderRequest = mock<Drive.Files.Create>()
            whenever(createFolderRequest.execute()).thenThrow(RuntimeException("Folder creation failed"))
            whenever(mockFiles.create(any())).thenReturn(createFolderRequest)

            val result = repoSpy.uploadFile(localTestFile, "HealthConnectExport/test.json")

            assertNull(result)
            verify(mockFiles).create(any())
        }
    }

    @Test
    fun `uploadFile creates new file when no existing file`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()

            // First execute (findFolder): returns existing folder
            // Second execute (check existing files): returns empty list
            whenever(mockFiles.list()).thenReturn(mockListRequest)
            whenever(mockListRequest.execute()).thenReturn(
                mockFolderListResponse,   // folder found
                mockEmptyFileListResponse  // no existing file
            )

            whenever(mockFiles.create(any(), any())).thenReturn(mockCreateRequest)
            whenever(mockCreateRequest.execute()).thenReturn(mockCreatedFile)

            val result = repoSpy.uploadFile(localTestFile, "HealthConnectExport/test.json")

            assertEquals("new_file_id", result)
            verify(mockFiles).create(any(), any()) // creates new file
            verify(mockFiles, never()).delete(any()) // no existing files to delete
            verify(mockFiles, never()).update(any(), any(), any())
        }
    }

    @Test
    fun `uploadFile deletes existing file then creates new one`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()

            // First execute (findFolder): returns existing folder
            // Second execute (check existing files): returns existing file
            whenever(mockFiles.list()).thenReturn(mockListRequest)
            whenever(mockListRequest.execute()).thenReturn(
                mockFolderListResponse,  // folder found
                mockFileListResponse     // existing file found
            )

            whenever(mockFiles.create(any(), any())).thenReturn(mockCreateRequest)
            whenever(mockCreateRequest.execute()).thenReturn(mockCreatedFile)

            val result = repoSpy.uploadFile(localTestFile, "HealthConnectExport/test.json")

            assertEquals("new_file_id", result)
            // Existing file was deleted first
            verify(mockFiles).delete(eq("existing_file_id"))
            verify(mockDeleteRequest).execute()
            // Then new file was created
            verify(mockFiles).create(any(), any())
            verify(mockFiles, never()).update(any(), any(), any())
        }
    }

    @Test
    fun `uploadFile deletes multiple existing files then creates new one`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()

            val file1 = mock<com.google.api.services.drive.model.File>()
            whenever(file1.id).thenReturn("file_1_id")
            val file2 = mock<com.google.api.services.drive.model.File>()
            whenever(file2.id).thenReturn("file_2_id")
            val multiFileResponse = mock<com.google.api.services.drive.model.FileList>()
            whenever(multiFileResponse.files).thenReturn(listOf(file1, file2))

            // First execute (findFolder): returns existing folder
            // Second execute (check existing files): returns multiple files
            whenever(mockFiles.list()).thenReturn(mockListRequest)
            whenever(mockListRequest.execute()).thenReturn(
                mockFolderListResponse,  // folder found
                multiFileResponse        // multiple existing files
            )

            whenever(mockFiles.create(any(), any())).thenReturn(mockCreateRequest)
            whenever(mockCreateRequest.execute()).thenReturn(mockCreatedFile)

            val result = repoSpy.uploadFile(localTestFile, "HealthConnectExport/test.json")

            assertEquals("new_file_id", result)
            // Both existing files were deleted
            verify(mockFiles, times(2)).delete(any())
            verify(mockFiles).delete(eq("file_1_id"))
            verify(mockFiles).delete(eq("file_2_id"))
            // Then new file was created
            verify(mockFiles).create(any(), any())
            verify(mockFiles, never()).update(any(), any(), any())
        }
    }

    @Test
    fun `uploadFile returns null on exception`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()

            whenever(mockFiles.list()).thenThrow(RuntimeException("Network error"))

            val result = repoSpy.uploadFile(localTestFile, "HealthConnectExport/test.json")

            assertNull(result)
        }
    }

    // ============================
    // syncFiles() Tests
    // ============================

    @Test
    fun `syncFiles returns list of ids for multiple files`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()

            val file1 = File("health_2026-05-24.json")
            val file2 = File("health_2026-05-25.json")

            // First uploadFile call: list() called 2 times (findFolder + findFile)
            // Second uploadFile call: list() called 2 more times
            whenever(mockFiles.list()).thenReturn(mockListRequest)
            whenever(mockListRequest.execute()).thenReturn(
                mockFolderListResponse,   // findFolder (1st call)
                mockEmptyFileListResponse, // findFile (1st call)
                mockFolderListResponse,   // findFolder (2nd call)
                mockEmptyFileListResponse  // findFile (2nd call)
            )

            whenever(mockFiles.create(any(), any())).thenReturn(mockCreateRequest)
            whenever(mockCreateRequest.execute()).thenReturn(mockCreatedFile)

            val results = repoSpy.syncFiles(listOf(file1, file2))

            assertEquals(2, results.size)
            assertEquals("new_file_id", results[0])
            assertEquals("new_file_id", results[1])
            verify(mockFiles, times(2)).create(any(), any())
        }
    }

    @Test
    fun `syncFiles with empty list returns empty list`() {
        runBlocking {
            val results = repoSpy.syncFiles(emptyList())

            assertTrue(results.isEmpty())
            // No Drive calls should be made
            verify(mockDrive, never()).files()
        }
    }

    @Test
    fun `syncFiles with no account returns list of nulls`() {
        runBlocking {
            val results = repoSpy.syncFiles(listOf(File("test.json"), File("test2.json")))

            assertEquals(2, results.size)
            assertNull(results[0])
            assertNull(results[1])
            verify(mockDrive, never()).files()
        }
    }

    // ============================
    // listDriveFiles() Tests
    // ============================

    @Test
    fun `listDriveFiles returns empty list when no account`() {
        runBlocking {
            val result = repoSpy.listDriveFiles()

            assertTrue(result.isEmpty())
            verify(mockDrive, never()).files()
        }
    }

    @Test
    fun `listDriveFiles returns empty when no folder on drive`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()

            whenever(mockFiles.list()).thenReturn(mockListRequest)
            whenever(mockListRequest.execute()).thenReturn(mockEmptyFolderListResponse)

            val result = repoSpy.listDriveFiles()

            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun `listDriveFiles returns file names when folder exists`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()

            val file1 = mock<com.google.api.services.drive.model.File>()
            whenever(file1.name).thenReturn("health_2026-05-24.json")
            val file2 = mock<com.google.api.services.drive.model.File>()
            whenever(file2.name).thenReturn("health_2026-05-25.json")
            val fileListResponse = mock<com.google.api.services.drive.model.FileList>()
            whenever(fileListResponse.files).thenReturn(listOf(file1, file2))

            // First execute: findFolder → returns folder
            // Second execute: list files → returns files
            whenever(mockFiles.list()).thenReturn(mockListRequest)
            whenever(mockListRequest.execute()).thenReturn(
                mockFolderListResponse,
                fileListResponse
            )

            val result = repoSpy.listDriveFiles()

            assertEquals(2, result.size)
            assertEquals("health_2026-05-24.json", result[0])
            assertEquals("health_2026-05-25.json", result[1])
        }
    }

    @Test
    fun `listDriveFiles returns empty on exception`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()

            whenever(mockFiles.list()).thenThrow(RuntimeException("Network error"))

            val result = repoSpy.listDriveFiles()

            assertTrue(result.isEmpty())
        }
    }

    // ============================
    // Edge Cases
    // ============================

    @Test
    fun `uploadFile creates folder when does not exist`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()

            // Folder doesn't exist → create folder → then check files
            whenever(mockFiles.list()).thenReturn(mockListRequest)
            whenever(mockListRequest.execute()).thenReturn(
                mockEmptyFolderListResponse,  // no existing folder
                mockEmptyFileListResponse      // no existing file
            )

            val createFolderResponse = mock<com.google.api.services.drive.model.File>()
            whenever(createFolderResponse.id).thenReturn("new_folder_id")
            val createFolderRequest = mock<Drive.Files.Create>()
            whenever(createFolderRequest.execute()).thenReturn(createFolderResponse)
            whenever(mockFiles.create(any())).thenReturn(createFolderRequest)
            whenever(mockFiles.create(any(), any())).thenReturn(mockCreateRequest)
            whenever(mockCreateRequest.execute()).thenReturn(mockCreatedFile)

            val result = repoSpy.uploadFile(localTestFile, "HealthConnectExport/test.json")

            assertEquals("new_file_id", result)
            // Verify folder was created
            verify(mockFiles).create(any()) // folder creation
            verify(mockFiles).create(any(), any()) // file creation
        }
    }

    @Test
    fun `uploadFile returns null when existing file delete throws`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()

            whenever(mockFiles.list()).thenReturn(mockListRequest)
            whenever(mockListRequest.execute()).thenReturn(
                mockFolderListResponse,  // folder found
                mockFileListResponse     // existing file found
            )

            // Delete throws — should be caught and return null
            whenever(mockFiles.delete(any())).thenReturn(mockDeleteRequest)
            whenever(mockDeleteRequest.execute()).thenThrow(RuntimeException("Delete failed"))

            val result = repoSpy.uploadFile(localTestFile, "HealthConnectExport/test.json")

            assertNull(result)
            verify(mockFiles).delete(eq("existing_file_id"))
            // Create should NOT be called since delete failed
            verify(mockFiles, never()).create(any(), any())
        }
    }

    @Test
    fun `listDriveFiles exception after folder found, returns empty`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()

            // Folder found but subsequent file list throws
            whenever(mockFiles.list()).thenReturn(mockListRequest)
            whenever(mockListRequest.execute()).thenReturn(
                mockFolderListResponse   // folder found (first execute)
            )
            // Second list() call throws
            val mockListRequest2 = mock<Drive.Files.List>()
            whenever(mockListRequest2.setQ(any())).thenReturn(mockListRequest2)
            whenever(mockListRequest2.setSpaces(any())).thenReturn(mockListRequest2)
            whenever(mockListRequest2.execute()).thenThrow(RuntimeException("List files failed"))
            // Second files().list() returns the throwing mock
            whenever(mockFiles.list()).thenReturn(mockListRequest, mockListRequest2)

            val result = repoSpy.listDriveFiles()

            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun `getSignInOptions requests correct scopes`() {
        val options = repoSpy.getSignInOptions()

        assertNotNull(options)
    }

    @Test
    fun `uploadFile with special characters in filename uses escaped query`() {
        runBlocking {
            doReturn(mockAccount).`when`(repoSpy).getLastAccount()
            val specialFile = File("health_2026-05-24(+1).json")

            whenever(mockFiles.list()).thenReturn(mockListRequest)
            whenever(mockListRequest.execute()).thenReturn(
                mockFolderListResponse,   // folder found
                mockEmptyFileListResponse  // no existing file
            )

            whenever(mockFiles.create(any(), any())).thenReturn(mockCreateRequest)
            whenever(mockCreateRequest.execute()).thenReturn(mockCreatedFile)

            val result = repoSpy.uploadFile(specialFile, "HealthConnectExport/health_2026-05-24(+1).json")

            assertEquals("new_file_id", result)
            // Verify the Drive query contains the filename with special chars
            argumentCaptor<String>().apply {
                verify(mockListRequest, atLeast(1)).setQ(capture())
                assertTrue(lastValue.contains("health_2026-05-24(+1).json"))
            }
            verify(mockFiles).create(any(), any())
        }
    }
}
