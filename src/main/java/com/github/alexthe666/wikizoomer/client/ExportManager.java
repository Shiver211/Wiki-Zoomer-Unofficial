package com.github.alexthe666.wikizoomer.client;

import com.github.alexthe666.wikizoomer.ModConfig;
import com.github.alexthe666.wikizoomer.client.gif.AnimatedGifEncoder;
import com.github.alexthe666.wikizoomer.client.gif.GifFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SideOnly(Side.CLIENT)
public class ExportManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int DEFAULT_EXPORT_SIZE = 512;
    private static final int[] EXPORT_SIZES = new int[]{64, 128, 256, 512, 1024};
    private static final float DEFAULT_ZOOM = 100F;
    public static final float ITEM_BASE_SCALE = 16F;
    private static final Queue<ExportTask> QUEUE = new ArrayDeque<>();
    private static Framebuffer framebuffer;
    private static int batchRemaining = 0;
    private static boolean isBatchRunning = false;
    private static IntBuffer pixelBuffer;
    private static int[] pixelValues;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)),
            r -> {
                Thread t = new Thread(r, "WikiZoomer-ExportWorker");
                t.setDaemon(true);
                return t;
            }
    );
    private static final AtomicInteger ACTIVE_TASKS = new AtomicInteger(0);

    public static float getDefaultZoom() {
        return DEFAULT_ZOOM;
    }

    public static int getDefaultExportSize() {
        return DEFAULT_EXPORT_SIZE;
    }

    public static int[] getExportSizes() {
        return EXPORT_SIZES.clone();
    }

    public static boolean isItemAnimated(ItemStack stack) {
        return TextureAnimationHelper.isItemAnimated(stack);
    }

    public static void enqueue(ExportTask task) {
        if (task != null) {
            QUEUE.add(task);
        }
    }

    public static void enqueueBatch(List<ExportTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        QUEUE.addAll(tasks);
        batchRemaining += tasks.size();
        isBatchRunning = true;
        sendChat(I18n.format("gui.wikizoomer.batch_started", tasks.size()));
    }

    public static ExportTask createItemTask(ItemStack stack, float zoomPercent, ExportTask.Background background, int exportSize, boolean isBatch) {
        return createItemTask(stack, zoomPercent, background, exportSize, isBatch, 0F, 0F, false);
    }

    public static ExportTask createItemTask(ItemStack stack, float zoomPercent, ExportTask.Background background, int exportSize, boolean isBatch, float rotX, float rotY) {
        return createItemTask(stack, zoomPercent, background, exportSize, isBatch, rotX, rotY, false);
    }

    public static ExportTask createItemTask(ItemStack stack, float zoomPercent, ExportTask.Background background, int exportSize, boolean isBatch, float rotX, float rotY, boolean forcePng) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = stack.getItem().getRegistryName();
        if (id == null) {
            return null;
        }
        boolean isGif = !forcePng && ModConfig.exportAnimatedTexturesAsGif && TextureAnimationHelper.isItemAnimated(stack);
        File output = getOutputFile(id, isGif);
        return ExportTask.forItem(stack, output, background, isBatch, zoomPercent, exportSize, rotX, rotY, isGif);
    }

    public static ExportTask createEntityTask(Entity entity, float zoomPercent, ExportTask.Background background, int exportSize, boolean isBatch, int offsetX, int offsetY) {
        return createEntityTask(entity, zoomPercent, background, exportSize, isBatch, -30F, 45F, offsetX, offsetY);
    }

    public static ExportTask createEntityTask(Entity entity, float zoomPercent, ExportTask.Background background, int exportSize, boolean isBatch, float rotX, float rotY, int offsetX, int offsetY) {
        if (entity == null) {
            return null;
        }
        ResourceLocation id = EntityList.getKey(entity);
        if (id == null) {
            return null;
        }
        File output = getOutputFile(id, false);
        return ExportTask.forEntity(entity, output, background, isBatch, zoomPercent, exportSize, rotX, rotY, offsetX, offsetY);
    }

    public static ExportTask createEntityIdTask(ResourceLocation entityId, float zoomPercent, ExportTask.Background background, int exportSize, boolean isBatch, int offsetX, int offsetY) {
        return createEntityIdTask(entityId, zoomPercent, background, exportSize, isBatch, -30F, 45F, offsetX, offsetY);
    }

    public static ExportTask createEntityIdTask(ResourceLocation entityId, float zoomPercent, ExportTask.Background background, int exportSize, boolean isBatch, float rotX, float rotY, int offsetX, int offsetY) {
        if (entityId == null) {
            return null;
        }
        File output = getOutputFile(entityId, false);
        return ExportTask.forEntityId(entityId, output, background, isBatch, zoomPercent, exportSize, rotX, rotY, offsetX, offsetY);
    }

    public static void tick() {
        if (QUEUE.isEmpty()) {
            checkBatchFinished();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            return;
        }
        ExportTask task = QUEUE.poll();
        if (task == null) {
            return;
        }
        boolean success = renderTask(mc, task);
        if (task.isBatch) {
            batchRemaining--;
            checkBatchFinished();
        } else if (!success) {
            // Task failed
        }
    }

    private static void checkBatchFinished() {
        if (isBatchRunning && batchRemaining <= 0 && QUEUE.isEmpty() && ACTIVE_TASKS.get() == 0) {
            isBatchRunning = false;
            batchRemaining = 0;
            sendChat(I18n.format("gui.wikizoomer.batch_done"));
        }
    }

    private static File getOutputFile(ResourceLocation id, boolean isGif) {
        Minecraft mc = Minecraft.getMinecraft();
        File baseDir = new File(mc.gameDir, "wiki zoomer");
        File modDir = new File(baseDir, id.getNamespace());
        return new File(modDir, id.getPath() + (isGif ? ".gif" : ".png"));
    }

    private static boolean renderTask(Minecraft mc, ExportTask task) {
        if (task.type == ExportTask.Type.ITEM && (task.itemStack == null || task.itemStack.isEmpty())) {
            return false;
        }
        Entity entity = null;
        if (task.type == ExportTask.Type.ENTITY) {
            entity = resolveEntity(mc, task);
            if (entity == null) {
                return false;
            }
        }

        if (task.type == ExportTask.Type.ITEM && task.isGif) {
            if (renderItemGifTask(mc, task)) {
                return true;
            }
            // fallback to single frame render if gif rendering could not proceed
        }

        int exportSize = task.exportSize;
        ensureFramebuffer(exportSize);
        File targetOutput = task.outputFile;
        if (task.isGif && targetOutput.getName().toLowerCase().endsWith(".gif")) {
            String name = targetOutput.getName();
            targetOutput = new File(targetOutput.getParentFile(), name.substring(0, name.length() - 4) + ".png");
        }
        final File output = targetOutput;
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            LOGGER.warn("Could not create export directory: {}", parent.getAbsolutePath());
        }
        int prevWidth = mc.displayWidth;
        int prevHeight = mc.displayHeight;
        boolean success = false;
        boolean pushedMatrices = false;
        try {
            framebuffer.bindFramebuffer(true);
            GL11.glViewport(0, 0, exportSize, exportSize);
            if (task.background == ExportTask.Background.TRANSPARENT) {
                GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            } else {
                GlStateManager.clearColor(0.298F, 1.0F, 0.0F, 1.0F);
            }
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.disableCull();
            GlStateManager.enableDepth();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, exportSize, exportSize, 0.0D, -10000.0D, 10000.0D);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0F, 0.0F, -2000.0F);
            pushedMatrices = true;
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (task.type == ExportTask.Type.ITEM) {
                renderItem(task.itemStack, task.zoomPercent, exportSize, task.rotX, task.rotY);
            } else {
                renderEntity(mc, entity, task.zoomPercent, exportSize, task.rotX, task.rotY, task.offsetX, task.offsetY);
            }
            boolean transparent = task.background == ExportTask.Background.TRANSPARENT;
            BufferedImage image = readFramebuffer(exportSize, exportSize, framebuffer, transparent);

            ACTIVE_TASKS.incrementAndGet();
            EXECUTOR.submit(() -> {
                try {
                    ImageIO.write(image, "png", output);
                    if (!task.isBatch) {
                        sendChat(I18n.format("gui.wikizoomer.export_done", output.getName()));
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Failed to export PNG image", t);
                } finally {
                    ACTIVE_TASKS.decrementAndGet();
                    checkBatchFinished();
                }
            });
            success = true;
        } catch (Exception e) {
            LOGGER.warn("Failed to export image", e);
        } finally {
            if (pushedMatrices) {
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(GL11.GL_PROJECTION);
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            }
            framebuffer.unbindFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
            GL11.glViewport(0, 0, prevWidth, prevHeight);
        }
        return success;
    }

    private static boolean renderItemGifTask(Minecraft mc, ExportTask task) {
        Set<TextureAtlasSprite> animatedSprites = TextureAnimationHelper.getAnimatedSprites(task.itemStack);
        if (animatedSprites.isEmpty()) {
            return false;
        }
        int totalTicks = TextureAnimationHelper.getTotalPeriodTicks(animatedSprites);
        if (totalTicks <= 1) {
            return false;
        }

        int exportSize = task.exportSize;
        ensureFramebuffer(exportSize);
        File output = task.outputFile;
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            LOGGER.warn("Could not create export directory: {}", parent.getAbsolutePath());
        }

        int prevWidth = mc.displayWidth;
        int prevHeight = mc.displayHeight;
        boolean pushedMatrices = false;

        Map<TextureAtlasSprite, Integer> savedFrameCounters = new HashMap<>();
        Map<TextureAtlasSprite, Integer> savedTickCounters = new HashMap<>();
        for (TextureAtlasSprite sprite : animatedSprites) {
            savedFrameCounters.put(sprite, TextureAnimationHelper.getFrameCounter(sprite));
            savedTickCounters.put(sprite, TextureAnimationHelper.getTickCounter(sprite));
        }

        List<GifFrame> frames = new ArrayList<>();
        int[] prevPixels = null;
        GifFrame currentFrame = null;

        try {
            framebuffer.bindFramebuffer(true);
            GL11.glViewport(0, 0, exportSize, exportSize);

            // Reset each sprite to start of animation and upload initial frame
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            for (TextureAtlasSprite sprite : animatedSprites) {
                TextureAnimationHelper.setFrameCounter(sprite, 0);
                TextureAnimationHelper.setTickCounter(sprite, 0);
                TextureAnimationHelper.uploadInitialFrame(sprite);
            }

            for (int tick = 0; tick < totalTicks; tick++) {
                if (task.background == ExportTask.Background.TRANSPARENT) {
                    GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
                } else {
                    GlStateManager.clearColor(0.298F, 1.0F, 0.0F, 1.0F);
                }
                GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                GlStateManager.disableCull();
                GlStateManager.enableDepth();

                GlStateManager.matrixMode(GL11.GL_PROJECTION);
                GlStateManager.pushMatrix();
                GlStateManager.loadIdentity();
                GlStateManager.ortho(0.0D, exportSize, exportSize, 0.0D, -10000.0D, 10000.0D);
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                GlStateManager.pushMatrix();
                GlStateManager.loadIdentity();
                GlStateManager.translate(0.0F, 0.0F, -2000.0F);
                pushedMatrices = true;
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

                renderItem(task.itemStack, task.zoomPercent, exportSize, task.rotX, task.rotY);

                GlStateManager.popMatrix();
                GlStateManager.matrixMode(GL11.GL_PROJECTION);
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                pushedMatrices = false;

                boolean transparent = task.background == ExportTask.Background.TRANSPARENT;
                BufferedImage image = readFramebuffer(exportSize, exportSize, framebuffer, transparent);

                if (prevPixels != null && Arrays.equals(pixelValues, prevPixels) && currentFrame != null) {
                    currentFrame.delayCentiseconds += 5; // 5cs = 50ms (1 tick)
                } else {
                    prevPixels = pixelValues.clone();
                    currentFrame = new GifFrame(image, 5);
                    frames.add(currentFrame);
                }

                // Advance animation by 1 tick
                mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                for (TextureAtlasSprite sprite : animatedSprites) {
                    TextureAnimationHelper.updateSpriteAnimation(sprite);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to render item GIF frames", e);
            return false;
        } finally {
            if (pushedMatrices) {
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(GL11.GL_PROJECTION);
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            }
            framebuffer.unbindFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
            GL11.glViewport(0, 0, prevWidth, prevHeight);

            // Restore animated sprites to original state
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            for (TextureAtlasSprite sprite : animatedSprites) {
                Integer fc = savedFrameCounters.get(sprite);
                Integer tc = savedTickCounters.get(sprite);
                if (fc != null) {
                    TextureAnimationHelper.setFrameCounter(sprite, fc);
                    TextureAnimationHelper.uploadFrame(sprite, fc);
                }
                if (tc != null) {
                    TextureAnimationHelper.setTickCounter(sprite, tc);
                }
            }
        }

        if (frames.isEmpty()) {
            return false;
        }

        final List<GifFrame> capturedFrames = new ArrayList<>(frames);
        final boolean isTransparent = task.background == ExportTask.Background.TRANSPARENT;
        final boolean isBatch = task.isBatch;

        ACTIVE_TASKS.incrementAndGet();
        EXECUTOR.submit(() -> {
            try {
                AnimatedGifEncoder encoder = new AnimatedGifEncoder();
                encoder.setTransparent(isTransparent);
                encoder.setRepeat(0);
                encoder.start(output.getAbsolutePath());
                for (GifFrame f : capturedFrames) {
                    encoder.addFrame(f.image, f.delayCentiseconds);
                }
                encoder.finish();
                if (!isBatch) {
                    sendChat(I18n.format("gui.wikizoomer.export_done", output.getName()));
                }
            } catch (Throwable t) {
                LOGGER.warn("Failed to encode GIF image: " + output.getAbsolutePath(), t);
            } finally {
                ACTIVE_TASKS.decrementAndGet();
                checkBatchFinished();
            }
        });

        return true;
    }

    private static void ensureFramebuffer(int exportSize) {
        if (framebuffer == null || framebuffer.framebufferTextureWidth != exportSize || framebuffer.framebufferTextureHeight != exportSize) {
            framebuffer = new Framebuffer(exportSize, exportSize, true);
        }
    }

    private static Entity resolveEntity(Minecraft mc, ExportTask task) {
        if (task.entity != null) {
            return task.entity;
        }
        if (task.entityId == null) {
            return null;
        }
        return EntityList.createEntityByIDFromName(task.entityId, mc.world);
    }

    private static void renderItem(ItemStack stack, float zoomPercent, int exportSize, float rotX, float rotY) {
        float scale = ITEM_BASE_SCALE * (zoomPercent / 100F);
        float zOffset = 200.0F;
        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.translate(exportSize / 2F, exportSize / 2F, zOffset);
        GlStateManager.scale(scale, scale, scale);
        renderItem.zLevel += 50.0F;
        try {
            IBakedModel model = renderItem.getItemModelWithOverrides(stack, null, Minecraft.getMinecraft().player);
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
            GlStateManager.enableRescaleNormal();
            GlStateManager.translate(0.0F, 0.0F, renderItem.zLevel);
            GlStateManager.scale(1.0F, -1.0F, 1.0F);
            GlStateManager.scale(16.0F, 16.0F, 16.0F);
            if (model.isGui3d()) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
            model = ForgeHooksClient.handleCameraTransforms(model, ItemCameraTransforms.TransformType.GUI, false);
            if (stack.getItem() instanceof ItemBlock) {
                GlStateManager.rotate(rotX, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(rotY, 0.0F, 1.0F, 0.0F);
            }
            renderItem.renderItem(stack, model);
            GlStateManager.disableLighting();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
        } finally {
            renderItem.zLevel -= 50.0F;
            GlStateManager.popMatrix();
        }
    }

    private static void renderEntity(Minecraft mc, Entity entity, float zoomPercent, int exportSize, float rotX, float rotY, int offsetX, int offsetY) {
        entity.ticksExisted = 0;
        if (entity instanceof EntityLivingBase) {
            ((EntityLivingBase) entity).rotationYawHead = 0;
        }
        float baseScale = zoomPercent / 100F * 100F;
        float sizeScale = 0.75F;
        float maxSize = Math.max(entity.width, entity.height);
        if (maxSize > 1.0F) {
            sizeScale /= maxSize;
        }
        float scale = baseScale * sizeScale;
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        float centerX = exportSize / 2F + offsetX;
        float centerY = exportSize / 2F + offsetY;
        GlStateManager.translate(centerX, centerY, 150.0F + scale);
        GlStateManager.scale(-scale, scale, scale);
        GlStateManager.rotate(rotX, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(180.0F - rotY, 0.0F, -1.0F, 0.0F);
        GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        RenderManager renderManager = mc.getRenderManager();
        renderManager.setPlayerViewY(180.0F);
        renderManager.setRenderShadow(false);
        renderManager.renderEntity(entity, 0.0D, -entity.height / 2.0D, 0.0D, 0.0F, 1.0F, false);
        renderManager.setRenderShadow(true);
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static void sendChat(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.addScheduledTask(() -> {
                if (mc.player != null) {
                    mc.player.sendMessage(new TextComponentString(message));
                }
            });
        }
    }

    private static BufferedImage readFramebuffer(int width, int height, Framebuffer framebufferIn, boolean transparent) {
        if (OpenGlHelper.isFramebufferEnabled()) {
            width = framebufferIn.framebufferTextureWidth;
            height = framebufferIn.framebufferTextureHeight;
        }
        int size = width * height;
        if (pixelBuffer == null || pixelBuffer.capacity() < size) {
            pixelBuffer = BufferUtils.createIntBuffer(size);
            pixelValues = new int[size];
        }
        GlStateManager.glPixelStorei(3333, 1);
        GlStateManager.glPixelStorei(3317, 1);
        pixelBuffer.clear();
        if (OpenGlHelper.isFramebufferEnabled()) {
            GlStateManager.bindTexture(framebufferIn.framebufferTexture);
            GlStateManager.glGetTexImage(3553, 0, 32993, 33639, pixelBuffer);
        } else {
            GlStateManager.glReadPixels(0, 0, width, height, 32993, 33639, pixelBuffer);
        }
        pixelBuffer.get(pixelValues);
        TextureUtil.processPixelValues(pixelValues, width, height);
        BufferedImage image = new BufferedImage(width, height, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, pixelValues, 0, width);
        return image;
    }
}
