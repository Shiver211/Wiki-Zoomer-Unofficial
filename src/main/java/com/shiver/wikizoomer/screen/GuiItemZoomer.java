package com.shiver.wikizoomer.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.shiver.wikizoomer.client.ExportManager;
import com.shiver.wikizoomer.client.ExportTask;
import com.shiver.wikizoomer.tileentity.TileEntityZoomerBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;

public class GuiItemZoomer extends Screen {

    public static final Identifier GREENSCREEN = Objects.requireNonNull(Identifier.tryParse("wikizoomer:textures/gui/greenscreen.png"));
    private final TileEntityZoomerBase zoomerBase;
    private ExportTask.Background background = ExportTask.Background.GREENSCREEN;
    private float sliderValue = 100;
    private float prevSliderValue = sliderValue;
    private int exportSizeIndex = findDefaultExportSizeIndex();
    private static final int[] EXPORT_SIZES = ExportManager.getExportSizes();
    private float rotX = 0F;
    private float rotY = 0F;
    private ZoomSlider zoomSlider;
    private static final int FRAME_COLOR = 0xFFFF0000;

    public GuiItemZoomer(TileEntityZoomerBase zoomerBase) {
        super(Component.translatable("item_zoomer"));
        this.zoomerBase = zoomerBase;
        ExportManager.ExportSettings settings = ExportManager.getLastItemSettings();
        this.background = settings.background;
        this.sliderValue = settings.zoomPercent;
        this.prevSliderValue = this.sliderValue;
        this.exportSizeIndex = findExportSizeIndex(settings.exportSize);
        this.rotX = settings.rotX;
        this.rotY = settings.rotY;
    }

