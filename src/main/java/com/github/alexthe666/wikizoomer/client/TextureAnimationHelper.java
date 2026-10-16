package com.github.alexthe666.wikizoomer.client;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SideOnly(Side.CLIENT)
public class TextureAnimationHelper {

    private static final Logger LOGGER = LogManager.getLogger();

    private static Field animationMetadataField;
    private static Field frameCounterField;
    private static Field tickCounterField;
    private static boolean reflectionInitialized = false;

    private static synchronized void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        animationMetadataField = findFieldSafely(TextureAtlasSprite.class, "field_110982_k", "animationMetadata");
        frameCounterField = findFieldSafely(TextureAtlasSprite.class, "field_110973_g", "frameCounter");
        tickCounterField = findFieldSafely(TextureAtlasSprite.class, "field_110983_h", "tickCounter");
    }

    private static Field findFieldSafely(Class<?> clazz, String srgName, String mcpName) {
        Field field = null;
        try {
            field = ObfuscationReflectionHelper.findField(clazz, srgName);
        } catch (Throwable t1) {
            try {
                field = clazz.getDeclaredField(mcpName);
            } catch (Throwable t2) {
                LOGGER.warn("Could not find field {} ({}) on {}", srgName, mcpName, clazz.getName());
            }
        }
        if (field != null) {
            field.setAccessible(true);
        }
        return field;
    }

    public static boolean isItemAnimated(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Set<TextureAtlasSprite> sprites = getAnimatedSprites(stack);
        return !sprites.isEmpty() && getTotalPeriodTicks(sprites) > 1;
    }

    public static Set<TextureAtlasSprite> getAnimatedSprites(ItemStack stack) {
        Set<TextureAtlasSprite> sprites = new LinkedHashSet<>();
        if (stack == null || stack.isEmpty()) {
            return sprites;
        }
        Minecraft mc = Minecraft.getMinecraft();
        RenderItem renderItem = mc.getRenderItem();
        IBakedModel model = null;
        try {
            model = renderItem.getItemModelWithOverrides(stack, null, mc.player);
        } catch (Throwable ignored) {
        }
        if (model == null) {
            return sprites;
        }

        collectSpritesFromModel(model, stack, sprites);

        try {
            IBakedModel guiModel = ForgeHooksClient.handleCameraTransforms(model, ItemCameraTransforms.TransformType.GUI, false);
            if (guiModel != null && guiModel != model) {
                collectSpritesFromModel(guiModel, stack, sprites);
            }
        } catch (Throwable ignored) {
        }

        return sprites;
    }

    private static void collectSpritesFromModel(IBakedModel model, ItemStack stack, Set<TextureAtlasSprite> sprites) {
        if (model == null) return;

        boolean foundQuadSprite = false;

        try {
            List<BakedQuad> generalQuads = model.getQuads(null, null, 0L);
            if (generalQuads != null) {
                for (BakedQuad quad : generalQuads) {
                    if (quad != null && quad.getSprite() != null) {
                        foundQuadSprite = true;
                        if (quad.getSprite().hasAnimationMetadata()) {
                            sprites.add(quad.getSprite());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        for (EnumFacing facing : EnumFacing.values()) {
            try {
                List<BakedQuad> faceQuads = model.getQuads(null, facing, 0L);
                if (faceQuads != null) {
                    for (BakedQuad quad : faceQuads) {
                        if (quad != null && quad.getSprite() != null) {
                            foundQuadSprite = true;
                            if (quad.getSprite().hasAnimationMetadata()) {
                                sprites.add(quad.getSprite());
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (stack.getItem() instanceof ItemBlock) {
            try {
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                IBlockState state = block.getStateFromMeta(stack.getMetadata());
                List<BakedQuad> stateGeneral = model.getQuads(state, null, 0L);
                if (stateGeneral != null) {
                    for (BakedQuad quad : stateGeneral) {
                        if (quad != null && quad.getSprite() != null) {
                            foundQuadSprite = true;
                            if (quad.getSprite().hasAnimationMetadata()) {
                                sprites.add(quad.getSprite());
                            }
                        }
                    }
                }
                for (EnumFacing facing : EnumFacing.values()) {
                    List<BakedQuad> stateFace = model.getQuads(state, facing, 0L);
                    if (stateFace != null) {
                        for (BakedQuad quad : stateFace) {
                            if (quad != null && quad.getSprite() != null) {
                                foundQuadSprite = true;
                                if (quad.getSprite().hasAnimationMetadata()) {
                                    sprites.add(quad.getSprite());
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // If no quads had sprites, fallback to particle texture
        if (!foundQuadSprite) {
            try {
                TextureAtlasSprite particle = model.getParticleTexture();
                if (particle != null && particle.hasAnimationMetadata()) {
                    sprites.add(particle);
                }
            } catch (Throwable ignored) {}
        }
    }

    public static AnimationMetadataSection getAnimationMetadata(TextureAtlasSprite sprite) {
        if (sprite == null || !sprite.hasAnimationMetadata()) return null;
        initReflection();
        if (animationMetadataField != null) {
            try {
                return (AnimationMetadataSection) animationMetadataField.get(sprite);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static int getFrameCounter(TextureAtlasSprite sprite) {
        initReflection();
        if (frameCounterField != null && sprite != null) {
            try {
                return frameCounterField.getInt(sprite);
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    public static void setFrameCounter(TextureAtlasSprite sprite, int value) {
        initReflection();
        if (frameCounterField != null && sprite != null) {
            try {
                frameCounterField.setInt(sprite, value);
            } catch (Throwable ignored) {}
        }
    }

    public static int getTickCounter(TextureAtlasSprite sprite) {
        initReflection();
        if (tickCounterField != null && sprite != null) {
            try {
                return tickCounterField.getInt(sprite);
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    public static void setTickCounter(TextureAtlasSprite sprite, int value) {
        initReflection();
        if (tickCounterField != null && sprite != null) {
            try {
                tickCounterField.setInt(sprite, value);
            } catch (Throwable ignored) {}
        }
    }

    public static int getSpritePeriodTicks(TextureAtlasSprite sprite) {
        AnimationMetadataSection meta = getAnimationMetadata(sprite);
        if (meta == null) return 1;
        int count = meta.getFrameCount();
        if (count == 0) {
            count = sprite.getFrameCount();
        }
        if (count <= 0) return 1;

        int totalTicks = 0;
        for (int i = 0; i < count; i++) {
            totalTicks += meta.getFrameTimeSingle(i);
        }
        return totalTicks > 0 ? totalTicks : 1;
    }

    public static int getTotalPeriodTicks(Collection<TextureAtlasSprite> sprites) {
        if (sprites == null || sprites.isEmpty()) return 1;
        int lcm = 1;
        for (TextureAtlasSprite sprite : sprites) {
            int p = getSpritePeriodTicks(sprite);
            lcm = lcm(lcm, p);
            if (lcm >= 600) {
                return 600;
            }
        }
        return lcm;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    private static int lcm(int a, int b) {
        if (a <= 0 || b <= 0) return 1;
        long product = (long) a * b;
        long gcdVal = gcd(a, b);
        long res = product / gcdVal;
        return (int) Math.min(res, 600);
    }

    public static void uploadFrame(TextureAtlasSprite sprite, int frameIndex) {
        if (sprite == null) return;
        int frameCount = sprite.getFrameCount();
        if (frameCount <= 0) return;
        if (frameIndex < 0 || frameIndex >= frameCount) {
            frameIndex = 0;
        }
        int[][] data = sprite.getFrameTextureData(frameIndex);
        if (data != null) {
            TextureUtil.uploadTextureMipmap(data, sprite.getIconWidth(), sprite.getIconHeight(), sprite.getOriginX(), sprite.getOriginY(), false, false);
        }
    }

    public static void uploadInitialFrame(TextureAtlasSprite sprite) {
        AnimationMetadataSection meta = getAnimationMetadata(sprite);
        int initialIndex = (meta != null && meta.getFrameCount() > 0) ? meta.getFrameIndex(0) : 0;
        uploadFrame(sprite, initialIndex);
    }
}

