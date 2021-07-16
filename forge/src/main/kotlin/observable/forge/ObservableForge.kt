package observable.forge

import observable.Observable.init
import me.shedaniel.architectury.platform.forge.EventBuses
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.EventBus
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import observable.Observable
import observable.server.Profiler
import java.lang.reflect.Field
import java.lang.reflect.Modifier

@Mod(Observable.MOD_ID)
class ObservableForge {
    // Never lose this
    val originalBus = MinecraftForge.EVENT_BUS

    init {
        EventBuses.registerModEventBus(Observable.MOD_ID, FMLJavaModLoadingContext.get().modEventBus)
        FMLJavaModLoadingContext.get().modEventBus.addListener(this::onClientInit)

        val profilingBus = ProfilingEventBus(originalBus as EventBus)

        val eventBusField = MinecraftForge::class.java.getField("EVENT_BUS");
        eventBusField.isAccessible = true

        val mods = Field::class.java.getDeclaredField("modifiers")
        mods.isAccessible = true
        mods.setInt(eventBusField, eventBusField.modifiers and Modifier.FINAL.inv())

        Profiler.onStart {
            synchronized(MinecraftForge.EVENT_BUS) {
                eventBusField.set(null, profilingBus as IEventBus)
            }
            Observable.LOGGER.info("Mounted profiling bus. Current bus: ${MinecraftForge.EVENT_BUS}")
        }
        Profiler.onEnd {
            synchronized(MinecraftForge.EVENT_BUS) {
                eventBusField.set(null, originalBus)
            }
            Observable.LOGGER.info("Unmounted profiling bus. Current bus: ${MinecraftForge.EVENT_BUS}")
        }

        init()
    }

    fun onClientInit(ev: FMLClientSetupEvent) {
        Observable.clientInit()
        MinecraftForge.EVENT_BUS.register(ForgeClientHooks)
    }
}