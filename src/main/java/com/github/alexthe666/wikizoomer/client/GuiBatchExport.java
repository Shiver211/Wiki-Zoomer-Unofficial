package com.github.alexthe666.wikizoomer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiPageButtonList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.github.alexthe666.wikizoomer.ModConfig;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SideOnly(Side.CLIENT)
public class GuiBatchExport extends GuiScreen {
    private ModListWidget modList;
    private List<ModEntry> entries = new ArrayList<>();
    private boolean exportItems = true;
    private boolean exportEntities = true;
    private boolean exportAnimatedGif = ModConfig.exportAnimatedTexturesAsGif;
    private boolean useItemConfig = true;
    private boolean useEntityConfig = true;
    private boolean sliderModified = false;
    private ExportTask.Background background = ExportTask.Background.GREENSCREEN;
    private int exportSizeIndex = findDefaultExportSizeIndex();
    private static final int[] EXPORT_SIZES = ExportManager.getExportSizes();
    private float zoomPercent = 100F;
    private final GuiPageButtonList.GuiResponder sliderResponder = new GuiPageButtonList.GuiResponder() {
        @Override
        public void setEntryValue(int id, boolean value) {
        }

        @Override
        public void setEntryValue(int id, float value) {
            GuiBatchExport.this.setZoomValue(id, value);
        }

        @Override
        public void setEntryValue(int id, String value) {
        }
    };
    private GuiButton itemsButton;
    private GuiButton entitiesButton;
    private GuiButton gifButton;
    private GuiButton backgroundButton;
    private GuiButton resolutionButton;
    private GuiButton selectAllButton;
    private GuiButton clearButton;
    private GuiButton startButton;
    private GuiButton closeButton;

