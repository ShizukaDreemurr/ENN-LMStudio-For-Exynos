package dev.exynoslm.runtime

import kotlin.math.roundToInt

/**
 * Affine INT8 quantization used by NPU graphs (real = scale * (q - zeroPoint)).
 * Example sr_x2: in scale=1/255, zp=-128 (so 0..1 maps to -128..127); out scale≈0.0033, zp=1.
 */
object Quant {
    fun quantize(x: Float, scale: Float, zeroPoint: Int): Byte =
        ((x / scale).roundToInt() + zeroPoint).coerceIn(-128, 127).toByte()

    fun dequantize(q: Byte, scale: Float, zeroPoint: Int): Float = scale * (q - zeroPoint)

    fun quantize(src: FloatArray, scale: Float, zeroPoint: Int): ByteArray = ByteArray(src.size) { quantize(src[it], scale, zeroPoint) }
    fun dequantize(src: ByteArray, scale: Float, zeroPoint: Int): FloatArray = FloatArray(src.size) { dequantize(src[it], scale, zeroPoint) }

    /** Interleaved HWC (e.g. RGB bitmap bytes) -> planar CHW, which is what sr_x2 expects ([1,3,H,W]). */
    fun hwcToChw(src: ByteArray, h: Int, w: Int, c: Int): ByteArray {
        val out = ByteArray(src.size)
        for (y in 0 until h) for (x in 0 until w) for (k in 0 until c) out[k * h * w + y * w + x] = src[(y * w + x) * c + k]
        return out
    }
}
