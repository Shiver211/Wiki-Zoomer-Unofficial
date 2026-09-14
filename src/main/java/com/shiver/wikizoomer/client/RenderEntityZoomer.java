package com.shiver.wikizoomer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.shiver.wikizoomer.WikiZoomerUnofficialClient;
import com.shiver.wikizoomer.block.BlockZoomer;
import com.shiver.wikizoomer.tileentity.TileEntityEntityZoomer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class RenderEntityZoomer implements BlockEntityRenderer<TileEntityEntityZoomer, RenderEntityZoomer.EntityZoomerRenderState> {
    private final EntityRenderDispatcher entityRenderer;

    public RenderEntityZoomer(BlockEntityRendererProvider.Context manager) {
        this.entityRenderer = manager.entityRenderer();
    }

    @Override
    public EntityZoomerRenderState createRenderState() {
        return new EntityZoomerRenderState();
    }

    @Override
    public void extractRenderState(
            TileEntityEntityZoomer tileEntityIn,
            EntityZoomerRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(tileEntityIn, state, partialTicks, cameraPosition, breakProgress);
        Entity renderEntity = null;
        int ticksExisted = 0;
        if (tileEntityIn != null && tileEntityIn.getLevel() != null
                && tileEntityIn.getLevel().getBlockState(tileEntityIn.getBlockPos()).getBlock() instanceof BlockZoomer) {
            renderEntity = tileEntityIn.getCachedEntity();
            ticksExisted = tileEntityIn.ticksExisted;
        }
        state.rrr = (float) ticksExisted - 1 + partialTicks;
        if (renderEntity != null) {
            boolean isMimic = false;
            if (WikiZoomerUnofficialClient.dataMimic != null) {
                if (renderEntity.getType() == WikiZoomerUnofficialClient.dataMimic.getType()) {
                    renderEntity = WikiZoomerUnofficialClient.dataMimic;
                    isMimic = true;
                }
            }
            float f = 0.75F;
            float f1 = Math.max(renderEntity.getBbWidth(), renderEntity.getBbHeight());
            if ((double) f1 > 1.0D) {
                f /= f1;
            }
            state.scale = f;
            if (!isMimic) {
                renderEntity.setYRot(0.0F);
                renderEntity.setXRot(0.0F);
                if (renderEntity instanceof LivingEntity livingEntity) {
                    livingEntity.yBodyRot = 0.0F;
                    livingEntity.yHeadRotO = 0.0F;
                    livingEntity.yHeadRot = 0.0F;
                }
            }
            state.displayEntity = this.entityRenderer.extractEntity(renderEntity, partialTicks);
            state.displayEntity.lightCoords = state.lightCoords;
        } else {
            state.displayEntity = null;
        }
    }

    @Override
    public void submit(EntityZoomerRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.displayEntity == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5D, 1.0D, 0.5D);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.rrr * 2.0F));
        poseStack.translate(0.0D, 0.1F + Math.sin(state.rrr * 0.05F) * 0.1F, 0.0D);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.translate(0.0D, 0.2D, 0.0D);
        poseStack.scale(state.scale, state.scale, state.scale);
        this.entityRenderer.submit(state.displayEntity, camera, 0.0, 0.0, 0.0, poseStack, submitNodeCollector);
        poseStack.popPose();
        poseStack.popPose();
    }

    public static class EntityZoomerRenderState extends BlockEntityRenderState {
        public @Nullable EntityRenderState displayEntity;
        public float rrr;
        public float scale;
    }
}
