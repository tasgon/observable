package observable;

import observable.server.NativeTimingData;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Props {
    public static final AtomicBoolean notProcessing = new AtomicBoolean(true);

    public static final AtomicReference<NativeTimingData> currentTarget = new AtomicReference<>(null);

    public static int entityDepth = -1;
    public static int blockEntityDepth = -1;
    public static int blockDepth = -1;
    public static int fluidDepth = -1;
}
