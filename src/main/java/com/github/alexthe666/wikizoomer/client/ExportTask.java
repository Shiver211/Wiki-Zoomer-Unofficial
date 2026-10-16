package com.github.alexthe666.wikizoomer.client;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.io.File;

public class ExportTask {
    public enum Type {
        ITEM,
        ENTITY
    }

    public enum Background {
        GREENSCREEN,
        TRANSPARENT
    }

    public final Type type;
    public final ItemStack itemStack;
    public final Entity entity;
    public final ResourceLocation entityId;
    public final File outputFile;
    public final Background background;
    public final boolean isBatch;
    public final float zoomPercent;
    public final int exportSize;
    public final float rotX;
    public final float rotY;
    public final int offsetX;
    public final int offsetY;
    public final boolean isGif;

    private ExportTask(Type type, ItemStack itemStack, Entity entity, ResourceLocation entityId, File outputFile, Background background, boolean isBatch, float zoomPercent, int exportSize, float rotX, float rotY, int offsetX, int offsetY, boolean isGif) {
        this.type = type;
        this.itemStack = itemStack;
        this.entity = entity;
        this.entityId = entityId;
        this.outputFile = outputFile;
        this.background = background;
        this.isBatch = isBatch;
        this.zoomPercent = zoomPercent;
        this.exportSize = exportSize;
        this.rotX = rotX;
        this.rotY = rotY;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.isGif = isGif;
    }

    public static ExportTask forItem(ItemStack stack, File outputFile, Background background, boolean isBatch, float zoomPercent, int exportSize) {
        return forItem(stack, outputFile, background, isBatch, zoomPercent, exportSize, 0F, 0F, false);
    }

    public static ExportTask forItem(ItemStack stack, File outputFile, Background background, boolean isBatch, float zoomPercent, int exportSize, float rotX, float rotY) {
        return forItem(stack, outputFile, background, isBatch, zoomPercent, exportSize, rotX, rotY, false);
    }

    public static ExportTask forItem(ItemStack stack, File outputFile, Background background, boolean isBatch, float zoomPercent, int exportSize, float rotX, float rotY, boolean isGif) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return new ExportTask(Type.ITEM, copy, null, null, outputFile, background, isBatch, zoomPercent, exportSize, rotX, rotY, 0, 0, isGif);
    }

    public static ExportTask forEntity(Entity entity, File outputFile, Background background, boolean isBatch, float zoomPercent, int exportSize, int offsetX, int offsetY) {
        return forEntity(entity, outputFile, background, isBatch, zoomPercent, exportSize, -30F, 45F, offsetX, offsetY);
    }

    public static ExportTask forEntity(Entity entity, File outputFile, Background background, boolean isBatch, float zoomPercent, int exportSize, float rotX, float rotY, int offsetX, int offsetY) {
        return new ExportTask(Type.ENTITY, ItemStack.EMPTY, entity, null, outputFile, background, isBatch, zoomPercent, exportSize, rotX, rotY, offsetX, offsetY, false);
    }

    public static ExportTask forEntityId(ResourceLocation entityId, File outputFile, Background background, boolean isBatch, float zoomPercent, int exportSize, int offsetX, int offsetY) {
        return forEntityId(entityId, outputFile, background, isBatch, zoomPercent, exportSize, -30F, 45F, offsetX, offsetY);
    }

    public static ExportTask forEntityId(ResourceLocation entityId, File outputFile, Background background, boolean isBatch, float zoomPercent, int exportSize, float rotX, float rotY, int offsetX, int offsetY) {
        return new ExportTask(Type.ENTITY, ItemStack.EMPTY, null, entityId, outputFile, background, isBatch, zoomPercent, exportSize, rotX, rotY, offsetX, offsetY, false);
    }
}
