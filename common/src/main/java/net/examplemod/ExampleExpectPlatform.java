package net.examplemod;

import me.shedaniel.architectury.annotations.ExpectPlatform;
import me.shedaniel.architectury.platform.Platform;

import java.io.File;

public class ExampleExpectPlatform {
    /**
     * We can use {@link Platform#getConfigFolder()} but this is just an example of {@link ExpectPlatform}.
     * <p>
     * This must be a public static method. The platform-implemented solution must be placed under a
     * platform sub-package, with its class suffixed with {@code Impl}.
     * <p>
     * Example:
     * Expect: net.examplemod.ExampleExpectPlatform#getConfigDirectory()
     * Actual Fabric: net.examplemod.fabric.ExampleExpectPlatformImpl#getConfigDirectory()
     * Actual Forge: net.examplemod.forge.ExampleExpectPlatformImpl#getConfigDirectory()
     */
    @ExpectPlatform
    public static File getConfigDirectory() {
        // Just throw an error, the content should get replaced at runtime.
        throw new AssertionError();
    }
}
