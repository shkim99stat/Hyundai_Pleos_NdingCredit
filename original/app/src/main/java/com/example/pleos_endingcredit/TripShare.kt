package com.example.pleos_endingcredit

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** The QR contains the summary itself, so the demo needs no sharing server. */
internal object TripShare {
    fun summary(trip: Trip, record: TripRecording? = null): String = buildString {
        val content = TripResultContent(trip, record)
        appendLine(trip.name)
        appendLine(trip.createdOn)
        appendLine("${content.distance} km · ${content.duration}")
        appendLine("함께한 인원 ${trip.people}명")
        appendLine("나누는 인원 ${content.splitPeople}명")
        appendLine(content.total?.let { "총 이동비 ${TripSample.won(it)} · 1인 ${TripSample.won(requireNotNull(content.perPerson(content.splitPeople)))}" }
            ?: "이동비 기록 없음")
        append(content.places.joinToString(" · ") { it.name })
    }

    fun qr(trip: Trip, record: TripRecording? = null): Bitmap = qrText(summary(trip, record))

    fun qrText(full: String): Bitmap {
        val size = 640
        // Bound UTF-8 payload for long Korean place lists without cutting a surrogate pair.
        val payload = if (full.length > 600) full.take(600).trimEnd { Character.isHighSurrogate(it) } + "…" else full
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        val pixels = IntArray(size * size) { index ->
            if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
