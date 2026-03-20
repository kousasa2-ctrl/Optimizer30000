package dev.optimizer30000;

import dev.optimizer30000.config.OptimizationProfile;
import dev.optimizer30000.runtime.RenderBudgetState;
import dev.optimizer30000.runtime.ThreadBudgetGovernor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Optimizer30000Mod implements ClientModInitializer {
    public static final String MOD_ID = "optimizer30000";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static final OptimizationProfile PROFILE = OptimizationProfile.lowEndLaptop2011();
    private static final ThreadBudgetGovernor THREAD_BUDGET_GOVERNOR = new ThreadBudgetGovernor(PROFILE);
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        RenderBudgetState.configure(
                PROFILE.legacyGpuMode(), PROFILE.disablePostProcessing(),
                PROFILE.aggressiveEntityCulling(), PROFILE.disableTextShadows(),
                PROFILE.simplifyLightmapUpdates(), PROFILE.lazyDfu(),
                PROFILE.entityDistanceBlocks(), PROFILE.blockEntityDistanceBlocks(),
                PROFILE.translucentResortDistance()
        );

        checkConflicts();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.optimizer30000.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "category.optimizer30000"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                OptimizerConfig.enabled = !OptimizerConfig.enabled;
                if (client.player != null) {
                    String status = OptimizerConfig.enabled ? "§aВКЛ" : "§cВЫКЛ";
                    client.player.sendMessage(Text.literal("§6Optimizer30000: " + status), true);
                }
            }
        });
    }

    // ЭТОТ МЕТОД НУЖЕН МИКСИНАМ (ИСПРАВЛЯЕТ ТВОЮ ОШИБКУ)
    public static ThreadBudgetGovernor threadBudgetGovernor() {
        return THREAD_BUDGET_GOVERNOR;
    }

    private void checkConflicts() {
        StringBuilder conflicts = new StringBuilder();
        String[] mods = {"sodium", "sodium-extra", "lithium", "fpsreducer"};
        for (String mod : mods) {
            if (FabricLoader.getInstance().isModLoaded(mod)) {
                conflicts.append(mod).append(", ");
            }
        }
        if (conflicts.length() > 0) {
            OptimizerConfig.conflictMessage = "§cКонфликты: §f" + conflicts.substring(0, conflicts.length() - 2);
        }
    }
}