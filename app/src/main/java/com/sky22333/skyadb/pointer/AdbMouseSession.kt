package com.sky22333.skyadb.pointer

import android.content.Context
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.stream.AdbStream
import com.sky22333.skyadb.scrcpy.ScrcpyConstants
import com.sky22333.skyadb.scrcpy.ScrcpyServerManager
import java.io.Closeable
import java.io.EOFException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Dedicated control-only scrcpy session: no Surface, video decoder, or per-motion Shell process. */
class AdbMouseSession private constructor(
    private val server: AdbStream,
    private val control: AdbStream,
) : Closeable {
    private val closed = AtomicBoolean()
    private val writeLock = ReentrantLock()

    fun send(packet: ByteArray) = writeLock.withLock {
        check(!closed.get())
        control.sink.write(packet)
        control.sink.flush()
    }

    fun readLog(): String? = server.source.readUtf8Line()
    fun readControl(): Byte = control.source.readByte()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // Destroying the virtual device releases any held mouse button on the target.
        // Never wait behind a stalled writer: closing the channel also tears down UHID remotely.
        if (writeLock.tryLock()) {
            try {
                runCatching {
                    control.sink.write(MouseProtocol.destroy())
                    control.sink.flush()
                }
            } finally {
                writeLock.unlock()
            }
        }
        runCatching { control.close() }
        runCatching { server.close() }
    }

    companion object {
        suspend fun open(context: Context, kadb: Kadb): AdbMouseSession {
            ScrcpyServerManager(context).pushServer(kadb)
            val scid = ScrcpyConstants.formatScid((Random.nextInt() and 0x7fffffff).toUInt())
            val command = "CLASSPATH=${ScrcpyConstants.RemoteServerPath} app_process / " +
                "com.genymobile.scrcpy.Server ${ScrcpyConstants.ServerVersion} scid=$scid " +
                "video=false audio=false control=true tunnel_forward=true " +
                "send_device_meta=false clipboard_autosync=false cleanup=false"
            val server = kadb.open("shell:$command 2>&1")
            var control: AdbStream? = null
            try {
                var last: Throwable? = null
                for (attempt in 0 until ScrcpyConstants.ConnectRetryCount) {
                    currentCoroutineContext().ensureActive()
                    try {
                        control = kadb.open("localabstract:scrcpy_$scid")
                        if (control.source.readByte().toInt() != 0) throw EOFException("Invalid scrcpy handshake")
                        break
                    } catch (e: Exception) {
                        runCatching { control?.close() }
                        control = null
                        if (e is CancellationException) throw e
                        last = e
                        delay(ScrcpyConstants.ConnectRetryDelayMillis)
                    }
                }
                val stream = control ?: throw IllegalStateException("scrcpy control socket unavailable", last)
                currentCoroutineContext().ensureActive()
                return AdbMouseSession(server, stream).also { it.send(MouseProtocol.create()) }
            } catch (e: Throwable) {
                runCatching { control?.close() }
                runCatching { server.close() }
                throw e
            }
        }
    }
}
