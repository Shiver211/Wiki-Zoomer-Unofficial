package com.shiver.wikizoomer.client;

import com.shiver.wikizoomer.ModConfig;
import com.shiver.wikizoomer.client.gif.AnimatedGifEncoder;
import com.shiver.wikizoomer.client.gif.GifFrame;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.shiver.wikizoomer.WikiZoomerUnofficialClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OnlyIn(Dist.CLIENT)
public class ExportManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int[] EXPORT_SIZES = new int[]{64, 128, 256, 512, 1024, 4096};
    private static final int DEFAULT_EXPORT_SIZE = 512;
    private static final float DEFAULT_ZOOM = 100F;
    private static final Queue<ExportTask> QUEUE = new ArrayDeque<>();
    private static final ExportSettings LAST_ITEM_SETTINGS = new ExportSettings(DEFAULT_ZOOM, ExportTask.Background.GREENSCREEN, DEFAULT_EXPORT_SIZE, 0.0F, 0.0F, 0.0F, 0.0F);
    private static final ExportSettings LAST_ENTITY_SETTINGS = new ExportSettings(DEFAULT_ZOOM, ExportTask.Background.GREENSCREEN, DEFAULT_EXPORT_SIZE, 30.0F, 45.0F, 0.0F, 0.0F);
    private static RenderTarget renderTarget;
    private static int renderTargetSize = -1;
    private static boolean renderQueued = false;
    private static int batchRemaining = 0;
    private static IntBuffer pixelBuffer;
    private static int[] pixelValues;
    private static final ExecutorService EXPORT_WORKERS = Executors.newFixedThreadPool(
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)), runnable -> {
                Thread thread = new Thread(runnable, "WikiZoomer-ExportWorker");
                thread.setDaemon(true);
                return thread;
            });

    public static int[] getExportSizes() {
        return EXPORT_SIZES.clone();
    }

    public static int getDefaultExportSize() {
        return DEFAULT_EXPORT_SIZE;
    }

    public static float getDefaultZoom() {
        return DEFAULT_ZOOM;
    }

    public static boolean isItemAnimated(ItemStack stack) {
        return TextureAnimationHelper.isItemAnimated(stack);
    }

    public static ExportSettings getLastItemSettings() {
        return LAST_ITEM_SETTINGS.copy();
    }

    public static ExportSettings getLastEntitySettings() {
        return LAST_ENTITY_SETTINGS.copy();
    }

    public static void rememberItemSettings(float zoomPercent, ExportTask.Background background, int exportSize, float rotX, float rotY) {
        LAST_ITEM_SETTINGS.set(zoomPercent, background, exportSize, rotX, rotY, 0.0F, 0.0F);
    }

    public static void rememberEntitySettings(float zoomPercent, ExportTask.Background background, int exportSize,
                                                float rotX, float rotY, float offsetX, float offsetY) {
        LAST_ENTITY_SETTINGS.set(zoomPercent, background, exportSize, rotX, rotY, offsetX, offsetY);
    }

    public static void resetItemSettings() {
        LAST_ITEM_SETTINGS.set(DEFAULT_ZOOM, ExportTask.Background.GREENSCREEN, DEFAULT_EXPORT_SIZE, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    public static void resetEntitySettings() {
        LAST_ENTITY_SETTINGS.set(DEFAULT_ZOOM, ExportTask.Background.GREENSCREEN, DEFAULT_EXPORT_SIZE, 30.0F, 45.0F, 0.0F, 0.0F);
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
        sendChat(Component.translatable("gui.wikizoomer.batch_started", tasks.size()));
    }

    public static ExportTask createItemTask(ItemStack stack, float zoomPercent, ExportTask.Background background,
                                            int exportSize, boolean isBatch, float rotX, float rotY) {
        return createItemTask(stack, zoomPercent, background, exportSize, isBatch, rotX, rotY, false);
    }

    public static ExportTask createItemTask(ItemStack stack, float zoomPercent, ExportTask.Background background,
                                            int exportSize, boolean isBatch, float rotX, float rotY, boolean forcePng) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return null;
        }
        boolean isGif = !forcePng && ModConfig.EXPORT_ANIMATED_TEXTURES_AS_GIF.get() && isItemAnimated(stack);
        File output = getOutputFile(id, isGif);
        return ExportTask.forItem(stack, output, background, isBatch, zoomPercent, exportSize, rotX, rotY, isGif);
    }

    public static ExportTask createEntityTask(Entity entity, float zoomPercent, ExportTask.Background background,
                                                int exportSize, boolean isBatch, float rotX, float rotY, float offsetX, float offsetY) {
        if (entity == null) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null) {
            return null;
        }
        File output = getOutputFile(id);
        return ExportTask.forEntity(entity, output, background, isBatch, zoomPercent, exportSize, rotX, rotY, offsetX, offsetY);
    }

    public static ExportTask createEntityIdTask(ResourceLocation entityId, float zoomPercent, ExportTask.Background background,
                                                int exportSize, boolean isBatch, float rotX, float rotY, float offsetX, float offsetY) {
        if (entityId == null) {
            return null;
        }
        File output = getOutputFile(entityId);
        return ExportTask.forEntityId(entityId, output, background, isBatch, zoomPercent, exportSize, rotX, rotY, offsetX, offsetY);
    }

    public static void tick() {
        if (renderQueued || QUEUE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ExportTask task = QUEUE.poll();
        if (task == null) {
            return;
        }
        renderQueued = true;
        if (RenderSystem.isOnRenderThread()) {
            executeTask(mc, task);
        } else {
            RenderSystem.recordRenderCall(() -> executeTask(mc, task));
        }
    }

    private static void executeTask(Minecraft mc, ExportTask task) {
        try {
            if (!renderTask(mc, task)) {
                handleResult(task, task.outputFile, false);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to render exported image", e);
            handleResult(task, task.outputFile, false);
        } finally {
            renderQueued = false;
        }
    }

    private static void handleResult(ExportTask task, File output, boolean success) {
        if (task.isBatch) {
            batchRemaining--;
            if (batchRemaining <= 0) {
                batchRemaining = 0;
                sendChat(Component.translatable("gui.wikizoomer.batch_done"));
            }
        } else if (success) {
            sendChat(Component.translatable("gui.wikizoomer.export_done", output.getName()));
        }
    }

    private static File getOutputFile(ResourceLocation id) {
        return getOutputFile(id, false);
    }

    private static File getOutputFile(ResourceLocation id, boolean isGif) {
        Minecraft mc = Minecraft.getInstance();
        File baseDir = new File(mc.gameDirectory, "wiki zoomer");
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

        File output = task.outputFile;
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            LOGGER.warn("Could not create export directory: {}", parent.getAbsolutePath());
        }

        if (task.isGif) {
            List<GifFrame> frames = captureGifFrames(mc, task);
            if (!frames.isEmpty()) {
                File gifOutput = output;
                writeAsync(task, gifOutput, () -> writeGif(frames, task.background == ExportTask.Background.TRANSPARENT, gifOutput));
                return true;
            }
            // 材质重载后可能已变成静态图，此时使用 PNG 格式与扩展名。
            output = new File(output.getParentFile(), output.getName().replaceFirst("\\.gif$", ".png"));
        }
        BufferedImage image = renderFrame(mc, task, entity);
        File pngOutput = output;
        writeAsync(task, pngOutput, () -> ImageIO.write(image, "png", pngOutput));
        return true;
    }

    private static List<GifFrame> captureGifFrames(Minecraft mc, ExportTask task) {
        List<GifFrame> frames = new ArrayList<>();
        int[] previousPixels = null;
        try (TextureAnimationHelper.Capture capture = TextureAnimationHelper.beginCapture(task.itemStack)) {
            if (capture.periodTicks() <= 1) return frames;
            for (int tick = 0; tick < capture.periodTicks(); tick++) {
                BufferedImage image = renderFrame(mc, task, null);
                if (previousPixels != null && Arrays.equals(previousPixels, pixelValues)) {
                    frames.get(frames.size() - 1).delayCentiseconds += 5;
                } else {
                    previousPixels = pixelValues.clone();
                    frames.add(new GifFrame(image, 5)); // Minecraft 每 tick 为 50 ms。
                }
                capture.advance();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to capture animated texture; exporting PNG instead", e);
            frames.clear();
        }
        return frames;
    }

    private static void writeGif(List<GifFrame> frames, boolean transparent, File output) throws IOException {
        AnimatedGifEncoder encoder = new AnimatedGifEncoder();
        encoder.setTransparent(transparent);
        encoder.setRepeat(0);
        try (OutputStream stream = Files.newOutputStream(output.toPath())) {
            if (!encoder.start(stream)) throw new IOException("Could not start GIF encoder");
            for (GifFrame frame : frames) {
                if (!encoder.addFrame(frame.image, frame.delayCentiseconds)) throw new IOException("Could not encode GIF frame");
            }
            if (!encoder.finish()) throw new IOException("Could not finish GIF encoder");
        }
    }

    private static void writeAsync(ExportTask task, File output, ImageWriter writer) {
        EXPORT_WORKERS.execute(() -> {
            boolean success = false;
            try {
                writer.write();
                success = true;
            } catch (Exception e) {
                LOGGER.warn("Failed to write exported image: {}", output, e);
            }
            boolean result = success;
            // 完成提示和批量计数只在文件写入结束后更新。
            Minecraft.getInstance().execute(() -> handleResult(task, output, result));
        });
    }

    @FunctionalInterface
    private interface ImageWriter {
        void write() throws IOException;
    }

    private static BufferedImage renderFrame(Minecraft mc, ExportTask task, Entity entity) {
        ensureRenderTarget(task.exportSize);

        RenderTarget previous = mc.getMainRenderTarget();
        boolean transparent = task.background == ExportTask.Background.TRANSPARENT;
        renderTarget.setClearColor(transparent ? 0.0F : 0.298F, transparent ? 0.0F : 1.0F, 0.0F, transparent ? 0.0F : 1.0F);
        renderTarget.clear(Minecraft.ON_OSX);
        renderTarget.bindWrite(true);

        RenderSystem.viewport(0, 0, task.exportSize, task.exportSize);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        Matrix4f previousProjection = RenderSystem.getProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, task.exportSize, task.exportSize, 0.0F, 1000.0F, 3000.0F), VertexSorting.ORTHOGRAPHIC_Z);

        try {
            PoseStack poseStack = new PoseStack();
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, -2000.0F);
            if (task.type == ExportTask.Type.ITEM) {
                renderItemCentered(poseStack, bufferSource, task.itemStack, task.exportSize, task.zoomPercent, task.rotX, task.rotY);
            } else {
                renderEntityCentered(poseStack, bufferSource, entity, task.exportSize, task.zoomPercent, task.rotX, task.rotY, task.offsetX, task.offsetY);
            }
            bufferSource.endBatch();
            Lighting.setupFor3DItems();
            poseStack.popPose();
            return readPixels(task.exportSize, task.exportSize, renderTarget, transparent);
        } finally {
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.ORTHOGRAPHIC_Z);
            previous.bindWrite(true);
            RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
        }
    }

    private static void ensureRenderTarget(int exportSize) {
        if (renderTarget == null || renderTargetSize != exportSize) {
            if (renderTarget != null) {
                renderTarget.destroyBuffers();
            }
            renderTarget = new TextureTarget(exportSize, exportSize, true, Minecraft.ON_OSX);
            renderTargetSize = exportSize;
        }
    }

    private static Entity resolveEntity(Minecraft mc, ExportTask task) {
        if (task.entity != null) {
            return task.entity;
        }
        if (task.entityId == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(task.entityId);
        if (type == null || mc.level == null) {
            return null;
        }
        return type.create(mc.level);
    }

    private static void renderItemCentered(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                            ItemStack stack, int exportSize, float zoomPercent, float rotX, float rotY) {
        float scale = zoomPercent * 1.92F;
        poseStack.pushPose();
        poseStack.translate(exportSize / 2.0F, exportSize / 2.0F, 100.0F);
        poseStack.scale(scale, -scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotY));

        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
        if (!model.usesBlockLight()) {
            Lighting.setupForFlatItems();
        } else {
            Lighting.setupFor3DItems();
        }
        mc.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, poseStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);
        poseStack.popPose();
    }

    private static void renderEntityCentered(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                                Entity entity, int exportSize, float zoomPercent, float rotX, float rotY,
                                                float offsetX, float offsetY) {
        float centerX = exportSize / 2.0F + offsetX;
        float centerY = (exportSize + ((zoomPercent / 100.0F) * (entity.getBbHeight() * 100.0F))) / 2.0F + offsetY;
        Entity renderEntity = entity;
        boolean isMimic = false;
        if (WikiZoomerUnofficialClient.dataMimic != null && renderEntity.getType() == WikiZoomerUnofficialClient.dataMimic.getType()) {
            renderEntity = WikiZoomerUnofficialClient.dataMimic;
            isMimic = true;
        }

        poseStack.pushPose();
        poseStack.translate(centerX, centerY, 10.0F);
        poseStack.scale(zoomPercent, zoomPercent, zoomPercent);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        float halfHeight = renderEntity.getBbHeight() / 2.0F;
        poseStack.translate(0.0F, halfHeight, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotY));
        poseStack.translate(0.0F, -halfHeight, 0.0F);

        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher entityRenderDispatcher = mc.getEntityRenderDispatcher();
        Quaternionf cameraOrientation = Axis.XP.rotationDegrees(rotX);
        cameraOrientation.conjugate();
        entityRenderDispatcher.overrideCameraOrientation(cameraOrientation);
        entityRenderDispatcher.setRenderShadow(false);

        if (!isMimic) {
            if (renderEntity instanceof LivingEntity livingEntity) {
                livingEntity.yBodyRot = 0.0F;
                livingEntity.yHeadRotO = 0.0F;
                livingEntity.yHeadRot = 0.0F;
            }
            renderEntity.setYRot(0.0F);
            renderEntity.setXRot(0.0F);
            renderEntity.setOldPosAndRot();
        }

        Vector3f light0 = new Vector3f(-0.2F, 0.0F, 1.0F).normalize();
        Vector3f light1 = new Vector3f(-0.2F, -1.0F, 0.0F).normalize();
        RenderSystem.setShaderLights(light0, light1);

        entityRenderDispatcher.render(renderEntity, 0.0D, 0.0D, 0.0D, 0.0F, mc.getTimer().getGameTimeDeltaPartialTick(true), poseStack, bufferSource, 15728880);
        entityRenderDispatcher.setRenderShadow(true);
        Lighting.setupFor3DItems();
        poseStack.popPose();
    }

    private static BufferedImage readPixels(int width, int height, RenderTarget target, boolean transparent) {
        int size = width * height;
        if (pixelBuffer == null || pixelBuffer.capacity() < size) {
            pixelBuffer = BufferUtils.createIntBuffer(size);
            pixelValues = new int[size];
        }
        target.bindRead();
        RenderSystem.bindTexture(target.getColorTextureId());
        pixelBuffer.clear();
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
        pixelBuffer.get(pixelValues);
        processPixelValues(pixelValues, width, height);
        BufferedImage image = new BufferedImage(width, height, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, pixelValues, 0, width);
        return image;
    }

    private static void processPixelValues(int[] pixels, int width, int height) {
        int[] row = new int[width];
        int half = height / 2;
        for (int y = 0; y < half; ++y) {
            System.arraycopy(pixels, y * width, row, 0, width);
            System.arraycopy(pixels, (height - 1 - y) * width, pixels, y * width, width);
            System.arraycopy(row, 0, pixels, (height - 1 - y) * width, width);
        }
    }

    private static void sendChat(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(message);
        }
    }

    public static class ExportSettings {
        public float zoomPercent;
        public ExportTask.Background background;
        public int exportSize;
        public float rotX;
        public float rotY;
        public float offsetX;
        public float offsetY;

        private ExportSettings(float zoomPercent, ExportTask.Background background, int exportSize,
                                float rotX, float rotY, float offsetX, float offsetY) {
            set(zoomPercent, background, exportSize, rotX, rotY, offsetX, offsetY);
        }

        private void set(float zoomPercent, ExportTask.Background background, int exportSize,
                            float rotX, float rotY, float offsetX, float offsetY) {
            this.zoomPercent = zoomPercent;
            this.background = background;
            this.exportSize = exportSize;
            this.rotX = rotX;
            this.rotY = rotY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        private ExportSettings copy() {
            return new ExportSettings(zoomPercent, background, exportSize, rotX, rotY, offsetX, offsetY);
        }
    }
}
