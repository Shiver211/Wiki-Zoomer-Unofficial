package com.shiver.wikizoomer.tileentity;

import com.shiver.wikizoomer.ModDataComponents;
import com.shiver.wikizoomer.WikiZoomerUnofficial;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class TileEntityEntityZoomer extends TileEntityZoomerBase {
    private Entity cachedEntity = null;

    public TileEntityEntityZoomer(BlockPos pos, BlockState state) {
        super(WikiZoomerUnofficial.ENTITY_ZOOMER_TE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TileEntityEntityZoomer entity) {
        entity.baseTick();
        if (entity.getItem(0).getItem() != WikiZoomerUnofficial.ENTITY_BINDER_ITEM.get()) {
            entity.cachedEntity = null;
        }
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        super.setItem(index, stack);
        cachedEntity = null;
    }

    @Nullable
    public Entity getCachedEntity() {
        ItemStack item = this.getItem(0);
        if (item.getItem() == WikiZoomerUnofficial.ENTITY_BINDER_ITEM.get()) {
            if (cachedEntity != null) {
                return cachedEntity;
            }
            try {
                CompoundTag entityTag = item.get(ModDataComponents.ENTITY_TAG);
                if (entityTag != null && this.getLevel() != null) {
                    cachedEntity = EntityType.loadEntityRecursive(entityTag, this.getLevel(), new EntitySpawnRequest(EntitySpawnReason.LOAD, false), entity -> entity);
                    if (cachedEntity instanceof LivingEntity livingEntity) {
                        livingEntity.hurtTime = 0;
                    }
                    return cachedEntity;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.wikizoomer.entity_zoomer");
    }
}
