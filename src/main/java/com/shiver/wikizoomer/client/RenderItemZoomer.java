package com.shiver.wikizoomer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.shiver.wikizoomer.block.BlockZoomer;
import com.shiver.wikizoomer.tileentity.TileEntityItemZoomer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RenderItemZoomer implements BlockEntityRenderer<TileEntityItemZoomer, RenderItemZoomer.ItemZoomerRenderState> {
    private final ItemModelResolver itemModelResolver;

    public RenderItemZoomer(BlockEntityRendererProvider.Context manager) {
        this.itemModelResolver = manager.itemModelResolver();
    }

    @Override
    public ItemZoomerRenderState createRenderState() {
        return new ItemZoomerRenderState();
    }

    @Override
    public void extractRenderState(
            TileEntityItemZoomer tileEntityIn,
            ItemZoomerRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(tileEntityIn, state, partialTicks, cameraPosition, breakProgress);
        ItemStack stack = ItemStack.EMPTY;
        int ticksExisted = 0;
        if (tileEntityIn != null && tileEntityIn.getLevel() != null
                && tileEntityIn.getLevel().getBlockState(tileEntityIn.getBlockPos()).getBlock() instanceof BlockZoomer) {
            stack = tileEntityIn.getItem(0);
            ticksExisted = tileEntityIn.ticksExisted;
        }
        state.rrr = (float) ticksExisted - 1 + partialTicks;
        state.hasItem = !stack.isEmpty();
        if (state.hasItem) {
            int seed = (int) tileEntityIn.getBlockPos().asLong();
            this.itemModelResolver.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED, tileEntityIn.getLevel(), null, seed);
        } else {
            state.item.clear();
        }
    }

    @Override
    public void submit(ItemZoomerRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (!state.hasItem || state.item.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5D, 1.25D, 0.5D);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.rrr * 2.0F));
        poseStack.translate(0.0D, 0.1F + Math.sin(state.rrr * 0.05F) * 0.1F, 0.0D);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        poseStack.popPose();
    }

    public static class ItemZoomerRenderState extends BlockEntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float rrr;
        public boolean hasItem;
    }
}
