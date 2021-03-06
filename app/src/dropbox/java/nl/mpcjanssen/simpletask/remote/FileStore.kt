package nl.mpcjanssen.simpletask.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.WriteMode
import nl.mpcjanssen.simpletask.Constants
import nl.mpcjanssen.simpletask.Logger
import nl.mpcjanssen.simpletask.TodoApplication
import nl.mpcjanssen.simpletask.remote.IFileStore.Companion.ROOT_DIR
import nl.mpcjanssen.simpletask.task.TodoList.queue
import nl.mpcjanssen.simpletask.util.Config
import nl.mpcjanssen.simpletask.util.join
import nl.mpcjanssen.simpletask.util.showToastLong
import java.io.*
import kotlin.reflect.KClass

/**
 * FileStore implementation backed by Dropbox
 * Dropbox V2 API docs suck, most of the V2 code was inspired by https://www.sitepoint.com/adding-the-dropbox-api-to-an-android-app/
 */
object FileStore : IFileStore {

    private val TAG = "FileStore"
    private val CACHE_PREFS = "dropboxMeta"
    private val OAUTH2_TOKEN = "dropboxV2Token"

    private val log: Logger = Logger
    private val mPrefs: SharedPreferences?

    private val mApp = TodoApplication.app

    init {
        mPrefs = mApp.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    }

    private val dbxClient by lazy {
        val accessToken = getAccessToken()
        val requestConfig = DbxRequestConfig.newBuilder("simpletask").build()
        val client = DbxClientV2(requestConfig, accessToken)
        client
    }

    private fun getAccessToken(): String? {
        return mPrefs?.getString(OAUTH2_TOKEN, null)
    }

    fun setAccessToken(accessToken: String?) {
        val edit = mPrefs?.edit()
        edit?.let {
            if (accessToken == null) {
                edit.remove(OAUTH2_TOKEN).apply()
            } else {
                edit.putString(OAUTH2_TOKEN, accessToken).apply()
            }
        }
    }

    override val isAuthenticated: Boolean
        get() = getAccessToken() != null

    override fun logout() {
        setAccessToken(null)
    }

    override fun getRemoteVersion(filename: String): String {
        val data = dbxClient.files().getMetadata(filename) as FileMetadata
        return data.rev
    }

    override val isOnline: Boolean
        get() {
            val cm = mApp.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val netInfo = cm.activeNetworkInfo
            return netInfo != null && netInfo.isConnected
        }

    override fun loadTasksFromFile(path: String, eol: String): RemoteContents {

        // If we load a file and changes are pending, we do not want to overwrite
        // our local changes, instead we upload local and handle any conflicts
        // on the dropbox side.

        log.info(TAG, "Loading file from Dropbox: " + path)
        if (!isAuthenticated) {
            throw IOException("Not authenticated")
        }
        val readLines = ArrayList<String>()

        val download = dbxClient.files().download(path)
        val openFileStream = download.inputStream
        val fileInfo = download.result
        log.info(TAG, "The file's rev is: " + fileInfo.rev)

        val reader = BufferedReader(InputStreamReader(openFileStream, "UTF-8"))

        reader.forEachLine { line ->
            readLines.add(line)
        }
        openFileStream.close()
        return RemoteContents(remoteId = fileInfo.rev, contents = readLines)
    }

    override fun loginActivity(): KClass<*>? {
        return LoginScreen::class
    }

    @Synchronized
    @Throws(IOException::class)
    override fun saveTasksToFile(path: String, lines: List<String>, eol: String): String {
        log.info(TAG, "Saving ${lines.size} tasks to Dropbox.")
        val contents = join(lines, eol)

        var rev = Config.lastSeenRemoteId
        val toStore = contents.toByteArray(charset("UTF-8"))
        val `in` = ByteArrayInputStream(toStore)
        log.info(TAG, "Saving to file " + path)
        val uploadBuilder = dbxClient.files().uploadBuilder(path)
        uploadBuilder.withAutorename(true).withMode(if (rev != null) WriteMode.update(rev) else null)
        val uploaded = uploadBuilder.uploadAndFinish(`in`)
        rev = uploaded.rev
        val newName = uploaded.pathDisplay

        if (newName != path) {
            // The file was written under another name
            // Usually this means the was a conflict.
            log.info(TAG, "Filename was changed remotely. New name is: " + newName)
            showToastLong(mApp, "Filename was changed remotely. New name is: " + newName)
            mApp.switchTodoFile(newName)
        }
        return rev
    }

    override fun appendTaskToFile(path: String, lines: List<String>, eol: String) {
        if (!isOnline) {
            throw IOException("Device is offline")
        }

        val doneContents = ArrayList<String>()
        val download = dbxClient.files().download(path)
        download.inputStream.bufferedReader().forEachLine {
            doneContents.add(it)
        }
        download.close()
        val rev = download.result.rev

        log.info(TAG, "The file's rev is: " + rev)

        // Then append
        doneContents += lines
        val toStore = (join(doneContents, eol) + eol).toByteArray(charset("UTF-8"))
        val `in` = ByteArrayInputStream(toStore)
        dbxClient.files().uploadBuilder(path).withAutorename(true).withMode(if (rev != null) WriteMode.update(rev) else null).uploadAndFinish(`in`)
    }

    override fun writeFile(file: File, contents: String) {
        if (!isAuthenticated) {
            log.error(TAG, "Not authenticated, file ${file.canonicalPath} not written.")
            return
        }
        val toStore = contents.toByteArray(charset("UTF-8"))
        queue("Write to file ${file.canonicalPath}") {
            val inStream = ByteArrayInputStream(toStore)
            dbxClient.files().uploadBuilder(file.path).withMode(WriteMode.OVERWRITE).uploadAndFinish(`inStream`)
        }
    }

    @Throws(IOException::class)
    override fun readFile(file: String, fileRead: (String) -> Unit) {
        if (!isAuthenticated) {
            return
        }

        val download = dbxClient.files().download(file)
        log.info(TAG, "The file's rev is: " + download.result.rev)

        val reader = BufferedReader(InputStreamReader(download.inputStream, "UTF-8"))
        val readFile = ArrayList<String>()
        reader.forEachLine { line ->
            readFile.add(line)
        }
        download.inputStream.close()
        val contents = join(readFile, "\n")
        fileRead(contents)
    }

    fun changedConnectionState() {
        mApp.localBroadCastManager.sendBroadcast(Intent(Constants.BROADCAST_UPDATE_UI))
        if (isOnline) {
            log.info(TAG, "Device went online")
        } else {
            log.info(TAG, "Device no longer online skipping reloadLuaConfig")
        }
    }

    override fun getDefaultPath(): String {
        return if (Config.fullDropBoxAccess) {
            "/todo/todo.txt"
        } else {
            "/todo.txt"
        }
    }

    override fun loadFileList(path: String, txtOnly: Boolean): List<FileEntry> {

        val fileList = ArrayList<FileEntry>()

        val dbxPath = if (path == ROOT_DIR) "" else path
        val entries = FileStore.dbxClient.files().listFolder(dbxPath).entries
        entries?.forEach { entry ->
            if (entry is FolderMetadata)
                fileList.add(FileEntry(entry.name, isFolder = true))
            else if (!txtOnly || File(entry.name).extension == "txt") {
                fileList.add(FileEntry(entry.name, isFolder = false))
            }
        }
        return fileList
    }
}
