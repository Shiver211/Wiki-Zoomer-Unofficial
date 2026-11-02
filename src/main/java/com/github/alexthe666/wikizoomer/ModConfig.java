package com.github.alexthe666.wikizoomer;

import net.minecraftforge.common.ForgeConfigSpec;

public class ModConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue EXPORT_ANIMATED_TEXTURES_AS_GIF;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        EXPORT_ANIMATED_TEXTURES_AS_GIF = builder
                .comment("Export items with animated textures as looping GIF files instead of PNG.")
                .define("exportAnimatedTexturesAsGif", true);
        SPEC = builder.build();
    }
}
