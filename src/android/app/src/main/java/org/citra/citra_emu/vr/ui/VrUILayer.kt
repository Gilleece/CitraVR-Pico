package org.citra.citra_emu.vr.ui

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.Surface
import android.view.View
import android.view.Window
import android.view.WindowManager
import org.citra.citra_emu.utils.Log
import org.citra.citra_emu.vr.VrActivity
import org.citra.citra_emu.vr.utils.VRUtils
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/*
* This class populates an "SwapchainAndroidSurfaceKHR" with the
* contents of a secondary virtual display. It allows for smooth animations,
* but the perf doesn't scale well with texture size and it doesn't support mip
*levels. Therefore, it is important to set the the display size of the texture
*(using resource sizes, display density and the native density constant) to be
*something that's close enough to 1:1 texels:pixels so as to not require mips.
*
* On Pico devices, the runtime fails to composite VirtualDisplay-backed
* surface swapchains (the layer shows the app mirror instead of the
* Presentation's content). For those devices, this class instead inflates the
* layout offscreen and draws it directly into the swapchain surface with a
* Canvas on a fixed interval ("direct rendering" mode), dispatching input
* events to the offscreen view hierarchy.
**/
abstract class VrUILayer(
    val activity: VrActivity,
    private val layoutId: Int,
    densityDpi: Int = DEFAULT_DENSITY.toInt()
) {
    private val requestedDensity: Float = densityDpi.toFloat()
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null

    // Direct rendering (Pico): no VirtualDisplay/Presentation; the view is
    // drawn straight into the swapchain surface.
    private val useDirectRendering = VRUtils.isPicoHmd()
    private var directView: View? = null
    private var directSurface: Surface? = null
    private var directSurfaceWidth = 0
    private var directSurfaceHeight = 0
    private var useSoftwareCanvas = false
    private val directDrawHandler = Handler(Looper.getMainLooper())

    val window: Window?
        get() = presentation?.window

    /** Root of this layer's view hierarchy, valid in both rendering modes
     * once the surface has been set. */
    val contentRoot: View?
        get() = directView ?: presentation?.window?.decorView

    /** Mode-agnostic view lookup. Use this instead of window.findViewById(). */
    fun <T : View> findViewById(id: Int): T? = contentRoot?.findViewById(id)

    /// Called from JNI ////
    fun getBoundsForView(handle: Long): Int {
        val contentView = LayoutInflater.from(activity).inflate(layoutId, null, false)
        if (contentView == null) {
            Log.warning("contentView is null")
            return -1
        }
        // NOTE: this method will only work when the root layout is sized with wrap_content
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        contentView.layout(0, 0, contentView.measuredWidth, contentView.measuredHeight)

        val measuredWidthPx = contentView.width
        val measuredHeightPx = contentView.height

        val displayMetrics = activity.resources.displayMetrics
        val measuredWidthDp = (measuredWidthPx / displayMetrics.density) / (DEFAULT_DENSITY / requestedDensity)
        val measuredHeightDp = measuredHeightPx / displayMetrics.density / (DEFAULT_DENSITY / requestedDensity)

        // Call native method with measured dimensions
        nativeSetBounds(handle, 0, 0, measuredWidthDp.roundToInt(), measuredHeightDp.roundToInt())
        return 0
    }

    fun sendClickToUI(x: Float, y: Float, motionType: Int): Int {
        val action = when (motionType) {
            0 -> MotionEvent.ACTION_UP
            1 -> MotionEvent.ACTION_DOWN
            2 -> MotionEvent.ACTION_MOVE
            else -> MotionEvent.ACTION_HOVER_ENTER
        }
        activity.runOnUiThread { dispatchTouchEvent(x, y, action) }
        return 0
    }

    fun setSurface(
        surface: Surface, widthDp: Int,
        heightDp: Int
    ): Int {
        activity.runOnUiThread { setSurface_(surface, widthDp, heightDp) }
        return 0
    }

    protected open fun onSurfaceCreated() {}

    private fun dispatchTouchEvent(x: Float, y: Float, action: Int) {
        val eventTime = SystemClock.uptimeMillis()

        // Native sends coordinates in swapchain (surface) space. The direct
        // view is laid out at its natural pixel size, so scale accordingly.
        // (In presentation mode the display pixels match the surface 1:1.)
        var targetX = x
        var targetY = y
        val directTarget = directView
        if (directTarget != null && directSurfaceWidth > 0 && directSurfaceHeight > 0) {
            targetX = x * directTarget.width.toFloat() / directSurfaceWidth.toFloat()
            targetY = y * directTarget.height.toFloat() / directSurfaceHeight.toFloat()
        }

        val event = MotionEvent.obtain(
            eventTime, // Use the same timestamp for both downTime and eventTime
            eventTime,
            action,
            1, // Only one pointer is used here
            arrayOf(PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }),
            arrayOf(PointerCoords().apply {
                this.x = targetX
                this.y = targetY
                pressure = 1f
                size = 1f
            }),
            0, 0, // MetaState and buttonState
            1f, 1f, // Precision X and Y
            0, 0, // Device ID and Edge Flags
            InputDevice.SOURCE_TOUCHSCREEN,
            0 // Flags
        )
        try {
            // Dispatch the MotionEvent to the view
            if (directTarget != null) {
                directTarget.dispatchTouchEvent(event)
                synthesizeDirectClick(directTarget, targetX, targetY, action)
            } else {
                presentation?.window?.decorView?.dispatchTouchEvent(event)
            }
        } finally {
            // Ensure the MotionEvent is recycled after use
            event.recycle()
        }
        // Redraw immediately so presses give instant visual feedback.
        if (directTarget != null) { drawDirect() }
    }

    // Offscreen views are never attached to a window, so the click handling
    // View queues with post() on ACTION_UP is never executed (the run queue
    // only drains on window attach). OnTouchListeners still fire synchronously,
    // but OnClickListeners and checkable widgets (RadioButton/ToggleButton)
    // would never trigger. Synthesize the click: when DOWN and UP land on the
    // same clickable view, call performClick() directly.
    private var directClickTarget: View? = null

    private fun synthesizeDirectClick(root: View, x: Float, y: Float, action: Int) {
        when (action) {
            MotionEvent.ACTION_DOWN -> directClickTarget = findClickableViewAt(root, x, y)
            MotionEvent.ACTION_UP -> {
                val target = findClickableViewAt(root, x, y)
                if (target != null && target === directClickTarget) {
                    target.performClick()
                }
                directClickTarget = null
            }
        }
    }

    /** Depth-first hit test for the deepest visible, enabled, clickable view
     * containing the point. Coordinates are in [root]'s local space. */
    private fun findClickableViewAt(root: View, x: Float, y: Float): View? {
        if (root.visibility != View.VISIBLE) { return null }
        if (x < 0 || y < 0 || x >= root.width || y >= root.height) { return null }
        if (root is android.view.ViewGroup) {
            // Iterate in reverse so views drawn on top win the hit test.
            for (i in root.childCount - 1 downTo 0) {
                val child = root.getChildAt(i)
                val childX = x + root.scrollX - child.left - child.translationX
                val childY = y + root.scrollY - child.top - child.translationY
                val found = findClickableViewAt(child, childX, childY)
                if (found != null) { return found }
            }
        }
        return if (root.isClickable && root.isEnabled) root else null
    }

    private fun setSurface_(
        surface: Surface, widthDp: Int,
        heightDp: Int
    ) {
        if (useDirectRendering) {
            setupDirectRendering(surface, widthDp, heightDp)
            onSurfaceCreated()
            return
        }
        // Create a virtual display based on the exact dimensions needed for the view
        val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        virtualDisplay = displayManager.createVirtualDisplay(
            "CitraVR", widthDp, heightDp, requestedDensity.toInt(), surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        )
        presentation = Presentation(activity.applicationContext, virtualDisplay!!.display).apply {
            window?.setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION)
            setContentView(layoutId)
            // Sets the background to transparent. Remove to set background to white
            // (useful for catching overrendering)
            window?.setBackgroundDrawable(ColorDrawable(0))
            show()
        }
        onSurfaceCreated()
    }

    private fun setupDirectRendering(surface: Surface, widthDp: Int, heightDp: Int) {
        Log.info("VrUILayer: using direct surface rendering (Pico)")
        val contentView = LayoutInflater.from(activity).inflate(layoutId, null, false)
        // Lay the view out once at its natural size (same measurement
        // getBoundsForView() used to size the swapchain); drawDirect() scales
        // it to the surface dimensions.
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        contentView.layout(0, 0, contentView.measuredWidth, contentView.measuredHeight)
        directView = contentView
        directSurface = surface
        directSurfaceWidth = widthDp
        directSurfaceHeight = heightDp
        directDrawHandler.post(directDrawRunnable)
    }

    private val directDrawRunnable = object : Runnable {
        override fun run() {
            val surface = directSurface ?: return
            if (!surface.isValid) {
                Log.warning("VrUILayer: direct surface no longer valid, stopping draws")
                return
            }
            drawDirect()
            directDrawHandler.postDelayed(this, DIRECT_DRAW_INTERVAL_MS)
        }
    }

    private fun drawDirect() {
        val surface = directSurface ?: return
        val view = directView ?: return
        if (!surface.isValid || view.width == 0 || view.height == 0) { return }

        // The offscreen view has no ViewRoot, so pending layout requests
        // (text/visibility changes) must be serviced manually. measure() is a
        // cached no-op when no child requested layout. The size is pinned to
        // the original natural size so surface scaling stays constant.
        val width = view.width
        val height = view.height
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)

        val canvas: Canvas = try {
            if (useSoftwareCanvas) surface.lockCanvas(null) else surface.lockHardwareCanvas()
        } catch (e: Exception) {
            if (!useSoftwareCanvas) {
                Log.warning(
                    "VrUILayer: lockHardwareCanvas failed (${e.message}); " +
                        "falling back to software canvas"
                )
                useSoftwareCanvas = true
                try {
                    surface.lockCanvas(null)
                } catch (e2: Exception) {
                    Log.error("VrUILayer: lockCanvas failed: ${e2.message}")
                    return
                }
            } else {
                Log.error("VrUILayer: lockCanvas failed: ${e.message}")
                return
            }
        }
        try {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.scale(
                canvas.width.toFloat() / width.toFloat(),
                canvas.height.toFloat() / height.toFloat()
            )
            view.draw(canvas)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    /*** Debug/Testing  */
    fun writeBitmapToDisk(bmp: Bitmap, outName: String?) {
        val sdCard = activity.externalCacheDir
        if (sdCard != null && outName != null) {
            val file = File(sdCard.absolutePath, outName)
            try {
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (e: Exception) {
                Log.error("Failed to write bitmap to disk: ${e.message}")
            }
        }
    }

    companion object {
        // DPI android uses as "1:1" with dp coordinates.
        // AKA "baseline density"
        private const val DEFAULT_DENSITY = DisplayMetrics.DENSITY_MEDIUM.toFloat()

        // Redraw cadence for direct rendering mode (~30fps).
        private const val DIRECT_DRAW_INTERVAL_MS = 33L

        private external fun nativeSetBounds(
            handle: Long, leftInDp: Int, topInDp: Int,
            rightInDp: Int, bottomInDp: Int
        )

        /*** Debug/Testing  */
        const val DEBUG_WRITE_VIEW_TO_DISK = false
    }
}