    private void setSliderValue(float value) {
        this.sliderValue = Math.round(Mth.clamp(value, 1, 1000F));
        prevSliderValue = this.sliderValue;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        int i = this.width / 2;
        int j = (this.height - 166) / 2;
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

        this.zoomSlider = new ZoomSlider(col1X, row1Y, buttonWidth, buttonHeight);
        this.addRenderableWidget(this.zoomSlider);

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.wikizoomer.background", getBackgroundLabel()), (button) -> {
                    this.background = this.background == ExportTask.Background.GREENSCREEN
                            ? ExportTask.Background.TRANSPARENT : ExportTask.Background.GREENSCREEN;
                    init();
                }).size(buttonWidth, buttonHeight).pos(col2X, row1Y).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.wikizoomer.export_png"), (button) -> {
                    ExportManager.rememberItemSettings(sliderValue, background, getExportSize(), rotX, rotY);
                    ExportTask task = ExportManager.createItemTask(zoomerBase.getItem(0), sliderValue, background, getExportSize(), false, rotX, rotY);
                    if (task == null) {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("gui.wikizoomer.export_no_item"));
                        }
                    } else {
                        ExportManager.enqueue(task);
                    }
                }).size(buttonWidth, buttonHeight).pos(col3X, row1Y).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.wikizoomer.resolution", getExportSize(), getExportSize()), (button) -> {
                    exportSizeIndex = (exportSizeIndex + 1) % EXPORT_SIZES.length;
                    init();
                }).size(buttonWidth, buttonHeight).pos(col1X, row2Y).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.wikizoomer.batch_export"), (button) -> {
                    ExportManager.rememberItemSettings(sliderValue, background, getExportSize(), rotX, rotY);
                    Minecraft.getInstance().gui.setScreen(new GuiBatchExport());
                }).size(buttonWidth, buttonHeight).pos(col2X, row2Y).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.wikizoomer.clear_config"), (button) -> {
                    resetSettings();
                    init();
                }).size(buttonWidth, buttonHeight).pos(col2X, row3Y).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.wikizoomer.close"), (button) -> {
                    Minecraft.getInstance().gui.setScreen(null);
                }).size(buttonWidth, buttonHeight).pos(col3X, row2Y).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (background == ExportTask.Background.GREENSCREEN) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xFF4CFF00);
        } else {
            this.extractMenuBackground(guiGraphics);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderFocus(guiGraphics);
        renderCropFrame(guiGraphics);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        int previewSize = getPreviewSize();
        int left = (this.width - previewSize) / 2;
        int top = getPreviewTop(previewSize);
        if (mouseX > left && mouseX < left + previewSize && mouseY > top && mouseY < top + previewSize) {
            ItemStack itemStack = zoomerBase.getItem(0);
            if (!itemStack.isEmpty()) {
                guiGraphics.setTooltipForNextFrame(this.font, itemStack, mouseX, mouseY);
            }
        }
    }

    private void renderCropFrame(GuiGraphicsExtractor guiGraphics) {
        int size = getPreviewSize();
        int left = (this.width - size) / 2;
        int top = getPreviewTop(size);
        guiGraphics.fill(left, top, left + size, top + 2, FRAME_COLOR);
        guiGraphics.fill(left, top + size - 2, left + size, top + size, FRAME_COLOR);
        guiGraphics.fill(left, top, left + 2, top + size, FRAME_COLOR);
        guiGraphics.fill(left + size - 2, top, left + size, top + size, FRAME_COLOR);
    }

    private int getPreviewSize() {
        return Math.max(64, Math.min(Math.min(this.width - 40, this.height - 130), 512));
    }

    private int getPreviewTop(int previewSize) {
        return Math.max(8, (this.height - previewSize) / 2);
    }

    private void renderFocus(GuiGraphicsExtractor guiGraphics) {
        ItemStack itemStack = zoomerBase.getItem(0);
        if (!itemStack.isEmpty()) {
            int previewSize = getPreviewSize();
            int left = (this.width - previewSize) / 2;
            int top = getPreviewTop(previewSize);
            int targetSize = getExportSize();
            ExportManager.renderPreviewToTarget(Minecraft.getInstance(), ExportTask.Type.ITEM, itemStack, null,
                    sliderValue, background, targetSize, rotX, rotY, 0.0F, 0.0F);
            if (ExportManager.renderTarget != null && ExportManager.renderTarget.getColorTextureView() != null) {
                guiGraphics.blit(ExportManager.renderTarget.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                        left, top, left + previewSize, top + previewSize, 0.0F, 1.0F, 0.0F, 1.0F);
            }
        }
    }

    private void resetSettings() {
        ExportManager.resetItemSettings();
        ExportManager.ExportSettings settings = ExportManager.getLastItemSettings();
        this.background = settings.background;
        this.sliderValue = settings.zoomPercent;
        this.prevSliderValue = this.sliderValue;
        this.exportSizeIndex = findExportSizeIndex(settings.exportSize);
        this.rotX = settings.rotX;
        this.rotY = settings.rotY;
        if (zoomSlider != null) {
            zoomSlider.updateValue(this.sliderValue);
        }
    }

    private void rememberCurrentSettings() {
        ExportManager.rememberItemSettings(sliderValue, background, getExportSize(), rotX, rotY);
    }

    @Override
    public void removed() {
        rememberCurrentSettings();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int findDefaultExportSizeIndex() {
        int defaultSize = ExportManager.getDefaultExportSize();
        return findExportSizeIndex(defaultSize);
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

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (super.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        if (event.button() == 0) {
            rotY += (float) dragX;
            rotX -= (float) dragY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalDelta, double delta) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalDelta, delta)) {
            return true;
        }
        float newSliderValue = this.sliderValue + (float) delta * 10F;
        setSliderValue(newSliderValue);
        if (zoomSlider != null) {
            zoomSlider.updateValue(this.sliderValue);
        }
        return true;
    }

    private class ZoomSlider extends AbstractSliderButton {
        public ZoomSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.translatable("gui.wikizoomer.zoom"), GuiItemZoomer.this.sliderValue / 1000.0);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("gui.wikizoomer.zoom").append(": " + (int) GuiItemZoomer.this.sliderValue + "%"));
        }

        @Override
        protected void applyValue() {
            GuiItemZoomer.this.setSliderValue((float) (this.value * 1000.0));
        }

        public void updateValue(float newValue) {
            this.value = newValue / 1000.0;
            this.updateMessage();
        }
    }
}
