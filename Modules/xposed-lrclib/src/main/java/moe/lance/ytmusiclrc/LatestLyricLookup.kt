package moe.lance.ytmusiclrc

import java.net.HttpURLConnection
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Only the latest song is allowed to keep doing network work. */
internal class LatestLyricLookup(name: String) {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, name).apply { isDaemon = true } }
    private var future: Future<*>? = null
    private var request: Request? = null

    @Synchronized
    fun cancel() {
        request?.cancel()
        future?.cancel(true)
        request = null
        future = null
    }

    @Synchronized
    fun execute(action: () -> Unit) {
        cancel()
        val next = Request()
        request = next
        future = executor.submit {
            current.set(next)
            try {
                checkCancelled()
                action()
            } finally {
                current.remove()
            }
        }
    }

    private class Request {
        @Volatile var cancelled = false
        private var connection: HttpURLConnection? = null

        @Synchronized fun attach(value: HttpURLConnection) {
            if (cancelled) throw CancellationException()
            connection = value
        }

        @Synchronized fun detach() { connection = null }

        @Synchronized fun cancel() {
            cancelled = true
            connection?.disconnect()
            connection = null
        }
    }

    companion object {
        private val current = ThreadLocal<Request>()

        fun checkCancelled() {
            if (Thread.currentThread().isInterrupted || current.get()?.cancelled == true) {
                throw CancellationException("Superseded lyric lookup")
            }
        }

        fun attach(connection: HttpURLConnection) {
            checkCancelled()
            current.get()?.attach(connection)
        }

        fun detach() { current.get()?.detach() }
    }
}
