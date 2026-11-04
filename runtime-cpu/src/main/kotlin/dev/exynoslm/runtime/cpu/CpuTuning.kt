package dev.exynoslm.runtime.cpu

/**
 * Thread budget for CPU inference. Exynos 2600 has a 10-core 1+3+6 style cluster layout (exact topology: verify on device
 * via /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq). Using only the big/prime cores avoids
 * thrashing little cores and leaves headroom for UI; background agent runs use fewer threads.
 */
object CpuTuning {
    fun bigCoreCount(): Int {
        val freqs = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { i ->
            runCatching { java.io.File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()
        }
        if (freqs.isEmpty()) return (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        val threshold = freqs.max() * 0.75
        return freqs.count { it >= threshold }.coerceAtLeast(1)
    }
    fun threadsFor(background: Boolean): Int = if (background) (bigCoreCount() / 2).coerceAtLeast(1) else bigCoreCount()
}
