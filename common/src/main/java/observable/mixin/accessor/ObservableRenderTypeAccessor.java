package observable.mixin.accessor;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface ObservableRenderTypeAccessor {
    @Invoker("create")
    static RenderType callCreate(String name, RenderSetup setup) {
        throw new UnsupportedOperationException();
    }
}
