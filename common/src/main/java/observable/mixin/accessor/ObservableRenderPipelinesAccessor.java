package observable.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.renderer.RenderPipelines.class)
public interface ObservableRenderPipelinesAccessor {
    @Accessor("LINES_SNIPPET")
    static RenderPipeline.Snippet getLinesSnippet() {
        throw new UnsupportedOperationException();
    }

    @Accessor("DEBUG_FILLED_SNIPPET")
    static RenderPipeline.Snippet getDebugFilledSnippet() {
        throw new AssertionError();
    }
}
