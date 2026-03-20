package dev.optimizer30000.mixin;

import dev.optimizer30000.runtime.RenderBudgetState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Графический даунгрейд для Intel HD 3000.
 *
 * Здесь мы агрессивно отключаем весь post-processing path, который создает лишние
 * shader / framebuffer проходы, и урезаем частоту обновления lightmap до 1 раза
 * в 3 тика, чтобы снизить нагрузку на старый iGPU.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererLowSpecMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private LightmapTextureManager lightmapTextureManager;
    @Shadow private boolean postProcessorEnabled;

    @Shadow public abstract void disablePostProcessor();
    @Shadow public abstract Camera getCamera();

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"))
    private void optimizer30000$publishCamera(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!RenderBudgetState.legacyGpuMode()) {
            return;
        }

        RenderBudgetState.beginFrame();
        Vec3d pos = this.getCamera().getPos();
        RenderBudgetState.updateCamera(pos.x, pos.y, pos.z);

        if (RenderBudgetState.postProcessingDisabled() && this.postProcessorEnabled) {
            this.postProcessorEnabled = false;
            this.disablePostProcessor();
        }
    }

    @Inject(method = "getPostProcessor", at = @At("HEAD"), cancellable = true)
    private void optimizer30000$disablePostProcessorLookup(CallbackInfoReturnable<PostEffectProcessor> cir) {
        if (RenderBudgetState.postProcessingDisabled()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "togglePostProcessorEnabled", at = @At("HEAD"), cancellable = true)
    private void optimizer30000$disablePostProcessorToggle(CallbackInfo ci) {
        if (!RenderBudgetState.postProcessingDisabled()) {
            return;
        }

        this.postProcessorEnabled = false;
        ci.cancel();
    }

    @Inject(method = "loadPostProcessor", at = @At("HEAD"), cancellable = true)
    private void optimizer30000$cancelPostProcessorLoad(Identifier id, CallbackInfo ci) {
        if (!RenderBudgetState.postProcessingDisabled()) {
            return;
        }

        this.postProcessorEnabled = false;
        ci.cancel();
    }

    @Inject(method = "loadBlurPostProcessor", at = @At("HEAD"), cancellable = true)
    private void optimizer30000$cancelBlurPostProcessorLoad(ResourceFactory resourceFactory, CallbackInfo ci) {
        if (RenderBudgetState.postProcessingDisabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBlur", at = @At("HEAD"), cancellable = true)
    private void optimizer30000$cancelBlurRender(float delta, CallbackInfo ci) {
        if (RenderBudgetState.postProcessingDisabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onResized", at = @At("TAIL"))
    private void optimizer30000$disablePostEffectsAfterResize(int width, int height, CallbackInfo ci) {
        if (RenderBudgetState.postProcessingDisabled() && this.postProcessorEnabled) {
            this.postProcessorEnabled = false;
            this.disablePostProcessor();
        }
    }

    @Redirect(
            method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/LightmapTextureManager;tick()V")
    )
    private void optimizer30000$throttleLightmapTick(LightmapTextureManager instance) {
        if (!RenderBudgetState.simplifyLightmapUpdates()) {
            this.lightmapTextureManager.tick();
            return;
        }

        if (this.client.world != null && this.client.world.getTime() % 3L == 0L) {
            // Меньше вызовов tick() => меньше апдейтов lightmap texture и меньше
            // нагрузки на Intel HD 3000 при сохранении приемлемой визуальной стабильности.
            this.lightmapTextureManager.tick();
        }
    }
}
