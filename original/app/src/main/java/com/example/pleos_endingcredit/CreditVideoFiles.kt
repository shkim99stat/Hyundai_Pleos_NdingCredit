package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.system.Os
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

internal enum class CreditVideoChoice { PENDING, NONE, UPLOADED }

internal data class CreditVideoReceipt(
    val file: File?,
    val durationMillis: Long,
    val thumbnail: Bitmap? = null,
) {
    val includedSeconds get() = seconds.coerceAtMost(16)
    private val seconds get() = (durationMillis / 1_000).toInt()
    val durationText get() = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

internal class CreditVideoFiles(context: Context) {
    private val directory = File(context.noBackupFilesDir, "credit-videos")

    fun createUpload(): File {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Video directory unavailable")
        return File.createTempFile("upload-", ".video", directory)
    }

    fun inspect(file: File): CreditVideoReceipt? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (duration == null || duration !in 5_000L..30_000L) return null
            val frame = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 320)
                ?: return null
            CreditVideoReceipt(file, duration, frame)
        } finally {
            retriever.release()
        }
    }.getOrNull()

    fun restore(state: Bundle?): CreditVideoReceipt? {
        val duration = state?.getLong("b1.duration") ?: return null
        if (duration !in 5_000L..30_000L) return null
        val name = state.getString("b1.file") ?: return CreditVideoReceipt(null, duration)
        if (!name.matches(Regex("upload-[0-9]+\\.video"))) return null
        val file = File(directory, name).takeIf(File::isFile) ?: return null
        return CreditVideoReceipt(file, duration)
    }

    fun save(state: Bundle, receipt: CreditVideoReceipt?) {
        if (receipt == null) return
        state.putString("b1.file", receipt.file?.name)
        state.putLong("b1.duration", receipt.durationMillis)
    }

    fun pruneDrafts() {
        val expiredAt = System.currentTimeMillis() - 86_400_000
        directory.listFiles()?.filter { it.name.startsWith("upload-") && it.lastModified() < expiredAt }
            ?.forEach { it.delete() }
    }

    fun keep(tripId: String, receipt: CreditVideoReceipt): Boolean = runCatching {
        val file = requireNotNull(receipt.file)
        Os.rename(file.absolutePath, committed(tripId).absolutePath)
    }.isSuccess

    fun committed(tripId: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(tripId.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$key.video")
    }
}
