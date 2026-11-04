package dev.exynoslm.hf

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class HuggingFaceClientTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()
    private fun client() = HuggingFaceClient(http, server.url("").toString().trimEnd('/'))

    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() = server.shutdown()

    @Test fun searchParsesAndPaginates() = runTest {
        server.enqueue(MockResponse().setBody("""[{"id":"litert-community/Gemma3-1B-IT","pipeline_tag":"text-generation","downloads":10,"likes":2,"tags":["license:gemma"],"siblings":[{"rfilename":"gemma3-1b-it-int4.task"}]}]""")
            .addHeader("Link", "<${server.url("/api/models?cursor=abc")}>; rel=\"next\""))
        val page = client().search(SearchQuery(search = "gemma", pipelineTag = "text-generation"))
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("search=gemma")); assertTrue(req.path!!.contains("pipeline_tag=text-generation"))
        assertEquals("litert-community/Gemma3-1B-IT", page.models.single().repoId)
        assertEquals("gemma", page.models.single().license)
        assertTrue(page.nextUrl!!.contains("cursor=abc"))
    }

    @Test fun retriesOn429ThenSucceeds() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setBody("[]"))
        assertTrue(client().search(SearchQuery()).models.isEmpty())
    }

    @Test(expected = HubException.GatedOrUnauthorized::class)
    fun gatedRepo() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        client().model("google/gemma-3-1b-it")
    }

    @Test fun modelDetailHasSizesAndSha() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"a/b","siblings":[{"rfilename":"m.gguf","size":10,"lfs":{"sha256":"ff","size":10}}]}"""))
        val f = client().model("a/b").files.single()
        assertEquals(10L, f.sizeBytes); assertEquals("ff", f.sha256)
    }

    @Test fun downloadResumesAndVerifies() = runTest {
        val data = ByteArray(3000) { (it % 251).toByte() }
        val sha = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        val dest = File(tmp.root, "m.task")
        File(dest.path + ".part").writeBytes(data.copyOfRange(0, 1000)) // simulate interrupted download
        server.enqueue(MockResponse().setResponseCode(206).setBody(Buffer().write(data.copyOfRange(1000, 3000))))
        val states = ModelDownloader(http).download(server.url("/f").toString(), dest, sha).toList()
        assertEquals("bytes=1000-", server.takeRequest().getHeader("Range"))
        assertTrue(states.last() is DownloadState.Done)
        assertArrayEquals(data, dest.readBytes())
    }

    @Test(expected = ChecksumMismatch::class)
    fun downloadChecksumMismatch() = runTest {
        server.enqueue(MockResponse().setBody("abc"))
        ModelDownloader(http).download(server.url("/f").toString(), File(tmp.root, "x"), "00".repeat(32)).toList()
    }
}