    @Override
    public void initGui() {
        super.initGui();
        this.entries = buildEntries();
        this.buttonList.clear();
        int buttonWidth = 120;
        int buttonHeight = 20;
        int spacing = 20;
        int rowWidth = buttonWidth * 3 + spacing * 2;
        int startX = this.width / 2 - rowWidth / 2;
        int row1Y = this.height - 100;
        int row2Y = row1Y + 24;
        int row3Y = row2Y + 24;
        int row4Y = row3Y + 24;
        int listTop = 32;
        int listBottom = row1Y - 8;
        this.modList = new ModListWidget(this.mc, this.width, this.height, listTop, listBottom, 20, this.entries);
        int col1X = startX;
        int col2X = startX + buttonWidth + spacing;
        int col3X = startX + (buttonWidth + spacing) * 2;
        this.itemsButton = new GuiButton(0, col1X, row1Y, buttonWidth, buttonHeight, "");
        this.entitiesButton = new GuiButton(1, col2X, row1Y, buttonWidth, buttonHeight, "");
        this.gifButton = new GuiButton(9, col3X, row1Y, buttonWidth, buttonHeight, "");
        net.minecraft.client.gui.GuiSlider.FormatHelper formatHelper = new net.minecraft.client.gui.GuiSlider.FormatHelper() {
            @Override
            public String getText(int id, String name, float value) {
                if (!sliderModified && (useItemConfig || useEntityConfig)) {
                    return I18n.format("gui.wikizoomer.zoom") + ": " + I18n.format("gui.wikizoomer.configured");
                }
                return name + ": " + (int)Math.round(value) + "%";
            }
        };
        net.minecraft.client.gui.GuiSlider slider = new net.minecraft.client.gui.GuiSlider(sliderResponder, 8, col1X, row2Y, I18n.format("gui.wikizoomer.zoom"), 1, 1000, zoomPercent, formatHelper);
        slider.width = buttonWidth;
        slider.height = buttonHeight;
        this.addButton(slider);
        this.backgroundButton = new GuiButton(2, col2X, row2Y, buttonWidth, buttonHeight, "");
        this.resolutionButton = new GuiButton(3, col3X, row2Y, buttonWidth, buttonHeight, "");
        this.selectAllButton = new GuiButton(4, col1X, row3Y, buttonWidth, buttonHeight, I18n.format("gui.wikizoomer.batch_select_all"));
        this.clearButton = new GuiButton(5, col2X, row3Y, buttonWidth, buttonHeight, I18n.format("gui.wikizoomer.batch_clear"));
        this.closeButton = new GuiButton(7, col3X, row3Y, buttonWidth, buttonHeight, I18n.format("gui.wikizoomer.close"));
        this.startButton = new GuiButton(6, startX, row4Y, rowWidth, buttonHeight, I18n.format("gui.wikizoomer.batch_start"));
        this.buttonList.add(this.itemsButton);
        this.buttonList.add(this.entitiesButton);
        this.buttonList.add(this.gifButton);
        this.buttonList.add(this.backgroundButton);
        this.buttonList.add(this.resolutionButton);
        this.buttonList.add(this.selectAllButton);
        this.buttonList.add(this.clearButton);
        this.buttonList.add(this.startButton);
        this.buttonList.add(this.closeButton);
        updateButtonLabels();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            exportItems = !exportItems;
            updateButtonLabels();
        } else if (button.id == 1) {
            exportEntities = !exportEntities;
            updateButtonLabels();
        } else if (button.id == 2) {
            background = background == ExportTask.Background.GREENSCREEN ? ExportTask.Background.TRANSPARENT : ExportTask.Background.GREENSCREEN;
            useItemConfig = false;
            useEntityConfig = false;
            updateButtonLabels();
        } else if (button.id == 3) {
            exportSizeIndex = (exportSizeIndex + 1) % EXPORT_SIZES.length;
            useItemConfig = false;
            useEntityConfig = false;
            updateButtonLabels();
        } else if (button.id == 4) {
            setAllSelected(true);
        } else if (button.id == 5) {
            setAllSelected(false);
        } else if (button.id == 6) {
            startExport();
        } else if (button.id == 7) {
            Minecraft.getMinecraft().displayGuiScreen(null);
        } else if (button.id == 9) {
            exportAnimatedGif = !exportAnimatedGif;
            updateButtonLabels();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.modList.drawScreen(mouseX, mouseY, partialTicks);
        this.drawCenteredString(this.fontRenderer, I18n.format("gui.wikizoomer.batch_title"), this.width / 2, 12, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.modList.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        this.modList.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        this.modList.handleMouseInput();
    }

    private void updateButtonLabels() {
        String onStr = I18n.format("gui.wikizoomer.on");
        String offStr = I18n.format("gui.wikizoomer.off");
        this.itemsButton.displayString = I18n.format("gui.wikizoomer.batch_items", exportItems ? onStr : offStr);
        this.entitiesButton.displayString = I18n.format("gui.wikizoomer.batch_entities", exportEntities ? onStr : offStr);
        if (this.gifButton != null) {
            this.gifButton.displayString = I18n.format("gui.wikizoomer.batch_gif", exportAnimatedGif ? onStr : offStr);
        }
        if (useItemConfig || useEntityConfig) {
            this.backgroundButton.displayString = I18n.format("gui.wikizoomer.background", I18n.format("gui.wikizoomer.configured"));
            this.resolutionButton.displayString = I18n.format("gui.wikizoomer.resolution_configured");
        } else {
            this.backgroundButton.displayString = I18n.format("gui.wikizoomer.background", getBackgroundLabel());
            this.resolutionButton.displayString = I18n.format("gui.wikizoomer.resolution", getExportSize(), getExportSize());
        }
    }

    private void startExport() {
        if (!exportItems && !exportEntities) {
            sendChat(I18n.format("gui.wikizoomer.batch_no_types"));
            return;
        }
        Set<String> modIds = getSelectedModIds();
        if (modIds.isEmpty()) {
            sendChat(I18n.format("gui.wikizoomer.batch_no_mods"));
            return;
        }
        List<ExportTask> tasks = new ArrayList<>();
        ZoomerConfigCache itemConfig = ZoomerConfigCache.lastItemConfig;
        ZoomerConfigCache entityConfig = ZoomerConfigCache.lastEntityConfig;
        if (exportItems) {
            float itemZoom = useItemConfig ? itemConfig.zoomPercent : zoomPercent;
            ExportTask.Background itemBg = useItemConfig ? itemConfig.background : background;
            int itemSize = useItemConfig ? itemConfig.exportSize : getExportSize();
            float itemRotX = useItemConfig ? itemConfig.rotX : 0F;
            float itemRotY = useItemConfig ? itemConfig.rotY : 0F;
            for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
                if (item == Items.AIR) {
                    continue;
                }
                ResourceLocation id = item.getRegistryName();
                if (id == null || !modIds.contains(id.getNamespace())) {
                    continue;
                }
                ExportTask task = ExportManager.createItemTask(new ItemStack(item), itemZoom, itemBg, itemSize, true, itemRotX, itemRotY, !exportAnimatedGif);
                if (task != null) {
                    tasks.add(task);
                }
            }
        }
        if (exportEntities) {
            float entityZoom = useEntityConfig ? entityConfig.zoomPercent : zoomPercent;
            ExportTask.Background entityBg = useEntityConfig ? entityConfig.background : background;
            int entitySize = useEntityConfig ? entityConfig.exportSize : getExportSize();
            float entityRotX = useEntityConfig ? entityConfig.rotX : -30F;
            float entityRotY = useEntityConfig ? entityConfig.rotY : 45F;
            int entityOffX = useEntityConfig ? entityConfig.offsetX : 0;
            int entityOffY = useEntityConfig ? entityConfig.offsetY : 0;
            for (EntityEntry entry : ForgeRegistries.ENTITIES.getValuesCollection()) {
                ResourceLocation id = entry.getRegistryName();
                if (id == null || !modIds.contains(id.getNamespace())) {
                    continue;
                }
                if (entry.getEntityClass() == null || Modifier.isAbstract(entry.getEntityClass().getModifiers())) {
                    continue;
                }
                if (ModConfig.onlyLivingEntities && !EntityLivingBase.class.isAssignableFrom(entry.getEntityClass())) {
                    continue;
                }
                ExportTask task = ExportManager.createEntityIdTask(id, entityZoom, entityBg, entitySize, true, entityRotX, entityRotY, entityOffX, entityOffY);
                if (task != null) {
                    tasks.add(task);
                }
            }
        }
        if (tasks.isEmpty()) {
            sendChat(I18n.format("gui.wikizoomer.batch_no_tasks"));
            return;
        }
        ExportManager.enqueueBatch(tasks);
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

    private Set<String> getSelectedModIds() {
        Set<String> modIds = new HashSet<>();
        for (ModEntry entry : entries) {
            if (entry.selected) {
                modIds.add(entry.modId);
            }
        }
        return modIds;
    }

    private void setAllSelected(boolean selected) {
        for (ModEntry entry : entries) {
            entry.selected = selected;
        }
    }

    private List<ModEntry> buildEntries() {
        List<ModContainer> mods = new ArrayList<>(Loader.instance().getModList());
        mods.sort(Comparator.comparing(ModContainer::getModId));
        List<ModEntry> list = new ArrayList<>();
        for (ModContainer mod : mods) {
            list.add(new ModEntry(mod.getModId(), mod.getName()));
        }
        return list;
    }

    private void sendChat(String message) {
        if (this.mc.player != null) {
            this.mc.player.sendMessage(new TextComponentString(message));
        }
    }

    private void setZoomValue(int id, float value) {
        zoomPercent = value;
        useItemConfig = false;
        useEntityConfig = false;
        sliderModified = true;
        updateButtonLabels();
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

    @SideOnly(Side.CLIENT)
    private static class ModListWidget extends GuiListExtended {
        private final List<ModEntry> entries;

        public ModListWidget(Minecraft mcIn, int widthIn, int heightIn, int topIn, int bottomIn, int slotHeightIn, List<ModEntry> entries) {
            super(mcIn, widthIn, heightIn, topIn, bottomIn, slotHeightIn);
            this.entries = entries;
        }

        @Override
        public GuiListExtended.IGuiListEntry getListEntry(int index) {
            return entries.get(index);
        }

        @Override
        protected int getSize() {
            return entries.size();
        }
    }

    @SideOnly(Side.CLIENT)
    private static class ModEntry implements GuiListExtended.IGuiListEntry {
        private final String modId;
        private final String modName;
        private boolean selected = false;

        public ModEntry(String modId, String modName) {
            this.modId = modId;
            this.modName = modName;
        }

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            Minecraft mc = Minecraft.getMinecraft();
            String label = (selected ? "[x] " : "[ ] ") + modName + " (" + modId + ")";
            mc.fontRenderer.drawString(label, x + 2, y + 2, selected ? 0x00FF00 : 0xFFFFFF);
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            selected = !selected;
            return true;
        }

        @Override
        public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        }
    }
}
