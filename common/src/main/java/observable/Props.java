package observable;

import observable.server.Profiler;

import java.util.concurrent.atomic.AtomicReference;

public class Props {
    public static boolean notProcessing = true;

    public static AtomicReference<Profiler.TimingData> currentTarget = new AtomicReference<>(null);

    public static int entityDepth = -1;
    public static int blockEntityDepth = -1;
    public static int blockDepth = -1;
    public static int fluidDepth = -1;
}
