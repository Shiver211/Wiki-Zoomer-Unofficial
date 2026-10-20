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

    @Config.Comment("If true, items with subtypes will include the metadata suffix in their exported file name (e.g. wool_0.png, wool_1.png). If false, items with metadata 0 will not have a metadata suffix (e.g. wool.png, wool_1.png).")
    @Config.Name("Append Metadata Suffix For Subtypes")
    public static boolean appendMetadataSuffixForSubtypes = true;
}
