package dev.exynoslm.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Radii taken from the ported Lucian elements (13 / 14 / 16 / 22 / 30 dp, pill = 999). */
val LxMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

object LxShapes {
    val Pill = RoundedCornerShape(percent = 50)
    val Circle = CircleShape
    val Button = RoundedCornerShape(14.dp)
    val IconButton = RoundedCornerShape(13.dp)
    val Tile = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(22.dp)
    val Field = RoundedCornerShape(16.dp)
    val Tag = RoundedCornerShape(6.dp)

    /** #086 / #091 chat bubbles: 18 18 6 18 (user, tail bottom-end) and 18 18 18 6 (other, tail bottom-start). */
    val BubbleUser = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp)
    val BubbleOther = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp)
}
