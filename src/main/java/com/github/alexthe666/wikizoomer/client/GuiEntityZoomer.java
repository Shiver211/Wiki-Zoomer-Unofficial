package com.github.alexthe666.wikizoomer.client;

import com.github.alexthe666.wikizoomer.tileentity.TileEntityEntityZoomer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiPageButtonList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.client.config.GuiSlider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.github.alexthe666.wikizoomer.client.ExportManager;
import com.github.alexthe666.wikizoomer.client.ExportTask;
import com.github.alexthe666.wikizoomer.client.GuiBatchExport;
import com.github.alexthe666.wikizoomer.client.ZoomerConfigCache;

import java.io.IOException;

import static net.minecraft.client.Minecraft.getMinecraft;

@SideOnly(Side.CLIENT)
public class GuiEntityZoomer extends GuiScreen {
    private final GuiPageButtonList.GuiResponder sliderResponder;
    private TileEntityEntityZoomer zoomerBase;
    private ExportTask.Background background = ExportTask.Background.GREENSCREEN;
    private float sliderValue = 100;
    private int exportSizeIndex = findDefaultExportSizeIndex();
    private static final int[] EXPORT_SIZES = ExportManager.getExportSizes();
    private float rotX = -30F;
    private float rotY = 45F;
    private int offsetX = 0;
    private int offsetY = 0;
    private boolean dragging = false;
    private int lastMouseX, lastMouseY;

    public GuiEntityZoomer(TileEntityEntityZoomer zoomerBase) {
        super();
        this.zoomerBase = zoomerBase;
        applyLastConfig();
        sliderResponder = new GuiPageButtonList.GuiResponder() {
            @Override
            public void setEntryValue(int id, boolean value) {

            }

            @Override
            public void setEntryValue(int id, float value) {
                GuiEntityZoomer.this.setSliderValue(id, value);
            }

            @Override
            public void setEntryValue(int id, String value) {

            }
        };
        initGui();
    }

