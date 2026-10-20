package com.github.alexthe666.wikizoomer.client;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
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
    private static Field listAnimatedSpritesField;
    private static boolean reflectionInitialized = false;

    private static synchronized void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        animationMetadataField = findFieldSafely(TextureAtlasSprite.class, "field_110982_k", "animationMetadata");
        frameCounterField = findFieldSafely(TextureAtlasSprite.class, "field_110973_g", "frameCounter");
        tickCounterField = findFieldSafely(TextureAtlasSprite.class, "field_110983_h", "tickCounter");
        listAnimatedSpritesField = findFieldSafely(TextureMap.class, "field_94258_i", "listAnimatedSprites");
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

        GlStateManager.pushMatrix();
        try {
            IBakedModel guiModel = ForgeHooksClient.handleCameraTransforms(model, ItemCameraTransforms.TransformType.GUI, false);
            if (guiModel != null && guiModel != model) {
                collectSpritesFromModel(guiModel, stack, sprites);
            }
        } catch (Throwable ignored) {
        } finally {
            GlStateManager.popMatrix();
        }

        return sprites;
    }

    public static boolean isSpriteAnimated(TextureAtlasSprite sprite) {
        if (sprite == null) return false;
        if (sprite.hasAnimationMetadata()) return true;
        if (sprite.getFrameCount() > 1) return true;
        AnimationMetadataSection meta = getAnimationMetadata(sprite);
        return meta != null && meta.getFrameCount() > 1;
    }

    private static List<TextureAtlasSprite> getTextureMapAnimatedSprites() {
        initReflection();
        if (listAnimatedSpritesField != null) {
            try {
                TextureMap map = Minecraft.getMinecraft().getTextureMapBlocks();
                if (map != null) {
                    @SuppressWarnings("unchecked")
                    List<TextureAtlasSprite> list = (List<TextureAtlasSprite>) listAnimatedSpritesField.get(map);
                    if (list != null) {
                        return list;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return java.util.Collections.emptyList();
    }

    private static void checkAndAddQuadSprite(BakedQuad quad, Set<TextureAtlasSprite> sprites) {
        if (quad == null) return;
        TextureAtlasSprite quadSprite = quad.getSprite();
        if (quadSprite != null) {
            if (isSpriteAnimated(quadSprite)) {
                sprites.add(quadSprite);
            }
            return;
        }

        int[] vertexData = quad.getVertexData();
        if (vertexData == null || vertexData.length < 28) return;

        List<TextureAtlasSprite> animatedList = getTextureMapAnimatedSprites();
        if (animatedList.isEmpty()) return;

        int step = vertexData.length / 4;
        if (step < 7) step = 7;

        for (int v = 0; v < 4; v++) {
            int uIdx = v * step + 4;
            int vIdx = v * step + 5;
            if (vIdx >= vertexData.length) break;

            float u = Float.intBitsToFloat(vertexData[uIdx]);
            float vCoord = Float.intBitsToFloat(vertexData[vIdx]);

            for (TextureAtlasSprite animSprite : animatedList) {
                if (animSprite == null) continue;
                if (u >= animSprite.getMinU() - 0.0005F && u <= animSprite.getMaxU() + 0.0005F
                        && vCoord >= animSprite.getMinV() - 0.0005F && vCoord <= animSprite.getMaxV() + 0.0005F) {
                    if (isSpriteAnimated(animSprite)) {
                        sprites.add(animSprite);
                    }
                }
            }
        }
    }

    private static void collectSpritesFromQuads(IBakedModel model, IBlockState state, Set<TextureAtlasSprite> sprites) {
        if (model == null) return;
        try {
            List<BakedQuad> generalQuads = model.getQuads(state, null, 0L);
            if (generalQuads != null) {
                for (BakedQuad quad : generalQuads) {
                    checkAndAddQuadSprite(quad, sprites);
                }
            }
        } catch (Throwable ignored) {}

        for (EnumFacing facing : EnumFacing.values()) {
            try {
                List<BakedQuad> faceQuads = model.getQuads(state, facing, 0L);
                if (faceQuads != null) {
                    for (BakedQuad quad : faceQuads) {
                        checkAndAddQuadSprite(quad, sprites);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void collectSpritesFromModel(IBakedModel model, ItemStack stack, Set<TextureAtlasSprite> sprites) {
        if (model == null) return;

        collectSpritesFromQuads(model, null, sprites);

        try {
            TextureAtlasSprite particle = model.getParticleTexture();
            if (isSpriteAnimated(particle)) {
                sprites.add(particle);
            }
        } catch (Throwable ignored) {}

        if (stack.getItem() instanceof ItemBlock) {
            try {
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                IBlockState state = null;
                try {
                    state = block.getStateFromMeta(ExportManager.getEffectiveMetadata(stack));
                } catch (Throwable ignored) {}
                if (state == null) {
                    try {
                        state = block.getDefaultState();
                    } catch (Throwable ignored) {}
                }

                if (state != null) {
                    collectSpritesFromQuads(model, state, sprites);

                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc != null && mc.getBlockRendererDispatcher() != null) {
                        try {
                            IBakedModel blockModel = mc.getBlockRendererDispatcher().getModelForState(state);
                            if (blockModel != null && blockModel != model) {
                                collectSpritesFromQuads(blockModel, state, sprites);
                                collectSpritesFromQuads(blockModel, null, sprites);
                                TextureAtlasSprite p = blockModel.getParticleTexture();
                                if (isSpriteAnimated(p)) {
                                    sprites.add(p);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
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
        if (sprite == null) return 1;
        AnimationMetadataSection meta = getAnimationMetadata(sprite);
        if (meta == null) {
            return sprite.getFrameCount() > 1 ? sprite.getFrameCount() : 1;
        }
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

    public static void updateSpriteAnimation(TextureAtlasSprite sprite) {
        if (sprite == null) return;
        if (sprite.hasAnimationMetadata()) {
            try {
                sprite.updateAnimation();
                return;
            } catch (Throwable ignored) {}
        }
        int frameCount = sprite.getFrameCount();
        if (frameCount > 1) {
            int fc = getFrameCounter(sprite);
            int tc = getTickCounter(sprite) + 1;
            setTickCounter(sprite, tc);
            if (tc >= 1) {
                fc = (fc + 1) % frameCount;
                setFrameCounter(sprite, fc);
                setTickCounter(sprite, 0);
                uploadFrame(sprite, fc);
            }
        }
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

