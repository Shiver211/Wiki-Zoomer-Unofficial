package com.github.alexthe666.wikizoomer.client;

import com.github.alexthe666.wikizoomer.ModConfig;
import com.github.alexthe666.wikizoomer.tileentity.TileEntityZoomerBase;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.math.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.widget.ForgeSlider;
import org.lwjgl.opengl.GL11;

@OnlyIn(Dist.CLIENT)
public class GuiItemZoomer extends Screen {

    public static final ResourceLocation GREENSCREEN = Objects.requireNonNull(ResourceLocation.tryParse("wikizoomer:textures/gui/greenscreen.png"), "wikizoomer:textures/gui/greenscreen.png");
    private static final int CROP_FRAME_SIZE = 192;
    private static final int CROP_FRAME_COLOR = 0xFFFF0000;
    private final TileEntityZoomerBase zoomerBase;
    private ExportTask.Background background = ExportTask.Background.GREENSCREEN;
    private float sliderValue = 100;
    private float prevSliderValue = sliderValue;
    private int exportSizeIndex = findDefaultExportSizeIndex();
    private static final int[] EXPORT_SIZES = ExportManager.getExportSizes();
    private float rotX = 0F;
    private float rotY = 0F;
    private ForgeSlider zoomSlider;
    private Button exportButton;

    public GuiItemZoomer(TileEntityZoomerBase zoomerBase) {
        super(Component.translatable("item_zoomer"));
        this.zoomerBase = zoomerBase;
        applyConfig(ZoomerSessionConfig.getItemConfig());
    }

    private void setSliderValue(int i, float sliderValue) {
        this.sliderValue = Math.round(Mth.clamp(sliderValue, 1, 1000F));
        prevSliderValue = this.sliderValue;
    }

