package de.martenschaefer.grindenchantments.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.world.WorldEvents;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import de.martenschaefer.grindenchantments.GrindEnchantmentsMod;
import de.martenschaefer.grindenchantments.event.GrindstoneEvents;

@Mixin(GrindstoneScreenHandler.class)
public abstract class GrindstoneScreenHandlerMixin extends ScreenHandler {
    @Shadow
    @Final
    public Inventory input;
    @Final
    @Shadow
    public Inventory result;

    @Unique
    private PlayerEntity grindenchantments_player;

    protected GrindstoneScreenHandlerMixin(ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(at = @At("RETURN"), method = "<init>(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/screen/ScreenHandlerContext;)V")
    private void onReturnConstructor(int syncId, PlayerInventory playerInventory, final ScreenHandlerContext context, CallbackInfo ci) {
        this.grindenchantments_player = playerInventory.player;
    }

    @Inject(at = @At("RETURN"), method = "updateResult", cancellable = true)
    private void onUpdateResult(CallbackInfo ci) {
        ItemStack input1 = this.input.getStack(0);
        ItemStack input2 = this.input.getStack(1);

        // PlayerEntity player = ((PlayerInventory) this.slots.get(3).inventory).player;
        PlayerEntity player = this.grindenchantments_player;

        @Nullable
        ItemStack result = GrindstoneEvents.UPDATE_RESULT.invoker().onUpdateResult(input1, input2, player);

        if (result != null) {
            this.result.setStack(0, result);
            this.sendContentUpdates();
            ci.cancel();
        }
    }

    @Inject(method = "transferSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;onTakeItem(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void changeStackForOnTakeItem(PlayerEntity player, int index, CallbackInfoReturnable<ItemStack> cir, ItemStack itemStack, Slot slot) {
        slot.onTakeItem(player, itemStack);
    }

    @Redirect(method = "transferSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;onTakeItem(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;)V", ordinal = 0))
    private void removeVanillaOnTakeItemCall(Slot slot, PlayerEntity player, ItemStack stack) {
        // Remove call
    }

    @Mixin(targets = "net/minecraft/screen/GrindstoneScreenHandler$2")
    public static class Anonymous2Mixin extends Slot {
        @Shadow
        @Final
        GrindstoneScreenHandler field_16777;

        public Anonymous2Mixin(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Inject(method = "canInsert(Lnet/minecraft/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
        private void canInsertBooks(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
            Inventory input = this.field_16777.input;

            cir.setReturnValue(cir.getReturnValueZ() || GrindstoneEvents.CAN_INSERT.invoker().canInsert(stack, input.getStack(1), 0));
        }
    }

    @Mixin(targets = "net/minecraft/screen/GrindstoneScreenHandler$3")
    public static class Anonymous3Mixin extends Slot {
        @Shadow
        @Final
        GrindstoneScreenHandler field_16778;

        public Anonymous3Mixin(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Inject(method = "canInsert(Lnet/minecraft/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
        private void canInsertBooks(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
            Inventory input = this.field_16778.input;

            cir.setReturnValue(cir.getReturnValueZ() || GrindstoneEvents.CAN_INSERT.invoker().canInsert(stack, input.getStack(0), 1));
        }
    }

    @Mixin(targets = "net/minecraft/screen/GrindstoneScreenHandler$4")
    public static abstract class Anonymous4Mixin extends Slot {
        @Final
        @Shadow
        ScreenHandlerContext field_16779;
        @Shadow
        @Final
        GrindstoneScreenHandler field_16780;

        public Anonymous4Mixin(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Inject(method = "onTakeItem", at = @At("HEAD"), cancellable = true)
        private void onTakeResult(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
            Inventory input = this.field_16780.input;

            ItemStack input1 = input.getStack(0);
            ItemStack input2 = input.getStack(1);

            boolean success = GrindstoneEvents.TAKE_RESULT.invoker().onTakeResult(input1, input2, stack, player, input);

            if (success) {
                this.field_16779.run((world, pos) -> world.syncWorldEvent(WorldEvents.GRINDSTONE_USED, pos, 0)); // Plays grindstone sound
                ci.cancel();
            }
        }

        /**
         * @author mschae23
         */
        @Override
        public boolean canTakeItems(PlayerEntity player) {
            Inventory input = this.field_16780.input;

            ItemStack input1 = input.getStack(0);
            ItemStack input2 = input.getStack(1);

            return GrindstoneEvents.CAN_TAKE_RESULT.invoker().canTakeResult(input1, input2, player);
        }
    }
}
