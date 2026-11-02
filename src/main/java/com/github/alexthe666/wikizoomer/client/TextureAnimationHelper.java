package com.github.alexthe666.wikizoomer.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteTicker;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class TextureAnimationHelper {
    private static final int MAX_PERIOD_TICKS = 600;

    public static boolean isItemAnimated(ItemStack stack) {
        return !getAnimatedSprites(stack).isEmpty();
    }

    private static Set<TextureAtlasSprite> getAnimatedSprites(ItemStack stack) {
        Set<TextureAtlasSprite> sprites = new LinkedHashSet<>();
        if (stack.isEmpty()) {
            return sprites;
        }
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
        for (BakedModel pass : model.getRenderPasses(stack, false)) {
            collectSprites(pass, null, sprites);
            BakedModel guiModel = pass.applyTransform(ItemDisplayContext.GUI, new PoseStack(), false);
            if (guiModel != pass) {
                collectSprites(guiModel, null, sprites);
            }
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            collectSprites(mc.getBlockRenderer().getBlockModel(state), state, sprites);
        }
        return sprites;
    }

    private static void collectSprites(BakedModel model, BlockState state, Set<TextureAtlasSprite> sprites) {
        addSprite(model.getParticleIcon(), sprites);
        collectQuads(model.getQuads(state, null, RandomSource.create(42)), sprites);
        for (Direction face : Direction.values()) {
            collectQuads(model.getQuads(state, face, RandomSource.create(42)), sprites);
        }
    }

    private static void collectQuads(List<BakedQuad> quads, Set<TextureAtlasSprite> sprites) {
        for (BakedQuad quad : quads) {
            addSprite(quad.getSprite(), sprites);
        }
    }

    private static void addSprite(TextureAtlasSprite sprite, Set<TextureAtlasSprite> sprites) {
        if (sprite != null && sprite.contents().getUniqueFrames().count() > 1) {
            sprites.add(sprite);
        }
    }

    private static int getPeriodTicks(TextureAtlasSprite sprite) throws IOException {
        ResourceLocation name = sprite.contents().name();
        ResourceLocation texture = ResourceLocation.tryParse(name.getNamespace() + ":textures/" + name.getPath() + ".png");
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(texture).orElse(null);
        AnimationMetadataSection metadata = resource == null ? AnimationMetadataSection.EMPTY
                : resource.metadata().getSection(AnimationMetadataSection.SERIALIZER).orElse(AnimationMetadataSection.EMPTY);
        int[] period = {0};
        metadata.forEachFrame((index, time) -> period[0] += time);
        if (period[0] == 0) {
            NativeImage image = sprite.contents().getOriginalImage();
            int frames = (image.getWidth() / sprite.contents().width()) * (image.getHeight() / sprite.contents().height());
            period[0] = frames * metadata.getDefaultFrameTime();
        }
        return Math.max(1, period[0]);
    }

    private static int lcm(int a, int b) {
        int x = a;
        int y = b;
        while (y != 0) {
            int remainder = x % y;
            x = y;
            y = remainder;
        }
        return (int) Math.min((long) a / x * b, MAX_PERIOD_TICKS);
    }

    public static Capture beginCapture(ItemStack stack) throws IOException {
        Capture capture = new Capture();
        try {
            for (TextureAtlasSprite sprite : getAnimatedSprites(stack)) {
                SpriteTicker ticker = sprite.contents().createTicker();
                if (ticker != null) {
                    capture.animations.add(new SpriteAnimation(sprite, ticker));
                    capture.periodTicks = lcm(capture.periodTicks, getPeriodTicks(sprite));
                }
            }
            for (SpriteAnimation animation : capture.animations) {
                animation.bindTexture();
                animation.sprite.uploadFirstFrame();
            }
            return capture;
        } catch (IOException | RuntimeException e) {
            capture.close();
            throw e;
        }
    }

    public static class Capture implements AutoCloseable {
        private final List<SpriteAnimation> animations = new ArrayList<>();
        private int periodTicks = 1;

        public int periodTicks() {
            return periodTicks;
        }

        public void advance() {
            for (SpriteAnimation animation : animations) {
                animation.bindTexture();
                animation.ticker.tickAndUpload(animation.sprite.getX(), animation.sprite.getY());
            }
        }

        @Override
        public void close() {
            for (SpriteAnimation animation : animations) {
                animation.close();
            }
        }
    }

    private static class SpriteAnimation implements AutoCloseable {
        private final TextureAtlasSprite sprite;
        private final SpriteTicker ticker;
        private final AbstractTexture atlas;
        private final List<ByteBuffer> savedMipPixels = new ArrayList<>();

        private SpriteAnimation(TextureAtlasSprite sprite, SpriteTicker ticker) {
            this.sprite = sprite;
            this.ticker = ticker;
            this.atlas = Minecraft.getInstance().getTextureManager().getTexture(sprite.atlasLocation());
            int previousReadTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int readTarget = GL30.glGenFramebuffers();
            try {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                // 只备份当前材质区域；独立 ticker 不改变游戏内动画的计数器。
                for (int level = 0; level < sprite.contents().byMipLevel.length; level++) {
                    int width = sprite.contents().width() >> level;
                    int height = sprite.contents().height() >> level;
                    if (width == 0 || height == 0) break;
                    ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
                    GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, atlas.getId(), level);
                    GL11.glReadPixels(sprite.getX() >> level, sprite.getY() >> level, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                    savedMipPixels.add(pixels);
                }
            } finally {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadTarget);
                GL30.glDeleteFramebuffers(readTarget);
            }
        }

        private void bindTexture() {
            atlas.bind();
        }

        @Override
        public void close() {
            bindTexture();
            for (int level = 0; level < savedMipPixels.size(); level++) {
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, sprite.getX() >> level, sprite.getY() >> level,
                        sprite.contents().width() >> level, sprite.contents().height() >> level,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, savedMipPixels.get(level));
            }
            ticker.close();
        }
    }
}