    public void init() {
        super.init();
        this.clearWidgets();
        int i = (this.width) / 2;
        int j = (this.height - 166) / 2;
        MutableComponent exit = Component.translatable("gui.wikizoomer.close");
        MutableComponent backgroundLabel = Component.translatable("gui.wikizoomer.background", getBackgroundLabel());
        MutableComponent batchExport = Component.translatable("gui.wikizoomer.batch_export");
        MutableComponent clearConfig = Component.translatable("gui.wikizoomer.clear_config");
        MutableComponent resolutionLabel = Component.translatable("gui.wikizoomer.resolution", getExportSize(), getExportSize());
        int buttonWidth = 120;
        int buttonHeight = 20;
        int spacing = 20;
        int rowWidth = buttonWidth * 3 + spacing * 2;
        int startX = i - rowWidth / 2;
        int col1X = startX;
        int col2X = startX + buttonWidth + spacing;
        int col3X = startX + (buttonWidth + spacing) * 2;
        int row1Y = j + 180;
        int row2Y = row1Y + 22;
        int row3Y = row2Y + 22;
        this.zoomSlider = new ForgeSlider(col1X, row1Y, buttonWidth, buttonHeight, Component.translatable("gui.wikizoomer.zoom"), Component.literal("%"), 1, 1000, sliderValue, 1, 1, true) {
            @Override
            protected void applyValue() {
                GuiItemZoomer.this.setSliderValue(2, (float)getValue());
            }
        };
        this.addRenderableWidget(this.zoomSlider);
        this.addRenderableWidget(Button.builder(backgroundLabel, (button) -> {
            GuiItemZoomer.this.background = GuiItemZoomer.this.background == ExportTask.Background.GREENSCREEN
                    ? ExportTask.Background.TRANSPARENT
                    : ExportTask.Background.GREENSCREEN;
            init();
        }).size(buttonWidth, buttonHeight).pos(col2X, row1Y).build());
        this.exportButton = this.addRenderableWidget(Button.builder(getExportButtonText(), (button) -> {
            saveCurrentConfig();
            ExportTask task = ExportManager.createItemTask(zoomerBase.getItem(0), sliderValue, background, getExportSize(), false, rotX, rotY, hasShiftDown());
            if (task == null) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(Component.translatable("gui.wikizoomer.export_no_item"));
                }
            } else {
                ExportManager.enqueue(task);
            }
        }).size(buttonWidth, buttonHeight).pos(col3X, row1Y).build());
        this.addRenderableWidget(Button.builder(resolutionLabel, (button) -> {
            exportSizeIndex = (exportSizeIndex + 1) % EXPORT_SIZES.length;
            init();
        }).size(buttonWidth, buttonHeight).pos(col1X, row2Y).build());
        this.addRenderableWidget(Button.builder(batchExport, (button) -> {
            saveCurrentConfig();
            Minecraft.getInstance().setScreen(new GuiBatchExport());
        }).size(buttonWidth, buttonHeight).pos(col2X, row2Y).build());
        this.addRenderableWidget(Button.builder(exit, (button) -> {
            saveCurrentConfig();
            Minecraft.getInstance().setScreen(null);
        }).size(buttonWidth, buttonHeight).pos(col3X, row2Y).build());
        this.addRenderableWidget(Button.builder(clearConfig, (button) -> {
            applyConfig(ZoomerSessionConfig.resetItem());
            init();
        }).size(buttonWidth, buttonHeight).pos(col2X, row3Y).build());
    }

    public void renderGreenscreen(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF4CFF00);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        exportButton.setMessage(getExportButtonText());
        if (getMinecraft() != null) {
            try {
                if (background == ExportTask.Background.GREENSCREEN) {
                    renderGreenscreen(guiGraphics);
                } else {
                    this.renderBackground(guiGraphics);
                }
            } catch (Exception e) {

            }
            clearPreviewDepth();
            renderFocus(guiGraphics);
            renderCropFrame(guiGraphics);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 5000F);
            super.render(guiGraphics, mouseX, mouseY, partialTicks);
            guiGraphics.pose().popPose();
            int i = this.width / 2;
            int j = this.height / 2;
            float halfPreviewSize = 8.0F * getItemPreviewScale();
            if(mouseX > (i - halfPreviewSize) && mouseX < (i + halfPreviewSize) && mouseY > (j - halfPreviewSize) && mouseY < (j + halfPreviewSize)){
                ItemStack itemStack = zoomerBase.getItem(0);
                guiGraphics.renderTooltip(font, itemStack, -500, -500);
            }
        }

    }

    private void renderFocus(GuiGraphics guiGraphics) {
        int i = this.width / 2;
        int j = this.height / 2;
        ItemStack itemStack = zoomerBase.getItem(0);
        float scale = getItemPreviewScale();
        if (!itemStack.isEmpty()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(i, j, 10F);
            guiGraphics.pose().scale(1.0F, 1.0F, 0.01F);
            guiGraphics.pose().translate(0.0F, 0.0F, 1500.0F);
            guiGraphics.pose().scale(scale, scale, scale);
            guiGraphics.pose().translate(0.0F, 0.0F, 150.0F);
            guiGraphics.pose().mulPose(Axis.XP.rotationDegrees(rotX));
            guiGraphics.pose().mulPose(Axis.YP.rotationDegrees(rotY));
            guiGraphics.pose().translate(-8.0F, -8.0F, -150.0F);
            guiGraphics.renderItem(Minecraft.getInstance().player, itemStack, 0, 0, 1);
            guiGraphics.pose().popPose();
        }
    }

    private void clearPreviewDepth() {
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private void renderCropFrame(GuiGraphics guiGraphics) {
        int left = (this.width - CROP_FRAME_SIZE) / 2;
        int top = (this.height - CROP_FRAME_SIZE) / 2;
        int right = left + CROP_FRAME_SIZE;
        int bottom = top + CROP_FRAME_SIZE;
        guiGraphics.fill(left, top, right, top + 1, CROP_FRAME_COLOR);
        guiGraphics.fill(left, bottom - 1, right, bottom, CROP_FRAME_COLOR);
        guiGraphics.fill(left, top, left + 1, bottom, CROP_FRAME_COLOR);
        guiGraphics.fill(right - 1, top, right, bottom, CROP_FRAME_COLOR);
    }

    private float getPreviewScale() {
        return CROP_FRAME_SIZE / (float)getExportSize();
    }

    private float getItemPreviewScale() {
        return (sliderValue / 100.0F) * 12.0F * getPreviewScale();
    }

    private void saveCurrentConfig() {
        ZoomerSessionConfig.saveItem(sliderValue, background, getExportSize(), rotX, rotY);
    }

    private void applyConfig(ZoomerSessionConfig.ItemConfig config) {
        this.sliderValue = config.zoomPercent;
        this.prevSliderValue = this.sliderValue;
        this.background = config.background;
        this.exportSizeIndex = findExportSizeIndex(config.exportSize);
        this.rotX = config.rotX;
        this.rotY = config.rotY;
    }

    public boolean isPauseScreen() {
        return false;
    }

    private static int findDefaultExportSizeIndex() {
        return findExportSizeIndex(ExportManager.getDefaultExportSize());
    }

    private static int findExportSizeIndex(int exportSize) {
        for (int i = 0; i < EXPORT_SIZES.length; i++) {
            if (EXPORT_SIZES[i] == exportSize) {
                return i;
            }
        }
        return EXPORT_SIZES.length - 1;
    }

    private int getExportSize() {
        return EXPORT_SIZES[exportSizeIndex];
    }

    private Component getBackgroundLabel() {
        return background == ExportTask.Background.GREENSCREEN
                ? Component.translatable("gui.wikizoomer.background.greenscreen")
                : Component.translatable("gui.wikizoomer.background.transparent");
    }

    private Component getExportButtonText() {
        boolean gif = !hasShiftDown() && ModConfig.EXPORT_ANIMATED_TEXTURES_AS_GIF.get()
                && ExportManager.isItemAnimated(zoomerBase.getItem(0));
        return Component.translatable(gif ? "gui.wikizoomer.export_gif" : "gui.wikizoomer.export_png");
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (button == 0) {
            rotY += (float) dragX;
            rotX -= (float) dragY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float newSliderValue = this.sliderValue + (float) delta * 10F;
        setSliderValue(2, newSliderValue);
        if (zoomSlider != null) {
            zoomSlider.setValue(this.sliderValue);
        }
        return true;
    }

    @Override
    public void onClose() {
        saveCurrentConfig();
        super.onClose();
    }
}
