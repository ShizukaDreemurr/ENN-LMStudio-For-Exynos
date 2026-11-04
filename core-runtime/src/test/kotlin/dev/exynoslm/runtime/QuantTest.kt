package dev.exynoslm.runtime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class QuantTest {
    private val inScale = 0.003921568859368563f; private val inZp = -128

    @Test fun sr2InputRange() {
        assertEquals(-128, Quant.quantize(0f, inScale, inZp).toInt())
        assertEquals(127, Quant.quantize(1f, inScale, inZp).toInt())
        assertEquals(127, Quant.quantize(5f, inScale, inZp).toInt()) // clamps
    }

    @Test fun roundTrip() {
        val q = Quant.quantize(0.5f, inScale, inZp)
        assertEquals(0.5f, Quant.dequantize(q, inScale, inZp), inScale)
    }

    @Test fun hwcToChw() {
        // 1x2 image, RGB: px0=(1,2,3) px1=(4,5,6) -> R plane [1,4], G [2,5], B [3,6]
        assertArrayEquals(byteArrayOf(1, 4, 2, 5, 3, 6), Quant.hwcToChw(byteArrayOf(1, 2, 3, 4, 5, 6), 1, 2, 3))
    }
}
