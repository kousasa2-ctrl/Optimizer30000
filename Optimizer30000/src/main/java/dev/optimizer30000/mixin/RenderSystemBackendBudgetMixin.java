package dev.optimizer30000.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.optimizer30000.Optimizer30000Mod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Executor;

@Mixin(RenderSystem.class)
public abstract class RenderSystemBackendBudgetMixin {
    @Inject(method = "getBackendExecutor", at = @At("RETURN"), cancellable = true, require = 0)
    private static void optimizer30000$limitBackendExecutor(CallbackInfoReturnable<Executor> cir) {
        cir.setReturnValue(Optimizer30000Mod.threadBudgetGovernor().limitBackendExecutor(cir.getReturnValue()));
    }
}
