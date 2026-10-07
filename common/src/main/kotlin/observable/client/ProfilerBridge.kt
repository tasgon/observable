package observable.client

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.DeltaTracker
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4fc
import java.util.function.BiConsumer
import java.util.function.Consumer

interface WorldRenderer {
    fun render(
        stack: Matrix4fc,
        bufferSource: MultiBufferSource,
        camera: Vec3,
        modelViewMatrix: Matrix4fc,
        delta: DeltaTracker,
        collector: SubmitNodeCollector?,
        cameraState: CameraRenderState,
        flush: Boolean = false,
    )
}

object ProfilerBridge {
    const val MAX_DISTANCE_SQ = 4096.0
    private var renderDepth = 0
    private var screenOpener: Consumer<DeltaTracker>? = null
    private var hudRenderer: BiConsumer<GuiGraphicsExtractor, DeltaTracker>? = null
    private var worldRenderer: WorldRenderer? = null
    private var bridge: ClientBridge? = null
    private val scratchMatrix = Matrix4f()

    val CATEGORY: KeyMapping.Category =
        KeyMapping.Category
            .register(Identifier.fromNamespaceAndPath("observable", "main"))

    @JvmStatic
    fun setScreenOpener(opener: Consumer<DeltaTracker>) {
        screenOpener = opener
    }

    @JvmStatic
    fun setHudRenderer(renderer: BiConsumer<GuiGraphicsExtractor, DeltaTracker>) {
        hudRenderer = renderer
    }

    @JvmStatic
    fun setWorldRenderer(renderer: WorldRenderer) {
        worldRenderer = renderer
    }

    @JvmStatic
    fun setBridge(b: ClientBridge) {
        bridge = b
    }

    @JvmStatic
    fun getBridge(): ClientBridge? = bridge

    @JvmStatic
    fun clear() {
        bridge?.clear()
    }

    @JvmStatic
    fun openProfileScreen() {
        screenOpener?.accept(Minecraft.getInstance().deltaTracker)
    }

    @JvmStatic
    fun isSettingsKeyClicked(): Boolean = bridge?.settingsKey?.consumeClick() ?: false

    @JvmStatic
    fun isOverlayKeyClicked(): Boolean = bridge?.overlayKey?.consumeClick() ?: false

    @JvmStatic
    fun renderHud(
        graphics: GuiGraphicsExtractor,
        delta: DeltaTracker,
    ) {
        hudRenderer?.accept(graphics, delta)
    }

    @JvmStatic
    fun getTranslucent(): RenderType = RenderTypes.debugFilledBox()

    @JvmStatic
    fun getLines(): RenderType = RenderTypes.LINES

    @JvmStatic
    fun render(
        projection: Matrix4fc?,
        modelView: Matrix4fc,
        cameraPos: Vec3,
        stack: Matrix4fc,
        delta: DeltaTracker,
        collector: SubmitNodeCollector?,
        cameraState: CameraRenderState,
        flush: Boolean = false,
    ) {
        if (renderDepth > 0) {
            return
        }
        renderDepth++
        try {
            worldRenderer?.let {
                val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
                it.render(stack, bufferSource, cameraPos, modelView, delta, collector, cameraState, flush)
            }
        } finally {
            renderDepth--
        }
    }

    @JvmStatic
    fun drawWorldPass(
        stack: Matrix4fc,
        bufferSource: MultiBufferSource,
        camPos: Vec3,
        entries: List<BlockEntry>,
        labels: List<LabelEntry>,
        collector: SubmitNodeCollector?,
        cameraState: CameraRenderState,
        flush: Boolean = false,
    ) {
        if (entries.isEmpty() && labels.isEmpty()) return

        val mode = ClientSettings.renderMode

        // 1. Draw X-Ray Geometry (Cubes or Wireframes)
        if (entries.isNotEmpty()) {
            val buffer =
                if (mode == RenderMode.CUBES) {
                    bufferSource.getBuffer(observable.client.ObservableRenderTypes.getXRayBoxes())
                } else {
                    bufferSource.getBuffer(observable.client.ObservableRenderTypes.getXRayLines())
                }

            for (entry in entries) {
                val x = (entry.pos.x - camPos.x).toFloat()
                val y = (entry.pos.y - camPos.y).toFloat()
                val z = (entry.pos.z - camPos.z).toFloat()

                val r = (entry.color shr 16) and 0xFF
                val g = (entry.color shr 8) and 0xFF
                val b = entry.color and 0xFF

                // Inflate slightly to avoid Z-fighting and ensure clean lines
                val i = 0.02f
                val x0 = x - i
                val y0 = y - i
                val z0 = z - i
                val x1 = x + 1.0f + i
                val y1 = y + 1.0f + i
                val z1 = z + 1.0f + i

                if (mode == RenderMode.CUBES) {
                    f(buffer, stack, x0, y0, z0, x1, y1, z1, r, g, b, entry.alpha)
                } else {
                    drawWireframe(buffer, stack, x0, y0, z0, x1, y1, z1, r, g, b, 255)
                }
            }
        }

        // Draw labels
        val font = Minecraft.getInstance().font
        for (label in labels) {
            val distSq = label.pos.distanceToSqr(camPos)
            if (distSq > ProfilerBridge.MAX_DISTANCE_SQ) continue

            val x = (label.pos.x - camPos.x).toFloat()
            val y = (label.pos.y - camPos.y + 0.5).toFloat()
            val z = (label.pos.z - camPos.z).toFloat()

            scratchMatrix.set(stack)
            scratchMatrix.translate(x, y, z)
            scratchMatrix.rotate(cameraState.orientation)
            scratchMatrix.scale(0.025f, -0.025f, 0.025f)

            val text = label.text
            font.drawInBatch(
                text,
                -font.width(text) / 2.0f,
                0.0f,
                label.color or (0xFF shl 24),
                false,
                scratchMatrix,
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                0,
                0xF000F0,
            )
        }

        if (flush && bufferSource is MultiBufferSource.BufferSource) {
            bufferSource.endBatch()
        }
    }

