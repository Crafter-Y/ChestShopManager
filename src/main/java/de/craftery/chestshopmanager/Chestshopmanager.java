package de.craftery.chestshopmanager;

import com.mojang.brigadier.arguments.StringArgumentType;
import de.craftery.chestshopmanager.db.HibernateConfigurator;
import de.craftery.chestshopmanager.db.TestDatabaseConnection;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import net.minecraft.command.CommandSource;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class Chestshopmanager implements ModInitializer {
    private static final Pattern BUY_PATTERN = Pattern.compile("^Buy ((\\d+(,?))+) for \\$((\\d+(,?))+)$");
    private static final Pattern SELL_PATTERN = Pattern.compile("^Sell ((\\d+(,?))+) for \\$((\\d+(,?))+)$");

    private static final MutableText PREFIX = Text.literal("[ChestShopManager] ").formatted(Formatting.LIGHT_PURPLE);

    private int x = 0;
    private int y = 0;
    private int z = 0;

    private String owner = "";
    private int stock = 0;
    private String item = "";

    private int qty = 0;
    private Integer buyPrice = null;
    private Integer sellPrice = null;
    private int invSize = 1728;

    private int waitingTicks = 0;

    private String selectedShop = null;
    private long selectedShopId = 0;

    private final Set<String> knownItems = new HashSet<>();

    @Override
    public void onInitialize() {
        new TestDatabaseConnection();
        HibernateConfigurator.addEntity(ChestShop.class);
        HibernateConfigurator.addEntity(Shop.class);

        knownItems.addAll(ChestShop.getAll().stream().map(ChestShop::getItem).distinct().toList());

        watchShops();
        registerCommands();

        ClientTickEvents.START_CLIENT_TICK.register((client) -> {
            if (buyPrice != null || sellPrice != null) {
                if (waitingTicks > 3) {
                    saveShop();

                    buyPrice = null;
                    sellPrice = null;
                    waitingTicks = 0;
                } else {
                    waitingTicks++;
                }
            }
        });
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("cshop")
                        .then(literal("create")
                                .then(argument("name", StringArgumentType.word())
                                        .then(argument("command", StringArgumentType.greedyString())
                                            .executes((context) -> {
                                                String name = StringArgumentType.getString(context, "name");
                                                String command = StringArgumentType.getString(context, "command");
                                                Shop shop = Shop.getByName(name);

                                                if (shop == null) {
                                                    shop = new Shop();
                                                }

                                                shop.setCommand(command);
                                                shop.setName(name);
                                                shop.saveOrUpdate();

                                                context.getSource().sendFeedback(PREFIX.copy().append(Text.literal(" Created Shop " + name + " at " + command)));
                                                return 1;
                                            })
                                        )
                                )
                        )
                        .then(literal("use")
                                .then(argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            Shop shop = Shop.getByName(name);

                                            if (shop == null) {
                                                context.getSource().sendFeedback(PREFIX.copy().append(Text.literal("This shop does not exist")));
                                                return 1;
                                            }

                                            selectedShop = shop.getName();
                                            selectedShopId = shop.getId();

                                            context.getSource().sendFeedback(PREFIX.copy().append(Text.literal("Shop " + shop.getName() + " (" + shop.getCommand() + ") selected")));

                                            return 1;
                                        }).suggests((context, builder) -> {
                                            Shop.getAll().stream().map(Shop::getName).forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                )
                        )
                        .then(literal("buy")
                                .then(argument("item", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String itemName = StringArgumentType.getString(context, "item");

                                            List<ChestShop> shops = ChestShop.getByItem(itemName).stream().filter(shop -> shop.getBuyPrice() != null).sorted((a,b) -> a.getBuyPrice()/a.getQuantity() > b.getBuyPrice()/b.getQuantity() ? 1 : -1).toList();

                                            if (shops.isEmpty()) {
                                                context.getSource().sendFeedback(PREFIX.copy().append(Text.literal("No shop for " + itemName + " found")));
                                                return 1;
                                            }

                                            context.getSource().sendFeedback(PREFIX.copy().append(Text.literal("Places to buy " + itemName + ":")));
                                            for (ChestShop shop : shops) {
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

                                            return 1;
                                        }).suggests((context, builder) -> {
                                            for (String item : knownItems) {
                                                builder.suggest(item.toLowerCase());
                                            }
                                            return builder.buildFuture();
                                        })
                                )
                        )
                )
        );
    }

    private void watchShops() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            List<Text> parts = message.getSiblings();
            if (parts.isEmpty()) return;

            boolean isOwnerTag = false;
            boolean isStockTag = false;
            boolean isItemTag = false;
            for (Text text : parts) {
                if (text.getString().equals("Shop Information:") && text.getStyle().isBold()) {
                    initShopMessage();
                    break;
                }
                if (text.getString().equals("Owner: ") && text.getStyle().getColor() == TextColor.fromFormatting(Formatting.YELLOW)) {
                    isOwnerTag = true;
                    continue;
                }
                if (text.getString().equals("Stock: ") && text.getStyle().getColor() == TextColor.fromFormatting(Formatting.YELLOW)) {
                    isStockTag = true;
                    continue;
                }
                if (text.getString().equals("Item: ") && text.getStyle().getColor() == TextColor.fromFormatting(Formatting.YELLOW)) {
                    isItemTag = true;
                    continue;
                }

                if (isOwnerTag) {
                    owner = text.getString();
                    break;
                }
                if (isStockTag) {
                    try {
                        stock = Integer.parseInt(text.getString());
                    } catch (NumberFormatException e) {
                        break;
                    }
                    break;
                }
                if (isItemTag) {
                    item = text.getString();
                    break;
                }

                Matcher buyMatcher = BUY_PATTERN.matcher(text.getString());
                while (buyMatcher.find()) {
                    String quantity = buyMatcher.group(1);
                    String price = buyMatcher.group(4);
                    qty = Integer.parseInt(quantity.replaceAll(",", ""));
                    buyPrice = Integer.parseInt(price.replaceAll(",", ""));
                }

                Matcher sellMatcher = SELL_PATTERN.matcher(text.getString());
                while (sellMatcher.find()) {
                    String quantity = sellMatcher.group(1);
                    String price = sellMatcher.group(4);

                    qty = Integer.parseInt(quantity.replaceAll(",", ""));
                    sellPrice = Integer.parseInt(price.replaceAll(",", ""));
                }

                if (buyPrice != null && sellPrice != null) {
                    saveShop();
                    buyPrice = null;
                    sellPrice = null;
                }
            }
        });
    }

    private void saveShop() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        if (selectedShop == null) {
            player.sendMessage(PREFIX.copy().append(Text.literal("No Shop selected (/shop use <shop>)")));
            return;
        }

        ChestShop shop = ChestShop.getByCoordinate(x,y,z);

        if (shop != null) {
            if (selectedShopId != shop.getShopId()) {
                Shop otherShop = Shop.getById(shop.getShopId());
                if (otherShop != null) {
                    player.sendMessage(PREFIX.copy().append(Text.literal("Shop is owned by " + otherShop.getName() + "(" + otherShop.getCommand() + ")")));
                    return;
                }
            }
        } else {
            shop = new ChestShop();
        }

        StringBuilder notifyMessage = new StringBuilder();
        notifyMessage.append(selectedShop).append(" ").append(qty).append("x ").append(item).append(" (");
        if (buyPrice != null && sellPrice != null) {
            notifyMessage.append("B/S)");
        }else if (buyPrice != null) {
            notifyMessage.append("B)");
        } else {
            notifyMessage.append("S)");
        }
        notifyMessage.append(" (").append(stock).append(")");

        shop.setX(x);
        shop.setY(y);
        shop.setZ(z);
        shop.setOwner(owner);
        shop.setStock(stock);
        shop.setItem(item);
        shop.setQuantity(qty);
        shop.setSellPrice(sellPrice);
        shop.setBuyPrice(buyPrice);
        shop.setShopId(selectedShopId);

        shop.setFull(stock == invSize);

        knownItems.add(item);

        shop.saveOrUpdate();

        player.sendMessage(Text.literal(notifyMessage.toString()), true);
    }

    private void initShopMessage() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null) return;

        HitResult hit = player.raycast(20, 0, false);

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;

            BlockEntity blockState = player.getWorld().getBlockEntity(blockHit.getBlockPos());

            invSize = 1728;
            if (blockState instanceof ChestBlockEntity chest) {
                if (chest.getCachedState().get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                    invSize = 3456;
                }
            }

            x = blockHit.getBlockPos().getX();
            y = blockHit.getBlockPos().getY();
            z = blockHit.getBlockPos().getZ();
        }
    }
}
