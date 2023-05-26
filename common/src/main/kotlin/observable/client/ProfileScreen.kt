package observable.client

import com.mojang.blaze3d.vertex.PoseStack
import dev.architectury.utils.GameInstance
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import observable.Observable
import observable.net.C2SPacket
import observable.server.DataWithDiagnostics
import java.io.File
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

class ProfileScreen : Screen(TranslatableComponent("screen.observable.profile")) {
    companion object {
        private val STATUS_FILE get() = File("o_prof")
        var HAS_BEEN_OPENED = STATUS_FILE.exists()
            private set(value) {
                if (value && !field) {
                    try {
                        STATUS_FILE.createNewFile()
                    } catch (e: Exception) {
                        Observable.LOGGER.warn("Could not create status file: ${e.message}")
                        e.printStackTrace()
                    }
                }
                field = value
            }
    }

    init {
        this.init()
    }

    sealed class Action {
        companion object {
            val DEFAULT = NewProfile(30)
            val UNAVAILABLE = ObservableStatus("text.observable.unavailable")
            val NO_PERMISSIONS = ObservableStatus("text.observable.no_permissions")
        }
        data class NewProfile(var duration: Int) : Action()
        data class TPSProfilerRunning(val endTime: Long) : Action()
        object TPSProfilerCompleted : Action()
        data class ObservableStatus(val text: String) : Action()
        data class Custom(val text: String) : Action()

        val statusMsg get() = when (this) {
            is NewProfile -> TranslatableComponent("text.observable.status.new", duration)
            is TPSProfilerRunning -> TranslatableComponent(
                "text.observable.status.running",
                "%.1f".format(((endTime - System.currentTimeMillis()).toDouble() / 1e3).coerceAtLeast(0.0))
            )
            is TPSProfilerCompleted -> TranslatableComponent("text.observable.status.finished")
            is ObservableStatus -> TranslatableComponent(text)
            is Custom -> TextComponent(text)
        }
    }

    var action: Action = Action.UNAVAILABLE
    var startBtn: Button? = null
    var sample = false
    lateinit var fpsBtn: Button
    lateinit var editField: EditBox
    lateinit var getBtn: Button
    lateinit var overlayBtn: BetterCheckbox

    fun openLink(dest: String) {
        val mc = Minecraft.getInstance()
        mc.setScreen(
            ConfirmLinkScreen({ bl: Boolean ->
                if (bl) {
                    Util.getPlatform().openUri(dest)
                }
                mc.setScreen(this)
            }, dest, true)
        )
    }

    fun getData(url: String) {
        getBtn.active = false
        Thread {
            var apiUrl = url
            if (url.contains('#')) {
                val hash = url.split('#').last()
                apiUrl = "https://observable.tas.sh/get/$hash"
            }
            Observable.LOGGER.info("GET $apiUrl")
            try {
                val request = URL(apiUrl)
                    .openStream()
                    .let { GZIPInputStream(it) }
                    .bufferedReader()
                    .use { it.readText() }
                Observable.RESULTS = Json.decodeFromString<DataWithDiagnostics>(request).data
                Overlay.loadSync()
            } catch (e: Exception) {
                Observable.LOGGER.error("Profile download error", e)
                val errMsg = TextComponent("Error: ${e.message}")
                GameInstance.getClient().player?.displayClientMessage(errMsg, true)
            } finally {
                getBtn.active = true
            }
        }.start()
    }

    fun button(x: Int, y: Int, width: Int, height: Int, component: Component, onPress: () -> Unit): Button {
        val btn = Button(
            x,
            y,
            width,
            height,
            component
        ) { onPress() }
        return addRenderableWidget(btn)
    }

