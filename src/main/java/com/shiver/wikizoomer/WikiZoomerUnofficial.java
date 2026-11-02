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
    public static final DeferredBlock<Block> ITEM_ZOOMER_BLOCK = BLOCKS.register("item_zoomer",
            () -> new BlockZoomer(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(5, 20F), true));
    public static final DeferredBlock<Block> ENTITY_ZOOMER_BLOCK = BLOCKS.register("entity_zoomer",
            () -> new BlockZoomer(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(5, 20F), false));

    // Block Items
    public static final DeferredItem<BlockItem> ITEM_ZOOMER_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("item_zoomer", ITEM_ZOOMER_BLOCK);
    public static final DeferredItem<BlockItem> ENTITY_ZOOMER_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("entity_zoomer", ENTITY_ZOOMER_BLOCK);

    // Items
    public static final DeferredItem<ItemEntityBinder> ENTITY_BINDER_ITEM = ITEMS.register("entity_binder", ItemEntityBinder::new);
    public static final DeferredItem<ItemDataCopier> DATA_COPIER = ITEMS.register("data_copier", ItemDataCopier::new);

    // Block Entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityItemZoomer>> ITEM_ZOOMER_TE =
            BLOCK_ENTITIES.register("item_zoomer", () -> BlockEntityType.Builder.of(TileEntityItemZoomer::new, ITEM_ZOOMER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileEntityEntityZoomer>> ENTITY_ZOOMER_TE =
            BLOCK_ENTITIES.register("entity_zoomer", () -> BlockEntityType.Builder.of(TileEntityEntityZoomer::new, ENTITY_ZOOMER_BLOCK.get()).build(null));

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
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, ModConfig.SPEC);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
    }
}
