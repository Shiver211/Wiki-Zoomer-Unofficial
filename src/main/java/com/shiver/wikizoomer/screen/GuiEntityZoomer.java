package com.shiver.wikizoomer.screen;

import com.shiver.wikizoomer.client.ExportManager;
import com.shiver.wikizoomer.client.ExportTask;
import com.shiver.wikizoomer.tileentity.TileEntityEntityZoomer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class GuiEntityZoomer extends Screen {
    private final TileEntityEntityZoomer zoomerBase;
    private ExportTask.Background background = ExportTask.Background.GREENSCREEN;
    private float sliderValue = 100;
    private float prevSliderValue = sliderValue;
    private int exportSizeIndex = findDefaultExportSizeIndex();
    private static final int[] EXPORT_SIZES = ExportManager.getExportSizes();
    private float rotX = 30F;
    private float rotY = 45F;
    private ZoomSlider zoomSlider;
    private float offsetX = 0.0F;
    private float offsetY = 0.0F;
    private static final int FRAME_COLOR = 0xFFFF0000;

    public GuiEntityZoomer(TileEntityEntityZoomer zoomerBase) {
        super(Component.translatable("entity_zoomer"));
        this.zoomerBase = zoomerBase;
        ExportManager.ExportSettings settings = ExportManager.getLastEntitySettings();
        this.background = settings.background;
        this.sliderValue = settings.zoomPercent;
        this.prevSliderValue = this.sliderValue;
        this.exportSizeIndex = findExportSizeIndex(settings.exportSize);
        this.rotX = settings.rotX;
        this.rotY = settings.rotY;
        this.offsetX = settings.offsetX;
        this.offsetY = settings.offsetY;
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
                    Entity renderEntity = zoomerBase.getCachedEntity();
                    ExportManager.rememberEntitySettings(sliderValue, background, getExportSize(), rotX, rotY, offsetX, offsetY);
                    ExportTask task = ExportManager.createEntityTask(renderEntity, sliderValue, background, getExportSize(), false, rotX, rotY, offsetX, offsetY);
                    if (task == null) {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("gui.wikizoomer.export_no_entity"));
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
                    ExportManager.rememberEntitySettings(sliderValue, background, getExportSize(), rotX, rotY, offsetX, offsetY);
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
        Entity renderEntity = zoomerBase.getCachedEntity();
        if (renderEntity != null) {
            int previewSize = getPreviewSize();
            int left = (this.width - previewSize) / 2;
            int top = getPreviewTop(previewSize);
            int targetSize = getExportSize();
            ExportManager.renderPreviewToTarget(Minecraft.getInstance(), ExportTask.Type.ENTITY, ItemStack.EMPTY, renderEntity,
                    sliderValue, background, targetSize, rotX, rotY, offsetX, offsetY);
            ExportManager.blitPreview(guiGraphics, left, top, previewSize);
        }
        prevSliderValue = sliderValue;
    }

    private void resetSettings() {
        ExportManager.resetEntitySettings();
        ExportManager.ExportSettings settings = ExportManager.getLastEntitySettings();
        this.background = settings.background;
        this.sliderValue = settings.zoomPercent;
        this.prevSliderValue = this.sliderValue;
        this.exportSizeIndex = findExportSizeIndex(settings.exportSize);
        this.rotX = settings.rotX;
        this.rotY = settings.rotY;
        this.offsetX = settings.offsetX;
        this.offsetY = settings.offsetY;
        if (zoomSlider != null) {
            zoomSlider.updateValue(this.sliderValue);
        }
    }

    private void rememberCurrentSettings() {
        ExportManager.rememberEntitySettings(sliderValue, background, getExportSize(), rotX, rotY, offsetX, offsetY);
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

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        float step = event.hasShiftDown() ? 1.0F : 5.0F;
        if (event.key() == GLFW.GLFW_KEY_A) {
            offsetX -= step;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_D) {
            offsetX += step;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_W) {
            offsetY -= step;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_S) {
            offsetY += step;
            return true;
        }
        return false;
    }

    private class ZoomSlider extends AbstractSliderButton {
        public ZoomSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.translatable("gui.wikizoomer.zoom"), GuiEntityZoomer.this.sliderValue / 1000.0);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("gui.wikizoomer.zoom").append(": " + (int) GuiEntityZoomer.this.sliderValue + "%"));
        }

        @Override
        protected void applyValue() {
            GuiEntityZoomer.this.setSliderValue((float) (this.value * 1000.0));
        }

        public void updateValue(float newValue) {
            this.value = newValue / 1000.0;
            this.updateMessage();
        }
    }
}
