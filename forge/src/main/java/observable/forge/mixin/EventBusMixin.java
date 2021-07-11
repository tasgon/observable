package observable.forge.mixin;

import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBusInvokeDispatcher;
import net.minecraftforge.eventbus.api.IEventListener;
import observable.Observable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EventBus.class)
public class EventBusMixin {
    @Redirect(method = "post", remap = false, at = @At(value = "INVOKE",
        target = "net/minecraftforge/eventbus/api/IEventBusInvokeDispatcher;invoke(Lnet/minecraftforge/eventbus/api/IEventListener;Lnet/minecraftforge/eventbus/api/Event;)V"))
    public void redirectInvoke(IEventBusInvokeDispatcher dispatcher, IEventListener listener, Event event) {
        Observable.INSTANCE.getLOGGER().info("Processing listener: " + listener.getClass().getName());
        dispatcher.invoke(listener, event);
    }
}
