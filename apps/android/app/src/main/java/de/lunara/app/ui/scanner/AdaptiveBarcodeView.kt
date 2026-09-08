package de.lunara.app.ui.scanner

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import com.journeyapps.barcodescanner.BarcodeView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AdaptiveBarcodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BarcodeView(context, attrs, defStyleAttr) {
    override fun calculateFramingRect(container: Rect, surface: Rect): Rect {
        val frame = ScannerFramePolicy.calculate(
            container = container.toBounds(),
            surface = surface.toBounds(),
            density = resources.displayMetrics.density,
            largeScreen = resources.configuration.smallestScreenWidthDp >= 600,
        )
        return Rect(frame.left, frame.top, frame.right, frame.bottom)
    }
}

internal data class FrameBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = max(0, right - left)
    val height: Int get() = max(0, bottom - top)
}

internal object ScannerFramePolicy {
    fun calculate(
        container: FrameBounds,
        surface: FrameBounds,
        density: Float,
        largeScreen: Boolean,
    ): FrameBounds {
        val available = intersection(container, surface) ?: container
        if (available.width == 0 || available.height == 0) {
            return FrameBounds(
                available.left,
                available.top,
                available.left + 1,
                available.top + 1,
            )
        }

        val safeDensity = density.coerceAtLeast(0.1f)
        val shortSide = min(available.width, available.height)
        val shortSideDp = shortSide / safeDensity
        val gutterDp = when {
            shortSideDp < 240f -> 12f
            shortSideDp < 480f -> 20f
            else -> 32f
        }
        val maximumDp = if (largeScreen) 360f else 300f
        val side = min(
            (maximumDp * safeDensity).roundToInt(),
            shortSide - 2 * (gutterDp * safeDensity).roundToInt(),
        ).coerceAtLeast(1)
        val left = available.left + (available.width - side) / 2
        val top = available.top + (available.height - side) / 2
        return FrameBounds(left, top, left + side, top + side)
    }

    private fun intersection(first: FrameBounds, second: FrameBounds): FrameBounds? {
        val left = max(first.left, second.left)
        val top = max(first.top, second.top)
        val right = min(first.right, second.right)
        val bottom = min(first.bottom, second.bottom)
        return if (right > left && bottom > top) {
            FrameBounds(left, top, right, bottom)
        } else {
            null
        }
    }
}

private fun Rect.toBounds() = FrameBounds(left, top, right, bottom)
