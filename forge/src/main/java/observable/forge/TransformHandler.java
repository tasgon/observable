package observable.forge;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBusInvokeDispatcher;
import net.minecraftforge.eventbus.api.IEventListener;
import observable.Observable;

public class TransformHandler {
    public static void handleInvoke(IEventBusInvokeDispatcher dispatcher, IEventListener listener, Event event) {
        Observable.INSTANCE.getLOGGER().info("Processing listener: " + listener.getClass().getName());
        dispatcher.invoke(listener, event);
    }
}
