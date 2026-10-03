package dev.livingrealms.minecraft.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

/** Client-only key mappings. */
public final class LivingRealmsKeyMappings {
    public static final String CATEGORY = "key.categories.livingrealms";
    public static final Lazy<KeyMapping> OPEN_DASHBOARD = Lazy.of(() -> new KeyMapping(
            "key.livingrealms.open_dashboard",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F12,
            CATEGORY
    ));
    public static final Lazy<KeyMapping> OPEN_WORLD_MAP = Lazy.of(() -> new KeyMapping(
            "key.livingrealms.open_world_map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY
    ));
    public static final Lazy<KeyMapping> OPEN_CREATIVE_CATALOG = Lazy.of(() -> new KeyMapping(
            "key.livingrealms.open_creative_catalog",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    ));

    private LivingRealmsKeyMappings() {}
}