    private fun f(
        c: VertexConsumer,
        m: Matrix4fc,
        x0: Float,
        y0: Float,
        z0: Float,
        x1: Float,
        y1: Float,
        z1: Float,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
    ) {
        v(c, m, x0, y0, z0, r, g, b, a)
        v(c, m, x0, y1, z0, r, g, b, a)
        v(c, m, x1, y1, z0, r, g, b, a)
        v(c, m, x1, y0, z0, r, g, b, a)
        v(c, m, x0, y0, z1, r, g, b, a)
        v(c, m, x1, y0, z1, r, g, b, a)
        v(c, m, x1, y1, z1, r, g, b, a)
        v(c, m, x0, y1, z1, r, g, b, a)
        v(c, m, x0, y0, z0, r, g, b, a)
        v(c, m, x0, y0, z1, r, g, b, a)
        v(c, m, x0, y1, z1, r, g, b, a)
        v(c, m, x0, y1, z0, r, g, b, a)
        v(c, m, x1, y0, z0, r, g, b, a)
        v(c, m, x1, y1, z0, r, g, b, a)
        v(c, m, x1, y1, z1, r, g, b, a)
        v(c, m, x1, y0, z1, r, g, b, a)
        v(c, m, x0, y0, z0, r, g, b, a)
        v(c, m, x1, y0, z0, r, g, b, a)
        v(c, m, x1, y0, z1, r, g, b, a)
        v(c, m, x0, y0, z1, r, g, b, a)
        v(c, m, x0, y1, z0, r, g, b, a)
        v(c, m, x0, y1, z1, r, g, b, a)
        v(c, m, x1, y1, z1, r, g, b, a)
        v(c, m, x1, y1, z0, r, g, b, a)
    }

    private fun drawWireframe(
        c: VertexConsumer,
        m: Matrix4fc,
        x0: Float,
        y0: Float,
        z0: Float,
        x1: Float,
        y1: Float,
        z1: Float,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
    ) {
        l(c, m, x0, y0, z0, x1, y0, z0, r, g, b, a)
        l(c, m, x1, y0, z0, x1, y0, z1, r, g, b, a)
        l(c, m, x1, y0, z1, x0, y0, z1, r, g, b, a)
        l(c, m, x0, y0, z1, x0, y0, z0, r, g, b, a)

        l(c, m, x0, y1, z0, x1, y1, z0, r, g, b, a)
        l(c, m, x1, y1, z0, x1, y1, z1, r, g, b, a)
        l(c, m, x1, y1, z1, x0, y1, z1, r, g, b, a)
        l(c, m, x0, y1, z1, x0, y1, z0, r, g, b, a)

        l(c, m, x0, y0, z0, x0, y1, z0, r, g, b, a)
        l(c, m, x1, y0, z0, x1, y1, z0, r, g, b, a)
        l(c, m, x1, y0, z1, x1, y1, z1, r, g, b, a)
        l(c, m, x0, y0, z1, x0, y1, z1, r, g, b, a)
    }

    private fun l(
        c: VertexConsumer,
        m: Matrix4fc,
        x0: Float,
        y0: Float,
        z0: Float,
        x1: Float,
        y1: Float,
        z1: Float,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
    ) {
        c
            .addVertex(m, x0, y0, z0)
            .setColor(r, g, b, a)
            .setNormal(0.0f, 1.0f, 0.0f)
            .setLineWidth(4.5f)
        c
            .addVertex(m, x1, y1, z1)
            .setColor(r, g, b, a)
            .setNormal(0.0f, 1.0f, 0.0f)
            .setLineWidth(4.5f)
    }

    private fun v(
        c: VertexConsumer,
        m: Matrix4fc,
        x: Float,
        y: Float,
        z: Float,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
    ) {
        c.addVertex(m, x, y, z).setColor(r, g, b, a)
    }

    data class BlockEntry(
        val pos: BlockPos,
        val color: Int,
        val alpha: Int,
    )

    data class LabelEntry(
        val pos: Vec3,
        val text: String,
        val color: Int,
    )
}

interface ClientBridge {
    fun clear()

    val settingsKey: KeyMapping

    val overlayKey: KeyMapping
}
