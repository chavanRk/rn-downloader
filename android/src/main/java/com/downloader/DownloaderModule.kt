package com.downloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

// ─── Per-download state ───────────────────────────────────────────────────────

private data class DownloadState(
  val url: String,
  val fileName: String,
  val isBackground: Boolean,
  @Volatile var paused: Boolean = false,
  @Volatile var cancelled: Boolean = false,
  /** Bytes already written (for Range resume) */
  @Volatile var bytesDownloaded: Long = 0L
)

class DownloaderModule(private val reactContext: ReactApplicationContext) :
  NativeDownloaderSpec(reactContext) {

  // downloadId → state
  private val activeDownloads = ConcurrentHashMap<String, DownloadState>()
  // downloadId → background DownloadManager ID
  private val bgDownloadIds = ConcurrentHashMap<String, Long>()

  // ─── Helpers ───────────────────────────────────────────────────────────────

  private fun emit(event: String, map: com.facebook.react.bridge.WritableMap) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(event, map)
  }

  private fun resolveFileName(url: String, hint: String?): String {
    if (!hint.isNullOrBlank()) return hint
    var name = url.substringAfterLast("/")
    if (name.contains("?")) name = name.substringBefore("?")
    return name.ifBlank { "downloaded_file" }
  }

  // ─── download ──────────────────────────────────────────────────────────────

  override fun download(options: ReadableMap, promise: Promise) {
    val urlString = options.getString("url")
    if (urlString.isNullOrBlank()) {
      promise.resolve(Arguments.createMap().apply {
        putBoolean("success", false); putString("error", "URL is missing")
      })
      return
    }

    val isBackground = options.takeIf { it.hasKey("background") }?.getBoolean("background") ?: false
    val rawFileName = if (options.hasKey("fileName")) options.getString("fileName") else null
    val fileName = resolveFileName(urlString, rawFileName)
    val downloadId = UUID.randomUUID().toString()

    val state = DownloadState(url = urlString, fileName = fileName, isBackground = isBackground)
    activeDownloads[downloadId] = state

    if (isBackground) {
      // Use system DownloadManager — survives process death
      val dm = reactContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val request = DownloadManager.Request(Uri.parse(urlString)).apply {
        setTitle(fileName)
        setDescription("rn-downloader-id:$downloadId")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        setDestinationUri(Uri.fromFile(dest))
      }
      val bgId = dm.enqueue(request)
      bgDownloadIds[downloadId] = bgId
      activeDownloads.remove(downloadId)

      promise.resolve(Arguments.createMap().apply {
        putBoolean("success", true)
        putString("downloadId", downloadId)
      })
      return
    }

    // Foreground download
    promise.resolve(Arguments.createMap().apply {
      putBoolean("success", true)
      putString("downloadId", downloadId)
    })

    thread {
      try {
        var resumeFrom = state.bytesDownloaded
        val destFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        if (resumeFrom > 0) {
          connection.setRequestProperty("Range", "bytes=$resumeFrom-")
        }
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
          activeDownloads.remove(downloadId)
          val err = Arguments.createMap().apply {
            putBoolean("success", false)
            putString("downloadId", downloadId)
            putString("error", "SERVER_ERROR: $responseCode")
          }
          emit("onDownloadError", err)
          return@thread
        }

        val contentLength = connection.contentLength
        val totalExpected = if (resumeFrom > 0) resumeFrom + contentLength else contentLength.toLong()

        val input = connection.inputStream
        val output: FileOutputStream = if (resumeFrom > 0) {
          FileOutputStream(destFile, true) // append
        } else {
          FileOutputStream(destFile, false)
        }

        val buffer = ByteArray(8192)
        var total = resumeFrom
        var count: Int
        var lastProgress = -1

        while (input.read(buffer).also { count = it } != -1) {
          // Pause — busy-wait until resumed or cancelled
          while (state.paused && !state.cancelled) {
            state.bytesDownloaded = total
            Thread.sleep(200)
          }
          if (state.cancelled) {
            output.close(); input.close()
            destFile.delete()
            activeDownloads.remove(downloadId)
            return@thread
          }

          output.write(buffer, 0, count)
          total += count

          if (totalExpected > 0) {
            val progress = (total * 100 / totalExpected).toInt()
            if (progress > lastProgress) {
              lastProgress = progress
              val evt = Arguments.createMap().apply {
                putString("url", urlString)
                putString("downloadId", downloadId)
                putInt("progress", progress)
              }
              emit("onDownloadProgress", evt)
            }
          }
        }

        output.flush(); output.close(); input.close()
        activeDownloads.remove(downloadId)

        val result = Arguments.createMap().apply {
          putBoolean("success", true)
          putString("downloadId", downloadId)
          putString("filePath", destFile.absolutePath)
        }
        emit("onDownloadComplete", result)

      } catch (e: Exception) {
        activeDownloads.remove(downloadId)
        val err = Arguments.createMap().apply {
          putBoolean("success", false)
          putString("downloadId", downloadId)
          putString("error", e.message ?: "NETWORK_ERROR")
        }
        emit("onDownloadError", err)
      }
    }
  }

  // ─── pauseDownload ─────────────────────────────────────────────────────────

  override fun pauseDownload(downloadId: String, promise: Promise) {
    val state = activeDownloads[downloadId]
    if (state == null) {
      promise.resolve(Arguments.createMap().apply {
        putBoolean("success", false); putString("error", "Download not found")
      })
      return
    }
    state.paused = true
    promise.resolve(Arguments.createMap().apply { putBoolean("success", true) })
  }

  // ─── resumeDownload ────────────────────────────────────────────────────────

  override fun resumeDownload(downloadId: String, promise: Promise) {
    val state = activeDownloads[downloadId]
    if (state == null) {
      promise.resolve(Arguments.createMap().apply {
        putBoolean("success", false); putString("error", "Download not found or already finished")
      })
      return
    }
    state.paused = false
    promise.resolve(Arguments.createMap().apply { putBoolean("success", true) })
  }

  // ─── cancelDownload ────────────────────────────────────────────────────────

  override fun cancelDownload(downloadId: String, promise: Promise) {
    val state = activeDownloads[downloadId]
    if (state != null) {
      state.cancelled = true
      activeDownloads.remove(downloadId)
    }
    // Also cancel background DownloadManager downloads
    val bgId = bgDownloadIds.remove(downloadId)
    if (bgId != null) {
      val dm = reactContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      dm.remove(bgId)
    }
    promise.resolve(Arguments.createMap().apply { putBoolean("success", true) })
  }

  // ─── getCachedFiles ────────────────────────────────────────────────────────

  override fun getCachedFiles(promise: Promise) {
    try {
      val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
      val files = downloadsDir.listFiles() ?: emptyArray()
      val list = Arguments.createArray()
      for (f in files) {
        if (!f.isFile) continue
        list.pushMap(Arguments.createMap().apply {
          putString("fileName", f.name)
          putString("filePath", f.absolutePath)
          putDouble("size", f.length().toDouble())
          putDouble("modifiedAt", f.lastModified().toDouble())
        })
      }
      promise.resolve(Arguments.createMap().apply {
        putBoolean("success", true)
        putArray("files", list)
      })
    } catch (e: Exception) {
      promise.resolve(Arguments.createMap().apply {
        putBoolean("success", false); putString("error", e.message ?: "ERROR")
      })
    }
  }

  // ─── deleteFile ────────────────────────────────────────────────────────────

  override fun deleteFile(filePath: String, promise: Promise) {
    val file = File(filePath)
    val deleted = file.delete()
    promise.resolve(Arguments.createMap().apply {
      putBoolean("success", deleted)
      if (!deleted) putString("error", "File not found or could not be deleted")
    })
  }

  // ─── clearCache ────────────────────────────────────────────────────────────

  override fun clearCache(promise: Promise) {
    try {
      val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
      downloadsDir.listFiles()?.forEach { it.delete() }
      promise.resolve(Arguments.createMap().apply { putBoolean("success", true) })
    } catch (e: Exception) {
      promise.resolve(Arguments.createMap().apply {
        putBoolean("success", false); putString("error", e.message ?: "ERROR")
      })
    }
  }

  // ─── getBackgroundDownloads ──────────────────────────────────────────────────

  override fun getBackgroundDownloads(promise: Promise) {
    try {
      val dm = reactContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val query = DownloadManager.Query()
      // We check for all states that could still be in progress or completed but not yet handled
      val cursor = dm.query(query)
      val results = Arguments.createArray()

      if (cursor != null && cursor.moveToFirst()) {
        val descIdx = cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)
        val idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
        val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        val currentIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)

        do {
          val description = cursor.getString(descIdx) ?: ""
          if (description.startsWith("rn-downloader-id:")) {
            val downloadId = description.removePrefix("rn-downloader-id:")
            val status = cursor.getInt(statusIdx)
            val total = cursor.getLong(totalIdx)
            val current = cursor.getLong(currentIdx)
            val progress = if (total > 0) (current * 100 / total).toInt() else 0

            results.pushMap(Arguments.createMap().apply {
              putString("downloadId", downloadId)
              putString("url", cursor.getString(uriIdx))
              putInt("status", status)
              putInt("progress", progress)
            })
          }
        } while (cursor.moveToNext())
        cursor.close()
      }

      promise.resolve(Arguments.createMap().apply {
        putBoolean("success", true)
        putArray("downloads", results)
      })
    } catch (e: Exception) {
      promise.resolve(Arguments.createMap().apply {
        putBoolean("success", false); putString("error", e.message ?: "ERROR")
      })
    }
  }

  companion object {
    const val NAME = NativeDownloaderSpec.NAME
  }
}

