package observable

import ProfilingData
import com.mojang.blaze3d.platform.InputConstants
import me.shedaniel.architectury.event.events.GuiEvent
import me.shedaniel.architectury.event.events.client.ClientTickEvent
import me.shedaniel.architectury.registry.KeyBindings
import me.shedaniel.architectury.registry.Registries
import net.minecraft.client.KeyMapping
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.LazyLoadedValue
import observable.client.Overlay
import observable.client.ProfileScreen
import observable.net.BetterChannel
import observable.net.C2SPacket
import observable.net.S2CPacket
import observable.server.Profiler
import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW

object Observable {
    const val MOD_ID = "observable"

    // We can use this if we don't want to use DeferredRegister
    val REGISTRIES = LazyLoadedValue { Registries.get(MOD_ID) }

    private val PROFILE_KEYMAP by lazy { KeyMapping("key.observable.profile",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.observable.keybinds") }

    val CHANNEL = BetterChannel(ResourceLocation("channel/observable"))
    val LOGGER = LogManager.getLogger("Observable")
    val PROFILER: Profiler by lazy { Profiler() }
    var RESULTS: ProfilingData? = null
    val PROFILE_SCREEN by lazy { ProfileScreen() }

    @JvmStatic
    fun init() {
        CHANNEL.register { t: C2SPacket.InitTPSProfile, supplier ->
            PROFILER.startRunning(t.duration, supplier.get())
        }

        CHANNEL.register { t: S2CPacket.ProfilingStarted, supplier ->
            PROFILE_SCREEN.action = ProfileScreen.Action.TPSProfilerRunning(t.endNanos)
        }

        CHANNEL.register { t: S2CPacket.ProfilingResult, supplier ->
            RESULTS = t.data
            PROFILE_SCREEN.apply {
                action = ProfileScreen.Action.DEFAULT
                arrayOf(resultsBtn, overlayBtn).forEach { it.active = true }
            }
            val data = t.data.entries
            LOGGER.info("Received profiling result with ${data.size} entries")
            synchronized(Overlay) {
                Overlay.load(t.data)
            }
        }
    }

    @JvmStatic
    fun clientInit() {
        KeyBindings.registerKeyBinding(PROFILE_KEYMAP)

        ClientTickEvent.CLIENT_POST.register {
            if (PROFILE_KEYMAP.consumeClick()) {
                it.setScreen(PROFILE_SCREEN)
            }
        }

        GuiEvent.RENDER_HUD.register { stack, v ->

        }
    }
}