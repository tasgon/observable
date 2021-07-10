package observable

import me.shedaniel.architectury.registry.CreativeTabs
import me.shedaniel.architectury.registry.DeferredRegister
import me.shedaniel.architectury.registry.Registries
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.LazyLoadedValue
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import org.apache.logging.log4j.LogManager

object Observable {
    const val MOD_ID = "observable"

    // We can use this if we don't want to use DeferredRegister
    val REGISTRIES = LazyLoadedValue { Registries.get(MOD_ID) }

    // Registering a new creative tab
    val EXAMPLE_TAB: CreativeModeTab = CreativeTabs.create(ResourceLocation(MOD_ID, "example_tab")) { ItemStack(EXAMPLE_ITEM.get()) }
    val ITEMS = DeferredRegister.create(MOD_ID, Registry.ITEM_REGISTRY)
    val EXAMPLE_ITEM = ITEMS.register("example_item") { Item(Item.Properties().tab(Observable.EXAMPLE_TAB)) }

    public val LOGGER = LogManager.getLogger("Observable")

    @JvmStatic
    fun init() {
    }
}