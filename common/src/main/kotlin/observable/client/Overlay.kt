package observable.client

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import com.mojang.math.Matrix4f
import glm_.pow
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import observable.Observable
import kotlin.math.roundToInt

object Overlay {
    data class Color(val r: Int, val g: Int, val b: Int, val a: Int) {
        companion object {
            fun fromNanos(rateNanos: Double): Color {
                val micros = rateNanos / 1000.0
                return Color(micros)
            }
        }
        constructor(rateMicros: Double) : this(
            (rateMicros / 100.0 * 255).roundToInt().coerceIn(0, 255),
            ((100.0 - rateMicros) / 100.0 * 255).roundToInt().coerceIn(0, 255),
            0,
            (rateMicros / 100.0 * 255 + 25).roundToInt().coerceIn(0, 255)
        )

        val hex: Int = with(this) {
            val red = if (r > g) 0xFFu else (255 * r / g).toUInt()
            val green = if (g > r) 0xFFu else (255 * g / r).toUInt()
            (red shl 16) or (green shl 8) or (0xFFu shl 24)
        }.toInt()
    }

    sealed class Entry(val color: Color) {
        data class EntityEntry(val entityId: Int, val rate: Double) : Entry(Color.fromNanos(rate)) {
            val entity get() = Minecraft.getInstance().level?.getEntity(entityId)
        }
        data class BlockEntry(val pos: BlockPos, val rate: Double) : Entry(Color.fromNanos(rate))
    }

    var enabled = true
    var entities: List<Entry.EntityEntry> = ArrayList()
    var blocks: List<Entry.BlockEntry> = ArrayList()
    var blockMap = mapOf<ChunkPos, List<Entry.BlockEntry>>()
    lateinit var loc: Vec3
    var vertexBuf: VertexBuffer? = null
    var dataAvailable = false

    val DIST_FAC = 1.0 / (2*16.pow(2)).pow(.5F)

    val font: Font by lazy { Minecraft.getInstance().font }

