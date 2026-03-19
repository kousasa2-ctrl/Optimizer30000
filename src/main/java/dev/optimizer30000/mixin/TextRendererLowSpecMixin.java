package dev.optimizer30000.mixin;

import dev.optimizer30000.runtime.RenderBudgetState;
import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Выключает тени текста на HD 3000.
 *
 * Это уменьшает количество draw calls / повторных проходов при отрисовке UI,
 * особенно в меню, чате и на экранах с большим объемом текста.
 */
@Mixin(TextRenderer.class)
public abstract class TextRendererLowSpecMixin {
    @ModifyVariable(
            method = "draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private boolean optimizer30000$disableShadowForString(boolean shadow) {
        return RenderBudgetState.disableTextShadows() ? false : shadow;
    }

    @ModifyVariable(
            method = "draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private boolean optimizer30000$disableShadowForOrderedText(boolean shadow) {
        return RenderBudgetState.disableTextShadows() ? false : shadow;
    }
}
