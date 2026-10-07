package observable.client;

import observable.mixin.accessor.ObservableRenderPipelinesAccessor;
import observable.mixin.accessor.ObservableRenderTypeAccessor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import java.util.Optional;

public class ObservableRenderTypes {
    private static RenderType XRAY_LINES;
    private static RenderType XRAY_BOXES;

    public static RenderType getXRayLines() {
        if (XRAY_LINES == null) {
            XRAY_LINES = ObservableRenderTypeAccessor.callCreate(
                "observable_xray_lines",
                RenderSetup.builder(
                    com.mojang.blaze3d.pipeline.RenderPipeline.builder(ObservableRenderPipelinesAccessor.getLinesSnippet())
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(
                    Optional.of(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT),
                    com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_ALL
                ))
                .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("observable", "xray_lines"))
                .build()
                ).createRenderSetup()
            );
        }
        return XRAY_LINES;
    }

    public static RenderType getXRayBoxes() {
        if (XRAY_BOXES == null) {
            XRAY_BOXES = ObservableRenderTypeAccessor.callCreate(
                "observable_xray_boxes",
                RenderSetup.builder(
                    com.mojang.blaze3d.pipeline.RenderPipeline.builder(ObservableRenderPipelinesAccessor.getDebugFilledSnippet())
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(
                    Optional.of(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT),
                    com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_ALL
                ))
                .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("observable", "xray_boxes"))
                .build()
                ).createRenderSetup()
            );
        }
        return XRAY_BOXES;
    }
}
