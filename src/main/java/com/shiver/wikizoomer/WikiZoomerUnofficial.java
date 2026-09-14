package com.shiver.wikizoomer;

import com.shiver.wikizoomer.block.BlockZoomer;
import com.shiver.wikizoomer.item.ItemDataCopier;
import com.shiver.wikizoomer.item.ItemEntityBinder;
import com.shiver.wikizoomer.tileentity.TileEntityEntityZoomer;
import com.shiver.wikizoomer.tileentity.TileEntityItemZoomer;
import com.shiver.wikizoomer.tileentity.TileEntityZoomerBase;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(WikiZoomerUnofficial.MODID)
public class WikiZoomerUnofficial {
    public static final String MODID = "wikizoomer";
    public static final Logger LOGGER = LogUtils.getLogger();


    // Deferred Registers
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    // Blocks
    public static final DeferredBlock<Block> ITEM_ZOOMER_BLOCK = BLOCKS.registerBlock("item_zoomer",
            props -> new BlockZoomer(props, true), () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(5, 20F));
    public static final DeferredBlock<Block> ENTITY_ZOOMER_BLOCK = BLOCKS.registerBlock("entity_zoomer",
            props -> new BlockZoomer(props, false), () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(5, 20F));

    // Block Items
    public static final DeferredItem<BlockItem> ITEM_ZOOMER_BLOCK_ITEM = ITEMS.registerItem("item_zoomer",
            props -> new BlockItem(ITEM_ZOOMER_BLOCK.get(), props) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
                    tooltip.accept(Component.translatable("block.wikizoomer.item_zoomer.desc0").withStyle(net.minecraft.ChatFormatting.GRAY));
                    tooltip.accept(Component.translatable("block.wikizoomer.item_zoomer.desc1").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            }, () -> new Item.Properties().useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> ENTITY_ZOOMER_BLOCK_ITEM = ITEMS.registerItem("entity_zoomer",
            props -> new BlockItem(ENTITY_ZOOMER_BLOCK.get(), props) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
                    tooltip.accept(Component.translatable("block.wikizoomer.entity_zoomer.desc0").withStyle(net.minecraft.ChatFormatting.GRAY));
                    tooltip.accept(Component.translatable("block.wikizoomer.entity_zoomer.desc1").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            }, () -> new Item.Properties().useBlockDescriptionPrefix());

    // Items
    public static final DeferredItem<ItemEntityBinder> ENTITY_BINDER_ITEM = ITEMS.registerItem("entity_binder", ItemEntityBinder::new, () -> new Item.Properties().stacksTo(1));
    public static final DeferredItem<ItemDataCopier> DATA_COPIER = ITEMS.registerItem("data_copier", ItemDataCopier::new, () -> new Item.Properties().stacksTo(1));

    // Block Entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityItemZoomer>> ITEM_ZOOMER_TE =
            BLOCK_ENTITIES.register("item_zoomer", () -> new BlockEntityType<>(TileEntityItemZoomer::new, ITEM_ZOOMER_BLOCK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityEntityZoomer>> ENTITY_ZOOMER_TE =
            BLOCK_ENTITIES.register("entity_zoomer", () -> new BlockEntityType<>(TileEntityEntityZoomer::new, ENTITY_ZOOMER_BLOCK.get()));

    // Creative Tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WIKI_ZOOMER_TAB = CREATIVE_MODE_TABS.register("wikizoomer", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.wikizoomer"))
            .icon(() -> ITEM_ZOOMER_BLOCK_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ITEM_ZOOMER_BLOCK_ITEM.get());
                output.accept(ENTITY_ZOOMER_BLOCK_ITEM.get());
                output.accept(ENTITY_BINDER_ITEM.get());
                output.accept(DATA_COPIER.get());
            })
            .build());

    public WikiZoomerUnofficial(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
    }
}
