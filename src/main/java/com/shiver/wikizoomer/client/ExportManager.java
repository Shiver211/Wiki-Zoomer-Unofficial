package com.shiver.wikizoomer.client;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.shiver.wikizoomer.WikiZoomerUnofficialClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

@OnlyIn(Dist.CLIENT)
public class ExportManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int[] EXPORT_SIZES = new int[]{64, 128, 256, 512, 1024, 4096};
    private static final int DEFAULT_EXPORT_SIZE = 512;
    private static final float DEFAULT_ZOOM = 100F;
    private static final Queue<ExportTask> QUEUE = new ArrayDeque<>();
    private static final ExportSettings LAST_ITEM_SETTINGS = new ExportSettings(DEFAULT_ZOOM, ExportTask.Background.GREENSCREEN, DEFAULT_EXPORT_SIZE, 0.0F, 0.0F, 0.0F, 0.0F);
    private static final ExportSettings LAST_ENTITY_SETTINGS = new ExportSettings(DEFAULT_ZOOM, ExportTask.Background.GREENSCREEN, DEFAULT_EXPORT_SIZE, 30.0F, 45.0F, 0.0F, 0.0F);
    public static TextureTarget renderTarget;
    private static int renderTargetSize = -1;
    private static boolean renderQueued = false;
    private static int batchRemaining = 0;
    private static FeatureRenderDispatcher cachedDispatcher;

    public static int[] getExportSizes() {
        return EXPORT_SIZES.clone();
    }

    public static int getDefaultExportSize() {
        return DEFAULT_EXPORT_SIZE;
    }

    public static float getDefaultZoom() {
        return DEFAULT_ZOOM;
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
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return null;
        }
        File output = getOutputFile(id);
        return ExportTask.forItem(stack, output, background, isBatch, zoomPercent, exportSize, rotX, rotY);
    }

    public static ExportTask createEntityTask(Entity entity, float zoomPercent, ExportTask.Background background,
                                                int exportSize, boolean isBatch, float rotX, float rotY, float offsetX, float offsetY) {
        if (entity == null) {
            return null;
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null) {
            return null;
        }
        File output = getOutputFile(id);
        return ExportTask.forEntity(entity, output, background, isBatch, zoomPercent, exportSize, rotX, rotY, offsetX, offsetY);
    }

    public static ExportTask createEntityIdTask(Identifier entityId, float zoomPercent, ExportTask.Background background,
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
            mc.execute(() -> executeTask(mc, task));
        }
    }

    private static void executeTask(Minecraft mc, ExportTask task) {
        try {
            boolean success = renderTask(mc, task);
            Minecraft.getInstance().execute(() -> handleResult(task, success));
        } finally {
            renderQueued = false;
        }
    }

    private static void handleResult(ExportTask task, boolean success) {
        if (task.isBatch) {
            batchRemaining--;
            if (batchRemaining <= 0) {
                batchRemaining = 0;
                sendChat(Component.translatable("gui.wikizoomer.batch_done"));
            }
        } else if (success) {
            sendChat(Component.translatable("gui.wikizoomer.export_done", task.outputFile.getName()));
        }
    }

    private static File getOutputFile(Identifier id) {
        Minecraft mc = Minecraft.getInstance();
        File baseDir = new File(mc.gameDirectory, "wiki zoomer");
        File modDir = new File(baseDir, id.getNamespace());
        return new File(modDir, id.getPath() + ".png");
    }

    private static FeatureRenderDispatcher getFeatureRenderDispatcher() {
        if (cachedDispatcher != null) return cachedDispatcher;
        try {
            Field f = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("featureRenderDispatcher");
            f.setAccessible(true);
            cachedDispatcher = (FeatureRenderDispatcher) f.get(Minecraft.getInstance().gameRenderer);
            return cachedDispatcher;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get FeatureRenderDispatcher", e);
        }
    }

    public static void renderPreviewToTarget(Minecraft mc, ExportTask.Type type, ItemStack itemStack, Entity entity,
                                             float zoomPercent, ExportTask.Background background, int exportSize,
                                             float rotX, float rotY, float offsetX, float offsetY) {
        if (type == ExportTask.Type.ITEM && (itemStack == null || itemStack.isEmpty())) {
            return;
        }
        if (type == ExportTask.Type.ENTITY && entity == null) {
            return;
        }

        ensureRenderTarget(exportSize);

        boolean transparent = background == ExportTask.Background.TRANSPARENT;
        GpuDevice device = RenderSystem.getDevice();
        Vector4fc clearColor = transparent ? new Vector4f(0.0F, 0.0F, 0.0F, 0.0F) : new Vector4f(0.298F, 1.0F, 0.0F, 1.0F);
        device.createCommandEncoder().clearColorAndDepthTextures(renderTarget.getColorTexture(), clearColor, renderTarget.getDepthTexture(), 0.0);

        Projection projection = new Projection();
        projection.setupOrtho(-1000.0F, 1000.0F, exportSize, exportSize, true);
        ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("wikizoomer_preview");
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);

        RenderSystem.outputColorTextureOverride = renderTarget.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = renderTarget.getDepthTextureView();

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        SubmitNodeStorage submitNodeStorage = new SubmitNodeStorage();
        PoseStack poseStack = new PoseStack();

        if (type == ExportTask.Type.ITEM) {
            TrackingItemStackRenderState itemState = new TrackingItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(itemState, itemStack, ItemDisplayContext.GUI, mc.level, null, 0);
            boolean flat = !itemState.usesBlockLight();
            if (flat) {
                mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_FLAT);
            } else {
                mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
            }

            poseStack.pushPose();
            poseStack.translate(exportSize / 2.0F, exportSize / 2.0F, 0.0F);
            float scale = zoomPercent * 1.92F;
            poseStack.scale(scale, -scale, -scale);
            poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
            poseStack.mulPose(Axis.YP.rotationDegrees(rotY));
            itemState.submit(poseStack, submitNodeStorage, 15728880, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        } else {
            mc.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            Entity renderEntity = entity;
            if (WikiZoomerUnofficialClient.dataMimic != null && renderEntity.getType() == WikiZoomerUnofficialClient.dataMimic.getType()) {
                renderEntity = WikiZoomerUnofficialClient.dataMimic;
            } else {
                renderEntity.setYRot(0.0F);
                renderEntity.setXRot(0.0F);
                if (renderEntity instanceof LivingEntity livingEntity) {
                    livingEntity.yBodyRot = 0.0F;
                    livingEntity.yHeadRotO = 0.0F;
                    livingEntity.yHeadRot = 0.0F;
                }
                renderEntity.setOldPosAndRot();
            }

            EntityRenderState renderState = dispatcher.extractEntity(renderEntity, 1.0F);
            renderState.lightCoords = 15728880;

            poseStack.pushPose();
            float centerX = exportSize / 2.0F + offsetX;
            float centerY = (exportSize + ((zoomPercent / 100.0F) * (renderEntity.getBbHeight() * 100.0F))) / 2.0F + offsetY;
            poseStack.translate(centerX, centerY, 0.0F);
            poseStack.scale(zoomPercent, zoomPercent, -zoomPercent);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            float halfHeight = renderEntity.getBbHeight() / 2.0F;
            poseStack.translate(0.0F, halfHeight, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
            poseStack.mulPose(Axis.YP.rotationDegrees(rotY));
            poseStack.translate(0.0F, -halfHeight, 0.0F);

            Quaternionf cameraAngle = Axis.XP.rotationDegrees(rotX);
            CameraRenderState cameraRenderState = new CameraRenderState();
            cameraRenderState.orientation = cameraAngle.conjugate(new Quaternionf()).rotateY((float) Math.PI);

            dispatcher.submit(renderState, cameraRenderState, 0.0, 0.0, 0.0, poseStack, submitNodeStorage);
            poseStack.popPose();
        }

        getFeatureRenderDispatcher().renderAllFeatures(submitNodeStorage);
        modelViewStack.popMatrix();

        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;
        projectionBuffer.close();
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

        renderPreviewToTarget(mc, task.type, task.itemStack, entity, task.zoomPercent, task.background,
                task.exportSize, task.rotX, task.rotY, task.offsetX, task.offsetY);

        boolean transparent = task.background == ExportTask.Background.TRANSPARENT;
        GpuDevice device = RenderSystem.getDevice();

        GpuTexture sourceTexture = renderTarget.getColorTexture();
        if (sourceTexture != null) {
            GpuBuffer readBuffer = device.createBuffer(() -> "wikizoomer export buffer", 9, (long) task.exportSize * task.exportSize * sourceTexture.getFormat().blockSize());
            device.createCommandEncoder().copyTextureToBuffer(
                sourceTexture,
                readBuffer,
                0L,
                () -> {
                    try (GpuBufferSlice.MappedView read = readBuffer.map(true, false)) {
                        NativeImage image = new NativeImage(task.exportSize, task.exportSize, false);
                        for (int y = 0; y < task.exportSize; y++) {
                            for (int x = 0; x < task.exportSize; x++) {
                                int argb = read.data().getInt((x + y * task.exportSize) * sourceTexture.getFormat().blockSize());
                                if (!transparent) {
                                    argb |= 0xFF000000;
                                }
                                image.setPixelABGR(x, task.exportSize - y - 1, argb);
                            }
                        }
                        try {
                            image.writeToFile(output);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to write exported image", e);
                        }
                    } finally {
                        readBuffer.close();
                    }
                },
                0
            );
        }
        return true;
    }

    private static void ensureRenderTarget(int exportSize) {
        if (renderTarget == null || renderTargetSize != exportSize) {
            if (renderTarget != null) {
                renderTarget.destroyBuffers();
            }
            renderTarget = new TextureTarget("wikizoomer", exportSize, exportSize, true, GpuFormat.RGBA8_UNORM);
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
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(task.entityId).map(net.minecraft.core.Holder::value).orElse(null);
        if (type == null || mc.level == null) {
            return null;
        }
        return type.create(mc.level, EntitySpawnReason.LOAD);
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
