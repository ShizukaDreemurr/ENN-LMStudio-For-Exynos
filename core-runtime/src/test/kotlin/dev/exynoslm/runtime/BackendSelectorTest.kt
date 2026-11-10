package dev.exynoslm.runtime

import dev.exynoslm.model.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackendSelectorTest {
    private val model = LoadedModel("m", "m", "/x.task", ModelFormat.MEDIAPIPE_TASK, Quantization.INT4, 500L shl 20)
    private val device = DeviceState(availableRamBytes = 8L shl 30, totalRamBytes = 12L shl 30)

    @Test fun autoPrefersNpuThenFallsBackToGpu() {
        val sel = BackendSelector(mapOf(
            Backend.NPU_ENN to FakeEngine(Backend.NPU_ENN, available = false),
            Backend.GPU to FakeEngine(Backend.GPU),
            Backend.CPU to FakeEngine(Backend.CPU),
        )).select(model, BackendPreference.AUTO, device)
        assertEquals(Backend.GPU, sel.chosen)
        assertTrue(sel.fallbackNotes.single().startsWith("NPU_ENN"))
    }

    @Test fun forcedCpuIsTriedFirst() {
        val sel = BackendSelector(mapOf(Backend.GPU to FakeEngine(Backend.GPU), Backend.CPU to FakeEngine(Backend.CPU)))
            .select(model, BackendPreference.CPU, device)
        assertEquals(Backend.CPU, sel.chosen)
    }

    @Test fun rejectsWhenRamInsufficient() {
        val sel = BackendSelector(mapOf(Backend.CPU to FakeEngine(Backend.CPU)))
            .select(model, BackendPreference.AUTO, device.copy(availableRamBytes = 100L shl 20))
        assertNull(sel.chosen)
        assertTrue(sel.attempts.last().reasons.any { "RAM" in it })
    }

    @Test fun runtimeLoadFailureFallsBack() = runTest {
        val (sel, res) = BackendSelector(mapOf(
            Backend.GPU to FakeEngine(Backend.GPU, failLoad = true),
            Backend.CPU to FakeEngine(Backend.CPU),
        )).selectAndLoad(model, BackendPreference.GPU, device, InferenceConfig())
        assertTrue(res is LoadResult.Success)
        assertEquals(Backend.CPU, sel.chosen)
        assertTrue(sel.fallbackNotes.any { "load failed" in it })
    }

    @Test fun thermalPrefersCpuOverGpu() {
        val order = BackendSelector(emptyMap()).order(BackendPreference.AUTO, device.copy(thermalSevere = true))
        assertEquals(listOf(Backend.NPU_ENN, Backend.CPU, Backend.GPU), order)
    }

    @Test fun aotNpuFormat() {
        assertEquals(ModelFormat.ENN_COMPILED, ModelFormat.fromFileName("sr_x2.e9965.tflite"))
        assertEquals(ModelFormat.TFLITE, ModelFormat.fromFileName("sr_x2.tflite"))
    }

    @Test fun badges() {
        assertTrue(CompatBadge.TOO_LARGE in Compatibility.badges(ModelFile("big.task", 8L shl 30), ModelTask.TEXT_GENERATION, 12L shl 30))
        assertTrue(CompatBadge.NPU_READY in Compatibility.badges(ModelFile("mobilenet_int8.e9965.tflite", 4L shl 20), ModelTask.IMAGE_CLASSIFICATION, 12L shl 30))
    }

    @Test fun badgesReflectRunnableFormats() {
        val ram = 12L shl 30
        val gguf = Compatibility.badges(ModelFile("a-q4_k_m.gguf", 1L shl 30), ModelTask.TEXT_GENERATION, ram)
        assertEquals(setOf(CompatBadge.CPU, CompatBadge.GPU), gguf)
        assertEquals(setOf(CompatBadge.GPU, CompatBadge.CPU), Compatibility.badges(ModelFile("m.litertlm", 1L shl 30), ModelTask.TEXT_GENERATION, ram))
        assertEquals(setOf(CompatBadge.GPU, CompatBadge.CPU), Compatibility.badges(ModelFile("m.task", 1L shl 30), ModelTask.TEXT_GENERATION, ram))
        assertEquals(setOf(CompatBadge.GPU, CompatBadge.CPU), Compatibility.badges(ModelFile("m.tflite", 1L shl 20), ModelTask.TEXT_GENERATION, ram))
    }

    @Test fun unrunnableFormatsNeedConversion() {
        val ram = 12L shl 30
        for (name in listOf("m.onnx", "m.safetensors", "m.bin")) {
            assertEquals(setOf(CompatBadge.NEEDS_CONVERSION), Compatibility.badges(ModelFile(name, 1L shl 20), ModelTask.TEXT_GENERATION, ram))
        }
    }

    @Test fun customRunnableMapOverridesDefaults() {
        val none = Compatibility.badges(ModelFile("m.task", 1L shl 20), ModelTask.TEXT_GENERATION, 12L shl 30, runnable = emptyMap())
        assertEquals(setOf(CompatBadge.NEEDS_CONVERSION), none)
    }

    @Test fun lruUnloadsEldest() = runTest {
        val a = FakeEngine(); val b = FakeEngine()
        val mgr = EngineManager(maxResident = 1)
        mgr.touch("a", a); mgr.touch("b", b)
        assertEquals(1, a.unloadCount)
        assertEquals(listOf("b"), mgr.residentKeys())
    }

    @Test fun fakeEngineStreams() = runTest {
        val done = FakeEngine().generate(GenerationRequest(listOf(ChatMessage(Role.USER, "hi there"))))
            .filterIsInstance<GenerationEvent.Completed>().first()
        assertEquals("Echo: hi there", done.fullText)
    }
}
