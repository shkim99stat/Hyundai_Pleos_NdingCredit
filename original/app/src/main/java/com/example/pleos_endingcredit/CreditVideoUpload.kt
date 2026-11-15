package com.example.pleos_endingcredit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlin.concurrent.thread

/** A phone can upload one clip at a time while B1 is open on the same network. */
internal class CreditVideoUpload private constructor(
    private val server: ServerSocket,
    private val page: ByteArray,
    private val files: CreditVideoFiles,
    private val receive: (CreditVideoUpload, CreditVideoReceipt) -> Unit,
) : Closeable {
    private val path = "/${UUID.randomUUID().toString().replace("-", "")}"
    val qr: Bitmap = qrFor("http://${server.inetAddress.hostAddress}:${server.localPort}$path")
    private var client: Socket? = null

    init {
        thread(name = "credit-video-upload", isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                synchronized(this) {
                    if (server.isClosed) socket.close() else client = socket
                }
                socket.use { runCatching { serve(it) } }
                synchronized(this) { client = null }
            }
        }
    }

    @Synchronized
    override fun close() {
        runCatching { server.close() }
        runCatching { client?.close() }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 10_000
        val input = BufferedInputStream(socket.getInputStream())
        val request = input.line(2_048)?.split(' ') ?: return
        if (request.size != 3 || request[1] != path) {
            socket.reply("404 Not Found")
            return
        }
        val headers = mutableMapOf<String, String>()
        val headerDeadline = SystemClock.elapsedRealtime() + 10_000
        while (true) {
            if (headers.size >= 48 || SystemClock.elapsedRealtime() > headerDeadline) return
            val line = input.line(2_048) ?: return
            if (line.isEmpty()) break
            val key = line.substringBefore(':').lowercase()
            if (':' !in line || key in headers) return
            headers[key] = line.substringAfter(':').trim()
        }
        when (request[0]) {
            "GET" -> socket.reply("200 OK", page, "text/html; charset=utf-8")
            "POST" -> {
                val size = headers["content-length"]?.toLongOrNull()
                if (size == null || size !in 1..MAX_BYTES || "transfer-encoding" in headers) {
                    socket.reply("413 Content Too Large")
                } else if (headers["content-type"] != "application/octet-stream") {
                    socket.reply("415 Unsupported Media Type")
                } else {
                    upload(socket, input, size)
                }
            }
            else -> socket.reply("405 Method Not Allowed")
        }
    }

    private fun upload(socket: Socket, input: BufferedInputStream, size: Long) {
        var temporary: File? = null
        try {
            val file = files.createUpload()
            temporary = file
            val deadline = SystemClock.elapsedRealtime() + 120_000
            file.outputStream().buffered().use { output ->
                var remaining = size
                val buffer = ByteArray(64 * 1024)
                while (remaining > 0) {
                    if (SystemClock.elapsedRealtime() > deadline) throw IOException("Upload timed out")
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) throw IOException("Incomplete upload")
                    output.write(buffer, 0, count)
                    remaining -= count
                }
            }
            val receipt = files.inspect(file)
            if (receipt == null) {
                socket.reply("422 Unprocessable Content")
            } else if (!server.isClosed) {
                socket.reply("200 OK")
                receive(this, receipt)
                temporary = null
            }
        } finally {
            temporary?.delete()
        }
    }

    private fun BufferedInputStream.line(limit: Int): String? {
        val value = StringBuilder()
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (value.length < limit && SystemClock.elapsedRealtime() <= deadline) {
            val next = read()
            if (next == -1) return null
            if (next == '\n'.code) return value.toString().removeSuffix("\r")
            value.append(next.toChar())
        }
        return null
    }

    private fun Socket.reply(status: String, body: ByteArray = byteArrayOf(), contentType: String = "text/plain") {
        val headers = "HTTP/1.1 $status\r\nContent-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n\r\n"
        getOutputStream().apply {
            write(headers.toByteArray(Charsets.US_ASCII))
            write(body)
            flush()
        }
    }

    companion object {
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val MAX_BYTES = 256L * 1024 * 1024

        fun open(
            context: Context,
            files: CreditVideoFiles,
            receive: (CreditVideoUpload, CreditVideoReceipt) -> Unit,
        ): CreditVideoUpload? = runCatching {
            val addresses = NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
            val address = addresses.firstOrNull { it.isSiteLocalAddress } ?: return null
            val page = context.assets.open("b1/upload.html").use { it.readBytes() }
            val server = ServerSocket()
            try {
                server.bind(InetSocketAddress(address, 0), 4)
                CreditVideoUpload(server, page, files, receive)
            } catch (failure: Exception) {
                server.close()
                throw failure
            }
        }.getOrNull()

        private fun qrFor(url: String): Bitmap {
            val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(EncodeHintType.MARGIN to 4))
            val pixelsPerModule = 8
            val size = matrix.width * pixelsPerModule
            val pixels = IntArray(size * size) {
                if (matrix[it % size / pixelsPerModule, it / size / pixelsPerModule]) Color.rgb(17, 17, 17)
                else Color.WHITE
            }
            return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }
    }
}
