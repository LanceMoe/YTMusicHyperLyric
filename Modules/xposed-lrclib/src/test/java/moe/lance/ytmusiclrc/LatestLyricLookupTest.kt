package moe.lance.ytmusiclrc

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class LatestLyricLookupTest {
    @Test fun newerSongDisconnectsOldNetworkRequestAndRunsNext() {
        val lookup = LatestLyricLookup("lookup-test")
        val attached = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val stalePublished = AtomicBoolean(false)
        val connection = object : HttpURLConnection(URL("http://localhost/")) {
            override fun connect() = Unit
            override fun usingProxy() = false
            override fun disconnect() { disconnected.countDown() }
        }
        try {
            lookup.execute {
                LatestLyricLookup.attach(connection)
                attached.countDown()
                try { disconnected.await(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { }
                LatestLyricLookup.checkCancelled()
                stalePublished.set(true)
            }
            assertTrue(attached.await(5, TimeUnit.SECONDS))
            lookup.execute { completed.countDown() }
            assertTrue(disconnected.await(5, TimeUnit.SECONDS))
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertFalse(stalePublished.get())
        } finally { lookup.cancel() }
    }
}
