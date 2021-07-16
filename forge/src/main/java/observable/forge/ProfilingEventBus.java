/*
 * Minecraft Forge
 * Copyright (c) 2016.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package observable.forge;

import net.minecraftforge.eventbus.*;
import net.minecraftforge.eventbus.api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;

/**
 * Something that God never intended.
 *
 * To summarize the issue: we want to monitor event listeners, but we can't modify `EventBus` to insert our monitoring
 * code. So instead, we replace the entire event bus with our own, which proxies everything to the original bus
 * except the posting system, where we then insert our code. When we're done, we put back the original event bus,
 * and no (serious) harm was done in the process.
 */
public class ProfilingEventBus implements IEventExceptionHandler, IEventBus {
    private static final Logger LOGGER = LogManager.getLogger();
    static final Marker PROFILING_EVENTBUS = MarkerManager.getMarker("PROFILING_EVENT_BUS");
    private static final boolean checkTypesOnDispatch = Boolean.parseBoolean(System.getProperty("eventbus.checkTypesOnDispatch", "false"));

    private final EventBus originalBus;

    private final boolean trackPhases;
    private final int busID;
    private final IEventExceptionHandler exceptionHandler;
    private volatile boolean shutdown = false;

    private final Class<?> baseType;

    private int count = 0;


    public ProfilingEventBus(EventBus originalBus) throws NoSuchFieldException, IllegalAccessException
    {
        this.originalBus = originalBus;
        this.trackPhases = getField("trackPhases").getBoolean(originalBus);
        this.busID = getField("busID").getInt(originalBus);
        this.exceptionHandler = (IEventExceptionHandler) getField("exceptionHandler").get(originalBus);
        this.baseType = (Class<?>) getField("baseType").get(originalBus);
    }

    private Field getField(String name) throws NoSuchFieldException, IllegalAccessException {
        Field f = EventBus.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }


    @Override
    public void register(final Object target)
    {
        originalBus.register(target);
    }

    @Override
    public <T extends Event> void addListener(final Consumer<T> consumer) {
        originalBus.addListener(consumer);
    }

    @Override
    public <T extends Event> void addListener(final EventPriority priority, final Consumer<T> consumer) {
        originalBus.addListener(priority, consumer);
    }

    @Override
    public <T extends Event> void addListener(final EventPriority priority, final boolean receiveCancelled, final Consumer<T> consumer) {
        originalBus.addListener(priority, receiveCancelled, consumer);
    }

    @Override
    public <T extends Event> void addListener(final EventPriority priority, final boolean receiveCancelled, final Class<T> eventType, final Consumer<T> consumer) {
        originalBus.addListener(priority, receiveCancelled, eventType, consumer);
    }

    @Override
    public <T extends GenericEvent<? extends F>, F> void addGenericListener(final Class<F> genericClassFilter, final Consumer<T> consumer) {
        originalBus.addGenericListener(genericClassFilter, consumer);
    }

    @Override
    public <T extends GenericEvent<? extends F>, F> void addGenericListener(final Class<F> genericClassFilter, final EventPriority priority, final Consumer<T> consumer) {
        originalBus.addGenericListener(genericClassFilter, priority, consumer);
    }

    @Override
    public <T extends GenericEvent<? extends F>, F> void addGenericListener(final Class<F> genericClassFilter, final EventPriority priority, final boolean receiveCancelled, final Consumer<T> consumer) {
        originalBus.addGenericListener(genericClassFilter, priority, receiveCancelled, consumer);
    }

    @Override
    public <T extends GenericEvent<? extends F>, F> void addGenericListener(final Class<F> genericClassFilter, final EventPriority priority, final boolean receiveCancelled, final Class<T> eventType, final Consumer<T> consumer) {
        originalBus.addGenericListener(genericClassFilter, priority, receiveCancelled, eventType, consumer);
    }

    @Override
    public void unregister(Object object)
    {
        originalBus.unregister(object);
    }

    @Override
    public boolean post(Event event) {
        return post(event, (IEventListener::invoke));
    }

    @Override
    public boolean post(Event event, IEventBusInvokeDispatcher wrapper)
    {
        if (shutdown) return false;
        if (ProfilingEventBus.checkTypesOnDispatch && !baseType.isInstance(event))
        {
            throw new IllegalArgumentException("Cannot post event of type " + event.getClass().getSimpleName() + " to this event. Must match type: " + baseType.getSimpleName());
        }

        IEventListener[] listeners = event.getListenerList().getListeners(busID);
        int index = 0;
        try
        {
            for (; index < listeners.length; index++)
            {
                if (!trackPhases && Objects.equals(listeners[index].getClass(), EventPriority.class)) continue;
                if (count % 1000 == 0) LOGGER.info(count + " Event: " + event + "; Listener: " + listeners[index].getClass().getName());
                wrapper.invoke(listeners[index], event);
                count++;
            }git
        }
        catch (Throwable throwable)
        {
            exceptionHandler.handleException(this, event, listeners, index, throwable);
            throw throwable;
        }
        return event.isCancelable() && event.isCanceled();
    }

    @Override
    public void handleException(IEventBus bus, Event event, IEventListener[] listeners, int index, Throwable throwable)
    {
        originalBus.handleException(bus, event, listeners, index, throwable);
    }

    @Override
    public void shutdown()
    {
        LOGGER.fatal(PROFILING_EVENTBUS, "Passing shutdown request to original event bus");
        originalBus.shutdown();
        this.shutdown = true;
    }

    @Override
    public void start() {
        originalBus.start();
        this.shutdown = false;
    }
}
