package de.craftery.chestshopmanager;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import red.jackf.whereisit.api.SearchResult;
import red.jackf.whereisit.client.render.CurrentGradientHolder;
import red.jackf.whereisit.client.render.Rendering;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;

public class Commands {
    public static void createShop(CommandContext<FabricClientCommandSource> context, String name, String command) {
        Shop shop = Shop.getByName(name);

        if (shop == null) {
            shop = new Shop();
        }

        shop.setCommand(command);
        shop.setName(name);
        shop.saveOrUpdate();

        Messages.sendCommandFeedback(context, Messages.SHOP_CREATED, name, command);
    }

    public static void useShop(CommandContext<FabricClientCommandSource> context, String name) {
        Shop shop = Shop.getByName(name);

        if (shop == null) {
            Messages.sendCommandFeedback(context, Messages.SHOP_NOT_EXISTING);
            return;
        }

        Chestshopmanager.getInstance().setSelectedShop(shop.getName());
        Chestshopmanager.getInstance().setSelectedShopId(shop.getId());

        Messages.sendCommandFeedback(context, Messages.SHOP_SELECTED, shop.getName(), shop.getCommand());
    }

    public static void buyItem(CommandContext<FabricClientCommandSource> context, String itemName) {
        List<ChestShop> shops = ChestShop.getByItem(itemName).stream().filter(shop -> shop.getBuyPrice() != null).sorted((a, b) -> a.getBuyPrice()/a.getQuantity() > b.getBuyPrice()/b.getQuantity() ? 1 : -1).toList();

        if (shops.isEmpty()) {
            Messages.sendCommandFeedback(context, Messages.SHOP_NOT_FOUND, itemName);
            return;
        }

        listBuyPlaces(context, itemName, shops, 1);
    }

    private static void listBuyPlaces(CommandContext<FabricClientCommandSource> context, String itemName, List<ChestShop> shops, int page) {
        page--; // because the first page is 1

        context.getSource().sendFeedback(Text.empty());
        Messages.sendCommandFeedback(context, Messages.BUY_HEADER, itemName);
        for (int i = 0; i < shops.size(); i++) {
            if (i < page*5 || i >= (page+1)*5) continue;

            ChestShop shop = shops.get(i);

            Shop sellerShop = Shop.getById(shop.getShopId());

            assert sellerShop != null;

            MutableText base = Text.empty();

            float pricePerUnit = (float) shop.getBuyPrice() /shop.getQuantity();
            DecimalFormat df = new DecimalFormat("#.###");
            df.setRoundingMode(RoundingMode.CEILING);

            base.append(Text.literal(shop.getQuantity() + "x").formatted(Formatting.GREEN));
            base.append(Text.literal(" - ").formatted(Formatting.GRAY));
            base.append(Text.literal("$" + shop.getBuyPrice()).formatted(Formatting.GOLD));
            base.append(Text.literal(" ($" + df.format(pricePerUnit) + "/pc)").formatted(Formatting.GOLD));

            MutableText info = Text.empty();
            info.append(Text.literal(shop.getOwner()).formatted(Formatting.GRAY));

            info.append(Text.literal(" "));
            MutableText tp = Text.literal("[TP]").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cshop tp " + shop.getId()))).formatted(Formatting.GREEN);
            info.append(tp);

            context.getSource().sendFeedback(base);
            context.getSource().sendFeedback(info);
        }
    }

    public static void deleteChest(CommandContext<FabricClientCommandSource> context) {
        ClientPlayerEntity player = context.getSource().getPlayer();

        HitResult hit = player.raycast(20, 0, false);

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;

            int x = blockHit.getBlockPos().getX();
            int y = blockHit.getBlockPos().getY();
            int z = blockHit.getBlockPos().getZ();

            ChestShop shop = ChestShop.getByCoordinate(x,y,z);

            if (shop == null) {
                Messages.sendCommandFeedback(context, Messages.NO_SHOP_FOUND);
                return;
            }

            shop.delete();
            Messages.sendCommandFeedback(context, Messages.SHOP_DELETED);
        }
    }

    public static void tp(CommandContext<FabricClientCommandSource> context, Integer shopId) {
        ChestShop cs = ChestShop.getById(shopId);
        if (cs == null) return;

        Shop shop = Shop.getById(cs.getShopId());
        if (shop == null) return;

        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) return;

        Chestshopmanager.getInstance().setSelectedShop(shop.getName());
        Chestshopmanager.getInstance().setSelectedShopId(shop.getId());

        handler.sendCommand(shop.getCommand().replace("/", ""));

        // unfortunately this is very hacky. but it works :)
        // integration with Where Is it?
        Rendering.resetSearchTime();
        SearchResult search = SearchResult.builder(new BlockPos(cs.getX(), cs.getY(), cs.getZ())).build();
        Collection<SearchResult> list = new ArrayDeque<>();
        list.add(search);
        Rendering.addResults(list);

        CurrentGradientHolder.refreshColourScheme();
    }
}
