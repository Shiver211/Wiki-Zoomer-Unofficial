package com.shiver.wikizoomer.item;

import com.shiver.wikizoomer.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Consumer;

public class ItemEntityBinder extends Item {

    public ItemEntityBinder(Properties properties) {
        super(properties);
    }

    public ItemEntityBinder() {
        this(new Properties().stacksTo(1));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isEntityBound(stack);
    }

    public static Optional<EntityType<?>> getEntityType(ItemStack stack) {
        CompoundTag entityTag = stack.get(ModDataComponents.ENTITY_TAG);
        if (entityTag != null) {
            String idStr = entityTag.getStringOr("id", "");
            Identifier id = Identifier.tryParse(idStr);
            if (id != null) {
                return BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            }
        }
        return Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        if (!isEntityBound(stack)) {
            tooltip.accept(Component.translatable("item.wikizoomer.entity_binder.desc").withStyle(ChatFormatting.GRAY));
        }
        Boolean isPlayer = stack.get(ModDataComponents.IS_PLAYER_ENTITY);
        boolean isPlayerEntity = isPlayer != null && isPlayer;
        if (isPlayerEntity) {
            tooltip.accept(Component.translatable("entity.player.name").withStyle(ChatFormatting.GRAY));
        } else {
            getEntityType(stack).ifPresent(type -> tooltip.accept(type.getDescription()));
        }
    }

    public static boolean isEntityBound(ItemStack stack) {
        Boolean isPlayer = stack.get(ModDataComponents.IS_PLAYER_ENTITY);
        if (isPlayer != null && isPlayer) {
            return true;
        }
        return getEntityType(stack).isPresent();
    }

    @Override
    @NotNull
    public InteractionResult interactLivingEntity(@NotNull ItemStack stack, Player player, LivingEntity target, @NotNull InteractionHand hand) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, target.registryAccess());
        target.saveWithoutId(output);
        CompoundTag entityTag = output.buildResult();
        entityTag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());

        ItemStack stackReplacement = new ItemStack(this);
        stackReplacement.set(ModDataComponents.IS_PLAYER_ENTITY, target instanceof Player);
        stackReplacement.set(ModDataComponents.ENTITY_TAG, entityTag);

        if (!player.isCreative()) {
            stack.shrink(1);
        }
        player.swing(hand);
        if (!player.addItem(stackReplacement)) {
            ItemEntity itemEntity = player.drop(stackReplacement, false);
            if (itemEntity != null) {
                itemEntity.setNoPickUpDelay();
                itemEntity.setThrower(player);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