    class OverlayRenderType(name: String, fmt: VertexFormat, mode: VertexFormat.Mode) :
            RenderType(name, fmt,
            VertexFormat.Mode.QUADS, 256, false, true, {}, {}) {
        companion object {
            fun build(): RenderType {
                val boolType = java.lang.Boolean.TYPE
                val field = if (System.getenv("DEV_ENV") != null) "create" else "method_24049"
                val fn = RenderType::class.java.getDeclaredMethod(field,
                        String::class.java, VertexFormat::class.java, VertexFormat.Mode::class.java,
                        Integer.TYPE, boolType, boolType, RenderType.CompositeState::class.java)
                fn.isAccessible = true

                return fn.invoke(null, "heat", DefaultVertexFormat.POSITION_COLOR,
                        VertexFormat.Mode.QUADS, 256, false, false, buildCompositeState()) as RenderType
            }

            private fun buildCompositeState(): CompositeState {
                return RenderType.CompositeState.builder()
                    .setShaderState(ShaderStateShard { GameRenderer.getPositionColorShader() })
//                    .setTextureState(EmptyTextureStateShard({}, {}))
                    .setDepthTestState(DepthTestStateShard("always", 519))
                    .setTransparencyState(
                            RenderStateShard.TransparencyStateShard("src_to_one",
                                    {
                                        RenderSystem.enableBlend()
                                        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                                                GlStateManager.DestFactor.ONE)
                                    }
                            ) {
                                RenderSystem.disableBlend()
                                RenderSystem.defaultBlendFunc()
                            }).createCompositeState(true)
            }
        }
    }

    @Suppress("INACCESSIBLE_TYPE")
    private val renderType: RenderType by lazy {
        OverlayRenderType.build()
    }

    fun load(lvl: ClientLevel? = null) {
        val data = Observable.RESULTS ?: return
        val level = lvl ?: Minecraft.getInstance().level ?: return
        val levelLocation = level.dimension().location()
        val ticks = data.ticks
        val norm = Settings.normalized
        entities = data.entities[levelLocation]?.map {
            Entry.EntityEntry(it.obj, it.rate * (if (norm) it.ticks.toDouble() / ticks else 1.0))
        }?.filter { it.rate >= Settings.minRate }.orEmpty()

        blocks = data.blocks[levelLocation]?.map {
            Entry.BlockEntry(it.obj, it.rate * (if (norm) it.ticks.toDouble() / ticks else 1.0))
        }?.filter { it.rate >= Settings.minRate }.orEmpty()
        blockMap = blocks.groupBy { ChunkPos(it.pos) }

        dataAvailable = true

    }

    inline fun loadSync(lvl: ClientLevel? = null) = synchronized(this) {
        this.load(lvl)
    }

    fun render(poseStack: PoseStack, partialTicks: Float, projection: Matrix4f) {
        if (!enabled) return

        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        val bufSrc = Minecraft.getInstance().renderBuffers().bufferSource()


        RenderSystem.disableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)

        poseStack.pushPose()

        if (dataAvailable) {
            createVBO(camera)
            dataAvailable = false
        }

        camera.position.apply {
            poseStack.translate(-x, -y, -z)
        }

        synchronized(this) {
            val cpos = ChunkPos(Minecraft.getInstance().player!!.blockPosition())
            val dist = (Settings.maxBlockDist / 16).coerceAtLeast(2)
            for (x in (cpos.x - dist)..(cpos.x + dist)) {
                for (y in (cpos.z - dist)..(cpos.z + dist)) {
                    blockMap[ChunkPos(x, y)]?.forEach { entry ->
                        if (camera.blockPosition.distSqr(entry.pos) < Settings.maxBlockDist.pow(2)) {
                            drawBlock(entry, poseStack, camera, bufSrc)
//                            drawBlockOutlineTest(entry, poseStack, bufSrc)
                        }
                    }
                }
            }
            if (entities.size < Settings.maxEntityCount) for (entry in entities) {
                drawEntity(entry, poseStack, partialTicks, camera, bufSrc)
            }


            vertexBuf?.let {
                RenderSystem.setShader { GameRenderer.getPositionColorShader() }
                RenderSystem.disableTexture()
                it.drawWithShader(poseStack.last().pose(), projection, GameRenderer.getPositionColorShader()!!)
                RenderSystem.enableTexture()
            }
        }

        poseStack.popPose()
        bufSrc.endBatch()

        // Cleanup
        RenderSystem.enableDepthTest()
    }

    fun createVBO(camera: Camera) {
        Observable.LOGGER.info("Initializing VBO")
        vertexBuf?.close()
        val buf = BufferBuilder(renderType.bufferSize() * blocks.size)
        buf.begin(renderType.mode(), renderType.format())

        var stack = PoseStack()

        for (entry in blocks) {
            drawBlockOutline(entry, stack, buf)
        }

        buf.end()
        val vbuf = VertexBuffer()
        vbuf.upload(buf)
        vertexBuf = vbuf
        dataAvailable = false
    }

    inline fun drawEntity(entry: Entry.EntityEntry, poseStack: PoseStack,
                          partialTicks: Float, camera: Camera, bufSrc: MultiBufferSource) {
        val rate = entry.rate
        val entity = entry.entity ?: return
        if (entity.isRemoved || (entity == Minecraft.getInstance().player
            && entity.deltaMovement.lengthSqr() > .01)) return

        poseStack.pushPose()
        var text = "${(rate / 1000).roundToInt()} μs/t"
        var pos = entity.position()
        if (camera.position.distanceTo(pos) > Settings.maxEntityDist) return
        if (entity.isAlive) pos = pos.add(with(entity.deltaMovement) {
            Vec3(x, y.coerceAtLeast(0.0), z)
        }.scale(partialTicks.toDouble()))
        else text += " [X]"

        pos.apply {
            poseStack.translate(x, y + entity.bbHeight + 0.33, z)
            poseStack.mulPose(camera.rotation())
            poseStack.scale(-0.025F, -0.025F, 0.025F)
            font.drawInBatch(text, -font.width(text).toFloat() / 2, 0F, entry.color.hex, false,
                poseStack.last().pose(), bufSrc, true, 0, 0xF000F0)
        }

        poseStack.popPose()
    }

    private inline fun drawBlockOutlineTest(entry: Entry.BlockEntry, poseStack: PoseStack, bufSrc: MultiBufferSource) {
        val buf = bufSrc.getBuffer(renderType)
        drawBlockOutline(entry, poseStack, buf)
    }

    private inline fun drawBlockOutline(entry: Entry.BlockEntry, poseStack: PoseStack, buf: VertexConsumer) {
        poseStack.pushPose()

        entry.pos.apply {
            poseStack.translate(x.toDouble(), y.toDouble(), z.toDouble())
        }
        val mat = poseStack.last().pose()
        entry.color.apply {
            buf.vertex(mat, 0F, 1F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 1F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 1F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 1F, 0F).color(r, g, b, a).endVertex()

            buf.vertex(mat, 0F, 1F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 1F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 0F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 0F, 0F).color(r, g, b, a).endVertex()

            buf.vertex(mat, 1F, 1F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 1F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 0F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 0F, 1F).color(r, g, b, a).endVertex()

            buf.vertex(mat, 0F, 1F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 1F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 0F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 0F, 1F).color(r, g, b, a).endVertex()

            buf.vertex(mat, 1F, 0F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 0F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 1F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 1F, 1F).color(r, g, b, a).endVertex()

            buf.vertex(mat, 1F, 0F, 0F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 1F, 0F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 0F, 1F).color(r, g, b, a).endVertex()
            buf.vertex(mat, 0F, 0F, 0F).color(r, g, b, a).endVertex()
        }

        poseStack.popPose()
    }

    private inline fun drawBlock(entry: Entry.BlockEntry, poseStack: PoseStack,
                                 camera: Camera, bufSrc: MultiBufferSource) {
        poseStack.pushPose()

        val (pos, rate) = entry
        val text = "${(rate / 1000).roundToInt()} μs/t"

        val col: Int = -0x1
        pos.apply {
            poseStack.translate(x + 0.5, y + 0.5, z + 0.5)
            poseStack.mulPose(camera.rotation())
            poseStack.scale(-0.025F, -0.025F, 0.025F)
            font.drawInBatch(text, -font.width(text).toFloat() / 2, 0F, col, false,
                poseStack.last().pose(), bufSrc, true, 0, 0xF000F0)
        }

        poseStack.popPose()
    }
}