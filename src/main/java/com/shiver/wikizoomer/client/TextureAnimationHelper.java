package com.shiver.wikizoomer.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class TextureAnimationHelper {
    private static final int MAX_PERIOD_TICKS = 600;

    public static boolean isItemAnimated(ItemStack stack) {
        return !getAnimatedSprites(stack).isEmpty();
    }

    private static Set<TextureAtlasSprite> getAnimatedSprites(ItemStack stack) {
        Set<TextureAtlasSprite> sprites = new LinkedHashSet<>();
        if (stack.isEmpty()) return sprites;
        Minecraft mc = Minecraft.getInstance();
        CollectingRenderState state = new CollectingRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, mc.level, null, 0);
        for (var layer : state.collectedLayers) {
            for (BakedQuad quad : layer.prepareQuadList()) {
                addSprite(quad.materialInfo().sprite(), sprites);
            }
        }
        Material.Baked particle = state.pickParticleMaterial(RandomSource.create(42));
        if (particle != null) addSprite(particle.sprite(), sprites);
        return sprites;
    }

    private static void addSprite(TextureAtlasSprite sprite, Set<TextureAtlasSprite> sprites) {
        if (sprite.isAnimated()) sprites.add(sprite);
    }

    private static class CollectingRenderState extends TrackingItemStackRenderState {
        private final List<LayerRenderState> collectedLayers = new ArrayList<>();

        @Override
        public LayerRenderState newLayer() {
            LayerRenderState layer = super.newLayer();
            collectedLayers.add(layer);
            return layer;
        }
    }

    private static int getPeriodTicks(TextureAtlasSprite sprite) throws IOException {
        Identifier name = sprite.contents().name();
        Identifier texture = Identifier.fromNamespaceAndPath(name.getNamespace(), "textures/" + name.getPath() + ".png");
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(texture).orElse(null);
        AnimationMetadataSection metadata = resource == null ? null
                : resource.metadata().getSection(AnimationMetadataSection.TYPE).orElse(null);
        if (metadata == null) return Math.max(1, sprite.contents().getUniqueFrames().size());
        if (metadata.frames().isPresent() && !metadata.frames().get().isEmpty()) {
            return metadata.frames().get().stream().mapToInt(frame -> frame.timeOr(metadata.defaultFrameTime())).sum();
        }
        NativeImage image = sprite.contents().getOriginalImage();
        int frames = (image.getWidth() / sprite.contents().width()) * (image.getHeight() / sprite.contents().height());
        return Math.max(1, frames * metadata.defaultFrameTime());
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
                capture.animations.add(new SpriteAnimation(sprite));
                capture.periodTicks = lcm(capture.periodTicks, getPeriodTicks(sprite));
            }
            for (SpriteAnimation animation : capture.animations) animation.upload();
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
                animation.state.tick();
                animation.upload();
            }
        }

        @Override
        public void close() {
            for (SpriteAnimation animation : animations) animation.close();
        }
    }

    private static class SpriteAnimation implements AutoCloseable {
        private final TextureAtlasSprite sprite;
        private final GpuTexture atlas;
        private final GpuBuffer[] savedMipPixels;
        private final int paddedWidth;
        private final int paddedHeight;
        private final GpuTextureView[] atlasMipViews;
        private final GpuBuffer uniforms;
        private final SpriteContents.AnimationState state;

        private SpriteAnimation(TextureAtlasSprite sprite) {
            this.sprite = sprite;
            GpuDevice device = RenderSystem.getDevice();
            this.atlas = Minecraft.getInstance().getTextureManager().getTexture(sprite.atlasLocation()).getTexture();
            int levels = atlas.getMipLevels();
            int padding = Math.round(sprite.getU0() * atlas.getWidth(0)) - sprite.getX();
            this.paddedWidth = sprite.contents().width() + padding * 2;
            this.paddedHeight = sprite.contents().height() + padding * 2;
            this.savedMipPixels = new GpuBuffer[levels];
            this.atlasMipViews = new GpuTextureView[levels];
            CommandEncoder encoder = device.createCommandEncoder();
            // 26.2 的 OpenGL 纹理间复制忽略 mipLevel，改用缓冲区逐层保存含 padding 的像素。
            for (int level = 0; level < levels; level++) {
                int width = paddedWidth >> level;
                int height = paddedHeight >> level;
                savedMipPixels[level] = device.createBuffer(() -> "wikizoomer saved sprite pixels",
                        GpuBuffer.USAGE_COPY_SRC | GpuBuffer.USAGE_COPY_DST, (long) width * height * atlas.getFormat().blockSize());
                encoder.copyTextureToBuffer(atlas, savedMipPixels[level], 0L, () -> {}, level,
                        sprite.getX() >> level, sprite.getY() >> level, width, height);
                atlasMipViews[level] = device.createTextureView(atlas, level, 1);
            }
            int uniformSize = Mth.roundToward(SpriteContents.UBO_SIZE, device.getDeviceInfo().limits().minUniformOffsetAlignment());
            ByteBuffer buffer = MemoryUtil.memAlloc(uniformSize * levels);
            try {
                sprite.uploadSpriteUbo(buffer, 0, levels - 1, atlas.getWidth(0), atlas.getHeight(0), uniformSize);
                this.uniforms = device.createBuffer(() -> "wikizoomer sprite animation uniforms", GpuBuffer.USAGE_UNIFORM, buffer);
            } finally {
                MemoryUtil.memFree(buffer);
            }
            // 原版状态保留帧顺序、帧时长和 GPU 插值，游戏内动画计数器独立运行。
            this.state = sprite.createAnimationState(uniforms.slice(), uniformSize);
        }

        private void upload() {
            if (!state.needsToDraw()) return;
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            for (int level = 0; level < atlasMipViews.length; level++) {
                try (RenderPass pass = encoder.createRenderPass(() -> "wikizoomer sprite animation", atlasMipViews[level], Optional.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    state.drawToAtlas(pass, state.getDrawUbo(level));
                }
            }
        }

        @Override
        public void close() {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            for (int level = 0; level < atlasMipViews.length; level++) {
                int width = paddedWidth >> level;
                int height = paddedHeight >> level;
                encoder.copyBufferToTexture(savedMipPixels[level].slice(), 0, 0, width, height, atlas,
                        sprite.getX() >> level, sprite.getY() >> level, width, height, level, 0);
                savedMipPixels[level].close();
                atlasMipViews[level].close();
            }
            state.close();
            uniforms.close();
        }
    }
}
