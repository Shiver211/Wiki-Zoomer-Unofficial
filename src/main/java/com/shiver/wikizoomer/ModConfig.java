package com.shiver.wikizoomer;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue EXPORT_ANIMATED_TEXTURES_AS_GIF;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        EXPORT_ANIMATED_TEXTURES_AS_GIF = builder
                .comment("Export items with animated textures as looping GIF files instead of PNG.")
                .define("exportAnimatedTexturesAsGif", true);
        SPEC = builder.build();
    }
}
