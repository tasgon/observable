package observable.client

import com.mojang.blaze3d.platform.InputConstants
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import observable.Observable
import observable.net.S2CPacket

object ObservableClient {
    val PROFILE_SCREEN by lazy { ProfileScreen() }

    val KEY_OPEN_SETTINGS by lazy { KeyMapping("key.observable.settings", 85, ProfilerBridge.CATEGORY) }
    val KEY_TOGGLE_OVERLAY by lazy {
        KeyMapping("key.observable.overlay", InputConstants.UNKNOWN.value, ProfilerBridge.CATEGORY)
    }
    val KEY_CYCLE_RENDER_MODE by lazy {
        KeyMapping("key.observable.cycle_render_mode", InputConstants.UNKNOWN.value, ProfilerBridge.CATEGORY)
    }

    var isOverlayEnabled: Boolean
        get() = ClientConfig.data.isOverlayEnabled
        set(v) {
            ClientConfig.data.isOverlayEnabled = v
            ClientConfig.save()
        }

    fun showJoinMessage() {
        if (ClientConfig.data.silenceJoinMessage) return
        val mc = Minecraft.getInstance()
        val keyOverlay = KEY_TOGGLE_OVERLAY.translatedKeyMessage
        val keySettings = KEY_OPEN_SETTINGS.translatedKeyMessage
        val msg = Component.translatable("text.observable.join_message", keyOverlay, keySettings)
        mc.player?.sendSystemMessage(msg)
    }

    @JvmStatic
    fun clientInit() {
        Observable.LOGGER.info("Starting Observable Client-side initialization...")
        ClientConfig.load()
        Overlay.init()
        ProfilerBridge.setBridge(Overlay)
        ProfilerBridge.setWorldRenderer(Overlay)
        ProfilerBridge.setHudRenderer(Overlay::renderHud)

        ProfilerBridge.setScreenOpener {
            val mc = Minecraft.getInstance()
            if (mc.screen is ProfileScreen) {
                mc.setScreen(null)
            } else {
                mc.setScreen(PROFILE_SCREEN)
            }
        }

        KeyMappingRegistry.register(KEY_OPEN_SETTINGS)
        KeyMappingRegistry.register(KEY_TOGGLE_OVERLAY)
        KeyMappingRegistry.register(KEY_CYCLE_RENDER_MODE)

        ClientTickEvent.CLIENT_POST.register { mc ->
            if (KEY_OPEN_SETTINGS.consumeClick() && mc.player != null) {
                ProfilerBridge.openProfileScreen()
            }
            while (KEY_TOGGLE_OVERLAY.consumeClick()) {
                isOverlayEnabled = !isOverlayEnabled
                mc.player?.sendSystemMessage(
                    Component.literal(
                        "§7[§6Observable§7] §fOverlay " +
                            (if (isOverlayEnabled) "§aEnabled" else "§cDisabled"),
                    ),
                )
            }
            KeyBindDeepLink.tick()
        }

        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register {
            showJoinMessage()
        }

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register {
            Observable.clearResults()
            ProfilerBridge.clear()
        }

        Observable.CHANNEL.register { t: S2CPacket.ProfilingStarted, _ ->
            PROFILE_SCREEN.action = ProfileScreen.Action.TPSProfilerRunning(t.endMillis)
        }

        Observable.CHANNEL.register { _: S2CPacket.ProfilingCompleted, _ ->
            PROFILE_SCREEN.action = ProfileScreen.Action.TPSProfilerCompleted
        }

        Observable.CHANNEL.register { t: S2CPacket.ProfilingResult, _ ->
            Observable.RESULTS = t.data
            PROFILE_SCREEN.action = ProfileScreen.Action.NewProfile(ClientConfig.data.profileDuration)
            Overlay.loadSync()

            val mc = Minecraft.getInstance()

            if (t.link != null) {
                val linkComp =
                    Component.literal(t.link)
                        .withStyle {
                            it.withColor(net.minecraft.ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(observable.util.clickOpenUrl(t.link))
                        }

                val msg = Component.translatable("text.observable.profile_uploaded", linkComp)
                mc.player?.sendSystemMessage(msg)
            } else {
                val localLink = ProfileExporter.export(t.data)
                val msg = Component.translatable("text.observable.profile_saved", localLink)
                mc.player?.sendSystemMessage(msg)
            }
        }

        Observable.CHANNEL.register { t: S2CPacket.Availability, _ ->
            PROFILE_SCREEN.action =
                when (t) {
                    S2CPacket.Availability.Available -> ProfileScreen.Action.NewProfile(ClientConfig.data.profileDuration)
                    S2CPacket.Availability.NoPermissions -> ProfileScreen.Action.NO_PERMISSIONS
                }
        }

        Observable.LOGGER.info("Observable Client-side initialization complete.")
    }
}
