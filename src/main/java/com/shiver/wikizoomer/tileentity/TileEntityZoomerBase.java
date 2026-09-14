package com.shiver.wikizoomer.tileentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public abstract class TileEntityZoomerBase extends BaseContainerBlockEntity {

    private NonNullList<ItemStack> stacks = NonNullList.withSize(1, ItemStack.EMPTY);
    public int ticksExisted = 0;

    protected TileEntityZoomerBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.stacks) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return this.stacks.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        if (!this.stacks.get(index).isEmpty()) {
            ItemStack itemstack;
            if (this.stacks.get(index).getCount() <= count) {
                itemstack = this.stacks.get(index);
                this.stacks.set(index, ItemStack.EMPTY);
                this.setChanged();
                return itemstack;
            } else {
                itemstack = this.stacks.get(index).split(count);
                if (this.stacks.get(index).isEmpty()) {
                    this.stacks.set(index, ItemStack.EMPTY);
                }
                this.setChanged();
                return itemstack;
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack stack = this.stacks.get(index);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.stacks.set(index, ItemStack.EMPTY);
            return stack;
        }
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        ItemStack existing = this.stacks.get(index);
        boolean same = !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, existing);
        this.stacks.set(index, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        if (index == 0 && !same) {
            this.requestModelDataUpdate();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.stacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.stacks);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.stacks);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return null;
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public void baseTick() {
        ticksExisted++;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.stacks;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.stacks = items;
    }

    @Override
    public void clearContent() {
        stacks.clear();
        this.setChanged();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.wikizoomer.zoomer");
    }
}
