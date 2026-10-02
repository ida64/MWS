package dev.paging.mws.registry;

import dev.paging.mws.Mws;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class MwsItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mws.MODID);

    public static final DeferredItem<Item> SERVER_BLADE = ITEMS.register("server_blade",
            () -> new Item(new Item.Properties().stacksTo(16)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable("item.mws.server_blade.tooltip").withStyle(ChatFormatting.GRAY));
                }
            });
    public static final DeferredItem<BucketItem> SLUDGE_BUCKET = ITEMS.register("sludge_bucket",
            () -> new BucketItem(MwsFluids.SLUDGE.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    static {
        ITEMS.registerSimpleBlockItem(MwsBlocks.SERVER_RACK);
        ITEMS.registerSimpleBlockItem(MwsBlocks.UPS);
        ITEMS.registerSimpleBlockItem(MwsBlocks.CHILLER);
        ITEMS.registerSimpleBlockItem(MwsBlocks.WATER_INTAKE);
    }

    private MwsItems() {
    }
}