    override fun init() {
        super.init()

        HAS_BEEN_OPENED = true

        val startBtn = button(
            0,
            height / 2 - 48,
            100,
            20,
            TranslatableComponent("text.observable.profile_tps")
        ) {
            val duration = (action as Action.NewProfile).duration
            Observable.CHANNEL.sendToServer(C2SPacket.InitTPSProfile(duration, sample))
        }
        startBtn.active = action is Action.NewProfile
        startBtn.x = width / 2 - startBtn.width - 4

        fpsBtn = button(
            width / 2 + 4,
            startBtn.y,
            startBtn.width,
            startBtn.height,
            TranslatableComponent("screen.observable.client_settings")
        ) {
            GameInstance.getClient().setScreen(ClientSettingsGui())
        }

        val samplerBtn = addRenderableWidget(
            BetterCheckbox(
                startBtn.x,
                startBtn.y + startBtn.height + 4,
                fpsBtn.x + fpsBtn.width - startBtn.x,
                20,
                TranslatableComponent("text.observable.sampler"),
                sample
            ) {
                sample = it
            }
        )

        val longWidth = fpsBtn.x + fpsBtn.width - samplerBtn.x
        val smallWidth = longWidth / 3 - 2

        val editFieldDesc = TranslatableComponent("text.observable.get_field")
        editField = addRenderableWidget(
            EditBox(
                GameInstance.getClient().font,
                samplerBtn.x,
                samplerBtn.y + samplerBtn.height + 16,
                smallWidth * 2,
                20,
                editFieldDesc
            )
        )
        editField.setSuggestion(editFieldDesc.string)
        editField.setResponder {
            editField.setSuggestion(if (it.isEmpty()) editFieldDesc.string else "")
        }

        getBtn = button(
            editField.x + editField.width + 8,
            editField.y,
            smallWidth,
            20,
            TranslatableComponent("text.observable.get_btn")
        ) {
            getData(editField.value)
        }

        overlayBtn = addRenderableWidget(
            BetterCheckbox(
                editField.x,
                editField.y + editField.height + 4,
                editField.width,
                20,
                TranslatableComponent("text.observable.overlay"),
                Overlay.enabled
            ) {
                if (it) {
                    synchronized(Overlay) {
                        Overlay.load()
                    }
                }
                Overlay.enabled = it
            }
        )

        if (Observable.RESULTS == null) {
            arrayOf(editField, overlayBtn).forEach {
                it.active = false
            }
        }

        val learnBtn = button(
            startBtn.x,
            overlayBtn.y + overlayBtn.height + 8,
            smallWidth,
            20,
            TranslatableComponent("text.observable.docs")
        ) {
            openLink("https://github.com/tasgon/observable/wiki")
        }
        val helpBtn = button(
            learnBtn.x + learnBtn.width + 4,
            learnBtn.y,
            smallWidth,
            20,
            TranslatableComponent("text.observable.discord")
        ) {
            openLink("https://discord.gg/sfPbb3b5tF")
        }
        val donateBtn = button(
            helpBtn.x + helpBtn.width + 4,
            helpBtn.y,
            smallWidth,
            20,
            TranslatableComponent("text.observable.donate")
        ) {
            openLink("https://github.com/tasgon/observable/wiki/Support-this-project")
        }

        this.startBtn = startBtn
        if (action == Action.UNAVAILABLE ||
            action == Action.NO_PERMISSIONS
        ) {
            Observable.CHANNEL.sendToServer(C2SPacket.RequestAvailability)
        }
    }

    override fun isPauseScreen() = false

    override fun render(poseStack: PoseStack, i: Int, j: Int, f: Float) {
        GuiComponent.drawCenteredString(
            poseStack,
            this.font,
            action.statusMsg,
            width / 2,
            startBtn!!.y - this.font.lineHeight - 4,
            0xFFFFFF
        )

        super.render(poseStack, i, j, f)
    }

    override fun mouseScrolled(d: Double, e: Double, f: Double): Boolean {
        (action as? Action.NewProfile)?.apply {
            duration += f.roundToInt() * 5
            duration = this.duration.coerceIn(5, 60)
        }

        return super.mouseScrolled(d, e, f)
    }
}
