package com.github.alexthe666.wikizoomer.client;

import com.github.alexthe666.wikizoomer.tileentity.TileEntityZoomerBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiPageButtonList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.client.config.GuiSlider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.util.List;

import com.github.alexthe666.wikizoomer.ModConfig;
import com.github.alexthe666.wikizoomer.client.ExportManager;
import com.github.alexthe666.wikizoomer.client.ExportTask;
import com.github.alexthe666.wikizoomer.client.GuiBatchExport;
import com.github.alexthe666.wikizoomer.client.ZoomerConfigCache;

@SideOnly(Side.CLIENT)
public class GuiItemZoomer extends GuiScreen {

    private final GuiPageButtonList.GuiResponder sliderResponder;
    private TileEntityZoomerBase zoomerBase;
    private ExportTask.Background background = ExportTask.Background.GREENSCREEN;
    private float sliderValue = 100;
    private int exportSizeIndex = findDefaultExportSizeIndex();
    private static final int[] EXPORT_SIZES = ExportManager.getExportSizes();
    private float rotX = 0F;
    private float rotY = 0F;
    private boolean dragging = false;
    private int lastMouseX, lastMouseY;

    public GuiItemZoomer(TileEntityZoomerBase zoomerBase) {
        super();
        this.zoomerBase = zoomerBase;
        applyLastConfig();
        sliderResponder = new GuiPageButtonList.GuiResponder() {
            @Override
            public void setEntryValue(int id, boolean value) {

            }

            @Override
            public void setEntryValue(int id, float value) {
                GuiItemZoomer.this.setSliderValue(id, value);
            }

            @Override
            public void setEntryValue(int id, String value) {

            }
        };
        initGui();
    }

    private void applyLastConfig() {
        ZoomerConfigCache lastConfig = ZoomerConfigCache.lastItemConfig;
        sliderValue = lastConfig.zoomPercent;
        background = lastConfig.background;
        exportSizeIndex = findExportSizeIndex(lastConfig.exportSize);
        rotX = lastConfig.rotX;
        rotY = lastConfig.rotY;
    }

