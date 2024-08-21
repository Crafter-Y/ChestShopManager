package de.craftery.chestshopmanager;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.RoundingMode;
import java.text.DecimalFormat;
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
        Messages.sendCommandFeedback(context, Messages.BUY_HEADER, itemName);
        for (int i = 0; i < shops.size(); i++) {
            if (i < page*5 || i >= (page+1)*5) continue;

            ChestShop shop = shops.get(i);

            Shop sellerShop = Shop.getById(shop.getShopId());

            MutableText base = Text.empty();

            float pricePerUnit = (float) shop.getBuyPrice() /shop.getQuantity();
            DecimalFormat df = new DecimalFormat("#.###");
            df.setRoundingMode(RoundingMode.CEILING);

            base.append(Text.literal(shop.getQuantity() + "x").formatted(Formatting.GREEN));
            base.append(Text.literal(" - ").formatted(Formatting.GRAY));
            base.append(Text.literal("$" + shop.getBuyPrice()).formatted(Formatting.GOLD));
            base.append(Text.literal(" ($" + df.format(pricePerUnit) + "/pc)").formatted(Formatting.GOLD));
            base.append(Text.literal(" - ").formatted(Formatting.GRAY));
            base.append(Text.literal(shop.getOwner()).formatted(Formatting.GRAY));

            //Text.literal("[TP]").formatted(Formatting.GREEN).

            context.getSource().sendFeedback(base);
        }
    }

}
