package com.github.alexthe666.wikizoomer;

import net.minecraftforge.common.config.Config;

@Config(modid = "wikizoomer")
public class ModConfig {

    @Config.Comment("If true, batch export will only include EntityLivingBase subclasses (mobs, animals, etc.), excluding items, projectiles, vehicles, and other non-living entities.")
    @Config.Name("Only Living Entities")
    public static boolean onlyLivingEntities = false;

    @Config.Comment("If true, items with animated textures will be exported as animated GIF files instead of static PNG.")
    @Config.Name("Export Animated Textures As GIF")
    public static boolean exportAnimatedTexturesAsGif = true;
}