    private void setSliderValue(int i, float sliderValue) {
        this.sliderValue = sliderValue;
    }

    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        int i = (this.width) / 2;
        int j = (this.height - 166) / 2;
        String exit = I18n.format("gui.wikizoomer.close");
        String backgroundLabel = I18n.format("gui.wikizoomer.background", getBackgroundLabel());
        String exportPng = I18n.format("gui.wikizoomer.export_png");
        String batchExport = I18n.format("gui.wikizoomer.batch_export");
        String resolutionLabel = I18n.format("gui.wikizoomer.resolution", getExportSize(), getExportSize());
        int buttonWidth = 120;
        int buttonHeight = 20;
        int spacing = 20;
        int rowWidth = buttonWidth * 3 + spacing * 2;
        int startX = i - rowWidth / 2;
        int col1X = startX;
        int col2X = startX + buttonWidth + spacing;
        int col3X = startX + (buttonWidth + spacing) * 2;
        net.minecraft.client.gui.GuiSlider.FormatHelper formatHelper = new net.minecraft.client.gui.GuiSlider.FormatHelper() {
            @Override
            public String getText(int id, String name, float value) {
                return name + ": " + (int)Math.round(value) + "%";
            }
        };
        int row1Y = j + 180;
        int row2Y = row1Y + 22;
        int row3Y = row2Y + 22;
        net.minecraft.client.gui.GuiSlider slider = new net.minecraft.client.gui.GuiSlider(sliderResponder, 0, col1X, row1Y, I18n.format("gui.wikizoomer.zoom"), 1, 1000, sliderValue, formatHelper);
        slider.width = buttonWidth;
        slider.height = buttonHeight;
        this.addButton(slider);
        this.addButton(new GuiButton(1, col2X, row1Y, buttonWidth, buttonHeight, backgroundLabel));
        this.addButton(new GuiButton(3, col3X, row1Y, buttonWidth, buttonHeight, getExportButtonText()));
        this.addButton(new GuiButton(5, col1X, row2Y, buttonWidth, buttonHeight, resolutionLabel));
        this.addButton(new GuiButton(4, col2X, row2Y, buttonWidth, buttonHeight, batchExport));
        this.addButton(new GuiButton(2, col3X, row2Y, buttonWidth, buttonHeight, exit));
        this.addButton(new GuiButton(6, col1X, row3Y, buttonWidth, buttonHeight, I18n.format("gui.wikizoomer.clear_config")));
        for (GuiButton button : this.buttonList) {
            button.enabled = true;
        }
    }

    private String getExportButtonText() {
        ItemStack stack = zoomerBase != null ? zoomerBase.getStackInSlot(0) : ItemStack.EMPTY;
        boolean animated = ModConfig.exportAnimatedTexturesAsGif && ExportManager.isItemAnimated(stack);
        if (animated) {
            if (isShiftKeyDown()) {
                return I18n.format("gui.wikizoomer.export_png");
            }
            return I18n.format("gui.wikizoomer.export_gif");
        }
        return I18n.format("gui.wikizoomer.export_png");
    }

    private GuiButton getButtonById(int id) {
        for (GuiButton btn : this.buttonList) {
            if (btn.id == id) {
                return btn;
            }
        }
        return null;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.enabled && button.id == 1) {
            background = background == ExportTask.Background.GREENSCREEN ? ExportTask.Background.TRANSPARENT : ExportTask.Background.GREENSCREEN;
        }
        if (button.enabled && button.id == 2) {
            saveConfig();
            Minecraft.getMinecraft().displayGuiScreen(null);
            return;
        }
        if (button.enabled && button.id == 3) {
            saveConfig();
            ItemStack stack = zoomerBase.getStackInSlot(0);
            boolean forcePng = isShiftKeyDown();
            ExportTask task = ExportManager.createItemTask(stack, sliderValue, background, getExportSize(), false, rotX, rotY, forcePng);
            if (task == null) {
                if (Minecraft.getMinecraft().player != null) {
                    Minecraft.getMinecraft().player.sendMessage(new TextComponentString(I18n.format("gui.wikizoomer.export_no_item")));
                }
            } else {
                ExportManager.enqueue(task);
            }
        }
        if (button.enabled && button.id == 4) {
            saveConfig();
            Minecraft.getMinecraft().displayGuiScreen(new GuiBatchExport());
        }
        if (button.enabled && button.id == 6) {
            resetToDefaults();
            return;
        }
        if (button.enabled && button.id == 5) {
            exportSizeIndex = (exportSizeIndex + 1) % EXPORT_SIZES.length;
        }
        initGui();
    }



    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (Minecraft.getMinecraft() != null) {
            try {
                if (background == ExportTask.Background.GREENSCREEN) {
                    int integer = 0X4CFF00;
                    float brightness = 1.0F;
                    int alpha = 255;
                    float f = (float) (integer >> 16 & 255) / 255.0F;
                    float f1 = (float) (integer >> 8 & 255) / 255.0F;
                    float f2 = (float) (integer & 255) / 255.0F;
                    drawRect(0, 0, this.width, this.height, MathHelper.rgb(f * brightness, f1 * brightness, f2 * brightness) | alpha << 24);
                } else {
                    this.drawDefaultBackground();
                }
            } catch (Exception e) {

            }
        }
        // Clear depth buffer so background doesn't clip the preview
        net.minecraft.client.renderer.GlStateManager.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);

        int i = (this.width) / 2;
        int j = (this.height) / 2;
        ItemStack stack = zoomerBase.getStackInSlot(0);
        int boxSize = Math.min(this.width, this.height) / 2;
        float displayScale = (float)boxSize / getExportSize() * sliderValue / 100F * 12F;
        if (!stack.isEmpty()) {
            // Set up wide-depth orthographic projection like ExportManager
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, this.width, this.height, 0.0D, -10000.0D, 10000.0D);
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableDepth();
            GlStateManager.translate(i, j, 0F);
            GlStateManager.scale(displayScale, displayScale, displayScale);
            GlStateManager.enableLighting();
            RenderHelper.enableGUIStandardItemLighting();
            renderItemPreview(stack, -8, -8);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
        }

        // Draw red crop box (hollow, 1px border) above the preview but below buttons.
        int boxX = (this.width - boxSize) / 2;
        int boxY = (this.height - boxSize) / 2;
        GlStateManager.disableDepth();
        drawRect(boxX, boxY, boxX + boxSize, boxY + 1, 0xFFFF0000);
        drawRect(boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, 0xFFFF0000);
        drawRect(boxX, boxY, boxX + 1, boxY + boxSize, 0xFFFF0000);
        drawRect(boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, 0xFFFF0000);
        GlStateManager.enableDepth();

        GuiButton exportBtn = getButtonById(3);
        if (exportBtn != null) {
            exportBtn.displayString = getExportButtonText();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void renderItemPreview(ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        net.minecraft.client.renderer.RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        renderItem.zLevel += 50.0F;
        try {
            IBakedModel model = renderItem.getItemModelWithOverrides(stack, null, Minecraft.getMinecraft().player);
            GlStateManager.pushMatrix();
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(516, 0.1F);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.translate((float)x, (float)y, 100.0F + renderItem.zLevel);
            GlStateManager.translate(8.0F, 8.0F, 0.0F);
            GlStateManager.scale(1.0F, -1.0F, 1.0F);
            GlStateManager.scale(16.0F, 16.0F, 16.0F);
            if (model.isGui3d()) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
            model = ForgeHooksClient.handleCameraTransforms(model, ItemCameraTransforms.TransformType.GUI, false);
            if (stack.getItem() instanceof ItemBlock) {
                GlStateManager.rotate(rotX, 1, 0, 0);
                GlStateManager.rotate(rotY, 0, 1, 0);
            }
            renderItem.renderItem(stack, model);
            GlStateManager.disableAlpha();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableLighting();
            GlStateManager.popMatrix();
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
        } finally {
            renderItem.zLevel -= 50.0F;
        }
    }


    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && isMouseInPreviewBox(mouseX, mouseY) && !isMouseOverButton(mouseX, mouseY)) {
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (state == 0) {
            dragging = false;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (dragging && clickedMouseButton == 0) {
            int dx = mouseX - lastMouseX;
            int dy = mouseY - lastMouseY;
            rotY += dx * 0.5F;
            rotX -= dy * 0.5F;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dWheel != 0 && isMouseInPreviewBox(org.lwjgl.input.Mouse.getEventX() * this.width / this.mc.displayWidth, this.height - org.lwjgl.input.Mouse.getEventY() * this.height / this.mc.displayHeight - 1)) {
            if (dWheel > 0) {
                sliderValue = Math.min(1000, sliderValue + 5);
            } else {
                sliderValue = Math.max(1, sliderValue - 5);
            }
            initGui();
        }
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    private static int findDefaultExportSizeIndex() {
        int defaultSize = ExportManager.getDefaultExportSize();
        for (int i = 0; i < EXPORT_SIZES.length; i++) {
            if (EXPORT_SIZES[i] == defaultSize) {
                return i;
            }
        }
        return EXPORT_SIZES.length - 1;
    }

    private int getExportSize() {
        return EXPORT_SIZES[exportSizeIndex];
    }

    private String getBackgroundLabel() {
        return background == ExportTask.Background.GREENSCREEN
                ? I18n.format("gui.wikizoomer.background.greenscreen")
                : I18n.format("gui.wikizoomer.background.transparent");
    }

    private boolean isMouseInPreviewBox(int mouseX, int mouseY) {
        int boxSize = Math.min(this.width, this.height) / 2;
        int boxX = (this.width - boxSize) / 2;
        int boxY = (this.height - boxSize) / 2;
        return mouseX >= boxX && mouseX < boxX + boxSize && mouseY >= boxY && mouseY < boxY + boxSize;
    }

    private boolean isMouseOverButton(int mouseX, int mouseY) {
        for (GuiButton button : this.buttonList) {
            if (button.visible && mouseX >= button.x && mouseY >= button.y && mouseX < button.x + button.width && mouseY < button.y + button.height) {
                return true;
            }
        }
        return false;
    }

    private void saveConfig() {
        ZoomerConfigCache.lastItemConfig.zoomPercent = sliderValue;
        ZoomerConfigCache.lastItemConfig.background = background;
        ZoomerConfigCache.lastItemConfig.exportSize = getExportSize();
        ZoomerConfigCache.lastItemConfig.rotX = rotX;
        ZoomerConfigCache.lastItemConfig.rotY = rotY;
    }

    private void resetToDefaults() {
        ZoomerConfigCache defaults = ZoomerConfigCache.getItemDefaults();
        sliderValue = defaults.zoomPercent;
        background = defaults.background;
        exportSizeIndex = findExportSizeIndex(defaults.exportSize);
        rotX = defaults.rotX;
        rotY = defaults.rotY;
        saveConfig();
        initGui();
    }

    private int findExportSizeIndex(int size) {
        for (int i = 0; i < EXPORT_SIZES.length; i++) {
            if (EXPORT_SIZES[i] == size) {
                return i;
            }
        }
        return findDefaultExportSizeIndex();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        saveConfig();
    }
}
