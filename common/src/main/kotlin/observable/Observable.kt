package observable

import com.mojang.blaze3d.platform.InputConstants
import me.shedaniel.architectury.event.events.client.ClientRawInputEvent
import me.shedaniel.architectury.event.events.client.ClientTickEvent
import me.shedaniel.architectury.networking.NetworkChannel
import me.shedaniel.architectury.networking.NetworkManager
import me.shedaniel.architectury.registry.CreativeTabs
import me.shedaniel.architectury.registry.DeferredRegister
import me.shedaniel.architectury.registry.KeyBindings
import me.shedaniel.architectury.registry.Registries
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.LazyLoadedValue
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import observable.client.ProfileScreen
import observable.server.Profiler
import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

object Observable {
    const val MOD_ID = "observable"

    // We can use this if we don't want to use DeferredRegister
    val REGISTRIES = LazyLoadedValue { Registries.get(MOD_ID) }

    // Registering a new creative tab
    val EXAMPLE_TAB: CreativeModeTab = CreativeTabs.create(ResourceLocation(MOD_ID, "example_tab")) { ItemStack(EXAMPLE_ITEM.get()) }
    val ITEMS = DeferredRegister.create(MOD_ID, Registry.ITEM_REGISTRY)
    val EXAMPLE_ITEM = ITEMS.register("example_item") { Item(Item.Properties().tab(Observable.EXAMPLE_TAB)) }

    val PROFILE_KEYBIND = KeyMapping("key.observable.profile",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.observable.keybinds")

//    val CHANNEL = NetworkChannel.create(ResourceLocation("net/channel/observable"))

    val LOGGER = LogManager.getLogger("Observable")

    val PROFILER: Profiler? = null

    @JvmStatic
    fun init() {
    }

    @JvmStatic
    fun clientInit() {
        KeyBindings.registerKeyBinding(PROFILE_KEYBIND)

        ClientTickEvent.CLIENT_POST.register {
            if (PROFILE_KEYBIND.consumeClick()) {
                it.setScreen(ProfileScreen())
            }
        }

        ClientRawInputEvent.MOUSE_SCROLLED.register { mc, dir ->
            LOGGER.info("scroll")
            (mc.screen as? ProfileScreen)?.let {
                it.duration += dir.roundToInt()
                InteractionResult.CONSUME
            } ?: run {
                InteractionResult.PASS
            }
        }
    }
}