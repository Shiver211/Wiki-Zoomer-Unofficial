package com.shiver.wikizoomer.item;

import com.shiver.wikizoomer.WikiZoomerUnofficialClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;

public class ItemDataCopier extends Item {

    public ItemDataCopier(Properties properties) {
        super(properties);
    }

    public ItemDataCopier() {
        this(new Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.wikizoomer.data_copier.desc").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.wikizoomer.data_copier.desc2").withStyle(ChatFormatting.GRAY));
        if (WikiZoomerUnofficialClient.dataMimic != null) {
            tooltip.accept(Component.translatable("item.wikizoomer.data_copier.tracking",
                    WikiZoomerUnofficialClient.dataMimic.getDisplayName()).withStyle(ChatFormatting.GREEN));
        }
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (player.level().isClientSide()) {
            WikiZoomerUnofficialClient.dataMimic = entity;
            player.sendSystemMessage(Component.translatable("item.wikizoomer.data_copier.success", entity.getDisplayName()));
        }
        return InteractionResult.SUCCESS;
    }
}
