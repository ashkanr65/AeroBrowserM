package com.aero.browser.core.network.doh

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loopback proxy used only by this app's WebView instances. WebView is pointed at it through
 * `ProxyController`, so every hostname the browser needs is resolved by [resolver] (DNS over
 * HTTPS) while other apps and the system resolver stay untouched.
 *
 * HTTPS is forwarded as an opaque CONNECT tunnel: TLS still terminates in the browser, so the
 * proxy never sees plaintext and does not need to trust or forge certificates.
 */
class BrowserDnsProxy(
    private val resolver: HostResolver,
    private val connectTimeoutMillis: Int = 15_000,
    private val headerTimeoutMillis: Int = 20_000,
    private val logger: (String) -> Unit = {},
) : Closeable {

    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "aero-doh-proxy").apply { isDaemon = true }
    }

    @Volatile
    var port: Int = 0
        private set

    fun start() {
        check(running.compareAndSet(false, true)) { "proxy already started" }
        val socket = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        socket.reuseAddress = true
        server = socket
        port = socket.localPort
        Thread({ acceptLoop(socket) }, "aero-doh-proxy-accept").apply {
            isDaemon = true
            start()
        }
    }

    override fun close() {
        running.set(false)
        runCatching { server?.close() }
        server = null
        workers.shutdownNow()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get() && !socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (failure: IOException) {
                if (running.get()) logger("accept failed: ${failure.message}")
                return
            }
            runCatching { workers.execute { serve(client) } }
                .onFailure { runCatching { client.close() } }
        }
    }

    private fun serve(client: Socket) {
        try {
            client.use { socket ->
                socket.soTimeout = headerTimeoutMillis
                val input = BufferedInputStream(socket.getInputStream())
                val head = readHeaderBlock(input)
                if (head == null) return
                when (val request = ProxyRequestParser.parse(head)) {
                    is ConnectTarget -> tunnel(socket, input, request)
                    is ForwardRequest -> forward(socket, input, request)
                    null -> writeStatus(socket, "400 Bad Request")
                }
            }
        } catch (failure: IOException) {
            logger("connection closed: ${failure.message}")
        } catch (failure: RuntimeException) {
            logger("proxy error: ${failure.message}")
        }
    }

    private fun tunnel(client: Socket, clientInput: InputStream, target: ConnectTarget) {
        val remote = connect(target) ?: return writeStatus(client, "502 Bad Gateway")
        remote.use {
            client.getOutputStream().apply {
                write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                flush()
            }
            client.soTimeout = 0
            it.soTimeout = 0
            workers.execute {
                pump(clientInput, it.getOutputStream()) { runCatching { it.shutdownOutput() } }
            }
            pump(it.getInputStream(), client.getOutputStream()) {
                runCatching { client.shutdownOutput() }
            }
        }
    }

    private fun forward(client: Socket, clientInput: InputStream, request: ForwardRequest) {
        val remote = connect(request) ?: return writeStatus(client, "502 Bad Gateway")
        remote.use {
            val head = StringBuilder()
                .append(request.method).append(' ').append(request.path).append(' ').append(request.version).append("\r\n")
            if (request.headers.none { (name, _) -> name.equals("Host", ignoreCase = true) }) {
                head.append("Host: ").append(hostHeader(request.host, request.port)).append("\r\n")
            }
            request.headers.forEach { (name, value) -> head.append(name).append(": ").append(value).append("\r\n") }
            head.append("Connection: close\r\n\r\n")
            val remoteOut = it.getOutputStream()
            remoteOut.write(head.toString().toByteArray(Charsets.ISO_8859_1))
            remoteOut.flush()
            workers.execute {
                pump(clientInput, remoteOut) { runCatching { it.shutdownOutput() } }
            }
            pump(it.getInputStream(), client.getOutputStream()) {
                runCatching { client.shutdownOutput() }
            }
        }
    }

    private fun connect(target: ProxyTarget): Socket? {
        val addresses = try {
            resolver.resolve(target.host)
        } catch (failure: IOException) {
            logger("resolve failed for ${target.host}: ${failure.message}")
            return null
        } catch (failure: RuntimeException) {
            logger("resolve failed for ${target.host}: ${failure.message}")
            return null
        }
        for (address in addresses) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(address, target.port), connectTimeoutMillis)
                socket.soTimeout = 0
                return socket
            } catch (failure: IOException) {
                logger("connect failed ${target.host} -> $address: ${failure.message}")
                runCatching { socket.close() }
            }
        }
        return null
    }

    /** Relays bytes until EOF; [onEof] lets the caller half-close the opposite side. */
    private fun pump(source: InputStream, sink: OutputStream, onEof: () -> Unit = {}) {
        val buffer = ByteArray(16 * 1024)
        try {
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
                sink.flush()
            }
        } catch (_: IOException) {
            // Either side closing the tunnel is normal.
        }
        runCatching(onEof)
    }

    private fun writeStatus(socket: Socket, status: String) {
        runCatching {
            socket.getOutputStream().apply {
                write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                flush()
            }
        }
    }

    private fun readHeaderBlock(input: InputStream): String? {
        val buffer = ByteArrayOutputStream(2048)
        val tail = IntArray(4) { -1 }
        while (buffer.size() < ProxyRequestParser.MAX_HEADER_BYTES) {
            val next = input.read()
            if (next < 0) return null
            buffer.write(next)
            tail[0] = tail[1]
            tail[1] = tail[2]
            tail[2] = tail[3]
            tail[3] = next
            if (tail[0] == '\r'.code && tail[1] == '\n'.code && tail[2] == '\r'.code && tail[3] == '\n'.code) {
                return buffer.toString(Charsets.ISO_8859_1.name())
            }
        }
        return null
    }

    private fun hostHeader(host: String, port: Int): String {
        val formatted = if (host.contains(':')) "[$host]" else host
        return if (port == 80) formatted else "$formatted:$port"
    }
}
