package dev.exynoslm.runtime

import dev.exynoslm.model.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FormatRouterEngineTest {
    private val device = DeviceState(availableRamBytes = 8L shl 30, totalRamBytes = 12L shl 30)
    private val config = InferenceConfig()

    private fun model(format: ModelFormat, path: String) =
        LoadedModel("m-$path", path, "/models/$path", format, Quantization.INT4, 500L shl 20)

    private val task = model(ModelFormat.MEDIAPIPE_TASK, "gemma.task")
    private val litert = model(ModelFormat.LITERT_LM, "gemma.litertlm")
    private val gguf = model(ModelFormat.GGUF, "qwen-q4_k_m.gguf")

    @Test fun routesByFormat() = runTest {
        val mediapipe = FakeEngine(Backend.CPU, formats = setOf(ModelFormat.MEDIAPIPE_TASK))
        val llama = FakeEngine(Backend.CPU, formats = setOf(ModelFormat.GGUF))
        val router = FormatRouterEngine(Backend.CPU, listOf(mediapipe, llama))

        assertEquals(LoadResult.Success(0, Backend.CPU), router.load(gguf, config))
        assertEquals(gguf, llama.loaded)
        assertNull(mediapipe.loaded)
    }

    @Test fun unloadsPreviousDelegateOnSwitch() = runTest {
        val mediapipe = FakeEngine(Backend.GPU, formats = setOf(ModelFormat.MEDIAPIPE_TASK))
        val llama = FakeEngine(Backend.GPU, formats = setOf(ModelFormat.GGUF))
        val router = FormatRouterEngine(Backend.GPU, listOf(mediapipe, llama))

        router.load(task, config)
        assertEquals(task, mediapipe.loaded)
        assertEquals(0, mediapipe.unloadCount)

        router.load(gguf, config)
        assertEquals(1, mediapipe.unloadCount)
        assertNull(mediapipe.loaded)
        assertEquals(gguf, llama.loaded)
        assertEquals(0, llama.unloadCount)
    }

    @Test fun reloadingSameDelegateDoesNotUnloadIt() = runTest {
        val mediapipe = FakeEngine(Backend.CPU, formats = setOf(ModelFormat.MEDIAPIPE_TASK))
        val router = FormatRouterEngine(Backend.CPU, listOf(mediapipe))

        router.load(task, config)
        router.load(model(ModelFormat.MEDIAPIPE_TASK, "other.task"), config)
        assertEquals(0, mediapipe.unloadCount)
    }

    @Test fun skipsUnavailableDelegate() = runTest {
        val broken = FakeEngine(Backend.CPU, available = false)
        val working = FakeEngine(Backend.CPU)
        val router = FormatRouterEngine(Backend.CPU, listOf(broken, working))

        assertEquals(LoadResult.Success(0, Backend.CPU), router.load(task, config))
        assertNull(broken.loaded)
        assertEquals(task, working.loaded)
    }

    @Test fun failsCleanlyForUnknownFormat() = runTest {
        val mediapipe = FakeEngine(Backend.CPU, formats = setOf(ModelFormat.MEDIAPIPE_TASK))
        val router = FormatRouterEngine(Backend.CPU, listOf(mediapipe))

        val result = router.load(model(ModelFormat.TFLITE, "x.tflite"), config)
        assertEquals(LoadResult.Failure("no engine for TFLITE on CPU"), result)
        assertNull(mediapipe.loaded)
        val events = router.generate(GenerationRequest(listOf(ChatMessage(Role.USER, "hi")))).toList()
        assertEquals(listOf<GenerationEvent>(GenerationEvent.Error("model not loaded")), events)
    }

    @Test fun failedLoadLeavesNothingActive() = runTest {
        val mediapipe = FakeEngine(Backend.CPU, formats = setOf(ModelFormat.MEDIAPIPE_TASK))
        val llama = FakeEngine(Backend.CPU, formats = setOf(ModelFormat.GGUF), failLoad = true)
        val router = FormatRouterEngine(Backend.CPU, listOf(mediapipe, llama))

        router.load(task, config)
        assertTrue(router.load(gguf, config) is LoadResult.Failure)
        assertEquals(1, mediapipe.unloadCount)
        val events = router.generate(GenerationRequest(listOf(ChatMessage(Role.USER, "hi")))).toList()
        assertEquals(listOf<GenerationEvent>(GenerationEvent.Error("model not loaded")), events)
    }

    @Test fun generateDelegatesToActiveEngine() = runTest {
        val mediapipe = FakeEngine(Backend.CPU, formats = setOf(ModelFormat.MEDIAPIPE_TASK))
        val router = FormatRouterEngine(Backend.CPU, listOf(mediapipe))
        router.load(task, config)

        val done = router.generate(GenerationRequest(listOf(ChatMessage(Role.USER, "hi there"))))
            .filterIsInstance<GenerationEvent.Completed>().first()
        assertEquals("Echo: hi there", done.fullText)
    }

    @Test fun unloadClearsActiveDelegate() = runTest {
        val mediapipe = FakeEngine(Backend.CPU, formats = setOf(ModelFormat.MEDIAPIPE_TASK))
        val router = FormatRouterEngine(Backend.CPU, listOf(mediapipe))
        router.load(task, config)

        router.unload()
        assertEquals(1, mediapipe.unloadCount)
        val events = router.generate(GenerationRequest(listOf(ChatMessage(Role.USER, "hi")))).toList()
        assertEquals(listOf<GenerationEvent>(GenerationEvent.Error("model not loaded")), events)
        // Unloading again with nothing active is a no-op.
        router.unload()
        assertEquals(1, mediapipe.unloadCount)
    }

    @Test fun capabilitiesUnionAvailableDelegates() {
        val router = FormatRouterEngine(Backend.GPU, listOf(
            FakeEngine(Backend.GPU, formats = setOf(ModelFormat.MEDIAPIPE_TASK)),
            FakeEngine(Backend.GPU, formats = setOf(ModelFormat.GGUF)),
            FakeEngine(Backend.GPU, available = false, formats = setOf(ModelFormat.LITERT_LM)),
        ))
        val caps = router.capabilities()
        assertTrue(caps.available)
        assertEquals(setOf(ModelFormat.MEDIAPIPE_TASK, ModelFormat.GGUF), caps.supportedFormats)
        assertFalse(caps.supportsSeed) // FakeEngine does not support seeds, so the OR over delegates is false
    }

    /** Engine with explicit quantization set; an empty set means "accepts any quantization". */
    private fun quantEngine(quants: Set<Quantization>, format: ModelFormat) = object : InferenceEngine {
        override val backend = Backend.CPU
        override suspend fun load(model: LoadedModel, config: InferenceConfig): LoadResult = LoadResult.Failure("n/a")
        override fun generate(request: GenerationRequest) = kotlinx.coroutines.flow.emptyFlow<GenerationEvent>()
        override suspend fun unload() {}
        override fun capabilities() = BackendCapabilities(
            backend = Backend.CPU, available = true,
            supportedFormats = setOf(format),
            supportedQuantizations = quants,
        )
    }

    @Test fun capabilitiesEmptyQuantizationMeansAny() {
        val restricted = quantEngine(setOf(Quantization.Q4_K_M), ModelFormat.GGUF)
        val anyQuant = quantEngine(emptySet(), ModelFormat.MEDIAPIPE_TASK)
        assertTrue(FormatRouterEngine(Backend.CPU, listOf(restricted, anyQuant)).capabilities().supportedQuantizations.isEmpty())
        assertEquals(setOf(Quantization.Q4_K_M), FormatRouterEngine(Backend.CPU, listOf(restricted)).capabilities().supportedQuantizations)
    }

    @Test fun quantizationUnionAcrossRestrictedDelegates() {
        val a = quantEngine(setOf(Quantization.Q4_K_M), ModelFormat.GGUF)
        val b = quantEngine(setOf(Quantization.INT4), ModelFormat.MEDIAPIPE_TASK)
        assertEquals(setOf(Quantization.Q4_K_M, Quantization.INT4),
            FormatRouterEngine(Backend.CPU, listOf(a, b)).capabilities().supportedQuantizations)
    }

    @Test fun capabilitiesUnavailableWhenNoDelegateAvailable() {
        val router = FormatRouterEngine(Backend.NPU_ENN, listOf(
            FakeEngine(Backend.NPU_ENN, available = false),
        ))
        val caps = router.capabilities()
        assertFalse(caps.available)
        assertEquals("fake: unavailable", caps.unavailableReason)
        assertTrue(FormatRouterEngine(Backend.NPU_ENN, emptyList()).capabilities().unavailableReason!!.isNotEmpty())
    }

    @Test fun selectorAndRouterIntegration() = runTest {
        val cpuRouter = FormatRouterEngine(Backend.CPU, listOf(
            FakeEngine(Backend.CPU, formats = setOf(ModelFormat.MEDIAPIPE_TASK)),
            FakeEngine(Backend.CPU, formats = setOf(ModelFormat.GGUF)),
        ))
        val selector = BackendSelector(mapOf(Backend.CPU to cpuRouter))

        // Format the router serves: selected and loaded.
        val (sel, res) = selector.selectAndLoad(gguf, BackendPreference.AUTO, device, config)
        assertTrue(res is LoadResult.Success)
        assertEquals(Backend.CPU, sel.chosen)

        // Format no delegate serves: rejected by the compatibility check, never reaches load().
        val unsupported = model(ModelFormat.TFLITE, "x.tflite")
        assertNull(selector.select(unsupported, BackendPreference.AUTO, device).chosen)
    }

    @Test fun selectorFallsBackWhenRouterCannotLoadFormat() = runTest {
        // GPU router advertises .task via a delegate that fails at load time; CPU router serves it.
        val gpu = FormatRouterEngine(Backend.GPU, listOf(FakeEngine(Backend.GPU, failLoad = true, formats = setOf(ModelFormat.MEDIAPIPE_TASK))))
        val cpu = FormatRouterEngine(Backend.CPU, listOf(FakeEngine(Backend.CPU, formats = setOf(ModelFormat.MEDIAPIPE_TASK))))
        val (sel, res) = BackendSelector(mapOf(Backend.GPU to gpu, Backend.CPU to cpu))
            .selectAndLoad(task, BackendPreference.GPU, device, config)
        assertTrue(res is LoadResult.Success)
        assertEquals(Backend.CPU, sel.chosen)
        assertTrue(sel.fallbackNotes.any { "load failed" in it })
    }
}