    private void applyLastConfig() {
        ZoomerConfigCache lastConfig = ZoomerConfigCache.lastEntityConfig;
        sliderValue = lastConfig.zoomPercent;
        background = lastConfig.background;
        exportSizeIndex = findExportSizeIndex(lastConfig.exportSize);
        rotX = lastConfig.rotX;
        rotY = lastConfig.rotY;
        offsetX = lastConfig.offsetX;
        offsetY = lastConfig.offsetY;
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
        this.addButton(new GuiButton(3, col3X, row1Y, buttonWidth, buttonHeight, exportPng));
        this.addButton(new GuiButton(5, col1X, row2Y, buttonWidth, buttonHeight, resolutionLabel));
        this.addButton(new GuiButton(4, col2X, row2Y, buttonWidth, buttonHeight, batchExport));
        this.addButton(new GuiButton(2, col3X, row2Y, buttonWidth, buttonHeight, exit));
        this.addButton(new GuiButton(6, col1X, row3Y, buttonWidth, buttonHeight, I18n.format("gui.wikizoomer.clear_config")));
        for (GuiButton button : this.buttonList) {
            button.enabled = true;
        }
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
            Entity entity = zoomerBase.getCachedEntity();
            ExportTask task = ExportManager.createEntityTask(entity, sliderValue, background, getExportSize(), false, rotX, rotY, offsetX, offsetY);
            if (task == null) {
                if (Minecraft.getMinecraft().player != null) {
                    Minecraft.getMinecraft().player.sendMessage(new TextComponentString(I18n.format("gui.wikizoomer.export_no_entity")));
                }
            } else {
                ExportManager.enqueue(task);
            }
        }
        if (button.enabled && button.id == 4) {
            saveConfig();
            Minecraft.getMinecraft().displayGuiScreen(new GuiBatchExport());
        }
        if (button.enabled && button.id == 5) {
            exportSizeIndex = (exportSizeIndex + 1) % EXPORT_SIZES.length;
        }
        if (button.enabled && button.id == 6) {
            resetToDefaults();
            return;
        }
        initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (getMinecraft() != null) {
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

        Entity renderEntity = zoomerBase.getCachedEntity();
        int boxSize = Math.min(this.width, this.height) / 2;
        float previewPixelScale = (float)boxSize / getExportSize();
        int centerX = this.width / 2 + Math.round(offsetX * previewPixelScale);
        int centerY = this.height / 2 + Math.round(offsetY * previewPixelScale);
        float displayScale = (float)boxSize / getExportSize() * (sliderValue / 100F) * 100F;
        if (renderEntity != null) {
            // Set up wide-depth orthographic projection like ExportManager
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, this.width, this.height, 0.0D, -10000.0D, 10000.0D);
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.translate(0.0F, 0.0F, 500.0F);
            float f = 0.75F;
            float f1 = Math.max(renderEntity.width, renderEntity.height);
            if ((double)f1 > 1.0D) {
                f /= f1;
            }
            if(renderEntity instanceof EntityLivingBase){
                drawEntityOnScreen(centerX, centerY, f * displayScale, rotX, rotY, (EntityLivingBase)renderEntity);
            }
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

        // Restore clean GUI render state for buttons and text
        GlStateManager.disableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    public static void drawEntityOnScreen(int posX, int posY, float scale, float rotX, float rotY, EntityLivingBase entity) {
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translate(posX, posY, 0.0F);
        GlStateManager.scale((-scale), scale, scale);
        GlStateManager.rotate(rotX, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(180 - rotY, 0, -1, 0);
        GlStateManager.rotate(180, 1.0F, 0.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        RenderManager rendermanager = Minecraft.getMinecraft().getRenderManager();
        rendermanager.setPlayerViewY(180.0F);
        rendermanager.setRenderShadow(false);
        entity.rotationYawHead = 0;
        try{
            rendermanager.renderEntity(entity, 0.0D, -entity.height / 2.0D, 0.0D, 0.0F, 1.0F, false);
        }catch (Exception e){
            System.out.println("Could not render entity due to interference with vanilla code by another mod");
        }
        rendermanager.setRenderShadow(true);
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        boolean shift = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT) || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RSHIFT);
        int moveAmount = shift ? 1 : 5;
        if (keyCode == org.lwjgl.input.Keyboard.KEY_W) {
            offsetY -= moveAmount;
        } else if (keyCode == org.lwjgl.input.Keyboard.KEY_S) {
            offsetY += moveAmount;
        } else if (keyCode == org.lwjgl.input.Keyboard.KEY_A) {
            offsetX -= moveAmount;
        } else if (keyCode == org.lwjgl.input.Keyboard.KEY_D) {
            offsetX += moveAmount;
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
            rotY -= dx * 0.5F;
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

    private void saveConfig() {
        ZoomerConfigCache.lastEntityConfig.zoomPercent = sliderValue;
        ZoomerConfigCache.lastEntityConfig.background = background;
        ZoomerConfigCache.lastEntityConfig.exportSize = getExportSize();
        ZoomerConfigCache.lastEntityConfig.rotX = rotX;
        ZoomerConfigCache.lastEntityConfig.rotY = rotY;
        ZoomerConfigCache.lastEntityConfig.offsetX = offsetX;
        ZoomerConfigCache.lastEntityConfig.offsetY = offsetY;
    }

    private void resetToDefaults() {
        ZoomerConfigCache defaults = ZoomerConfigCache.getEntityDefaults();
        sliderValue = defaults.zoomPercent;
        background = defaults.background;
        exportSizeIndex = findExportSizeIndex(defaults.exportSize);
        rotX = defaults.rotX;
        rotY = defaults.rotY;
        offsetX = defaults.offsetX;
        offsetY = defaults.offsetY;
        saveConfig();
        initGui();
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
}
