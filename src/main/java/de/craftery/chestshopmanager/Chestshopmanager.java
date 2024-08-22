package de.craftery.chestshopmanager;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.craftery.chestshopmanager.db.HibernateConfigurator;
import de.craftery.chestshopmanager.db.TestDatabaseConnection;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class Chestshopmanager implements ModInitializer {
    @Getter
    private static Chestshopmanager instance;

    private static final Pattern BUY_PATTERN = Pattern.compile("^Buy ((\\d+(,?))+) for \\$((\\d+(,?))+)$");
    private static final Pattern SELL_PATTERN = Pattern.compile("^Sell ((\\d+(,?))+) for \\$((\\d+(,?))+)$");

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

    @Setter
    private String selectedShop = null;
    @Setter
    private long selectedShopId = 0;

    private final Set<String> knownItems = new HashSet<>();

    @Override
    public void onInitialize() {
        instance = this;
        new TestDatabaseConnection();
        HibernateConfigurator.addEntity(ChestShop.class);
        HibernateConfigurator.addEntity(Shop.class);

        knownItems.addAll(ChestShop.getAll().stream().map(ChestShop::getItem).distinct().toList());

        watchShops();
        watchWarpUse();
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
                                                Commands.createShop(context, name, command);
                                                return 1;
                                            })
                                        )
                                )
                        )
                        .then(literal("use")
                                .then(argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            Commands.useShop(context, name);
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
                                            Commands.buyItem(context, itemName);
                                            return 1;
                                        }).suggests((context, builder) -> {
                                            for (String item : knownItems) {
                                                builder.suggest(item.toLowerCase());
                                            }
                                            return builder.buildFuture();
                                        })
                                )
                        )
                        .then(literal("tp")
                                .then(argument("shopId", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            Integer shopId = IntegerArgumentType.getInteger(context, "shopId");
                                            Commands.tp(context, shopId);
                                            return 1;
                                        }).suggests((context, builder) -> {
                                            for (String item : knownItems) {
                                                builder.suggest(item.toLowerCase());
                                            }
                                            return builder.buildFuture();
                                        })
                                )
                        )
                        .then(literal("deletechest")
                                .executes(context -> {
                                    Commands.deleteChest(context);
                                    return 1;
                                })
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

    private void watchWarpUse() {
        ClientSendMessageEvents.COMMAND.register((command -> {
             String[] parts = command.split(" ");
             if (parts.length >= 2 && parts[0].equalsIgnoreCase("pw")) {
                 selectedShop = null;

                 List<Shop> possibleShops = Shop.getByCommand("/pw " + parts[1]);

                 if (!possibleShops.isEmpty()) {
                     Shop shop = possibleShops.get(0);
                     selectedShop = shop.getName();
                     selectedShopId = shop.getId();
                 }
             }
        }));
    }

    private void saveShop() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        if (selectedShop == null) {
            Messages.sendPlayerMessage(player, Messages.NO_SHOP_SELECTED);
            return;
        }

        ChestShop shop = ChestShop.getByCoordinate(x,y,z);

        if (shop != null) {
            if (selectedShopId != shop.getShopId()) {
                Shop otherShop = Shop.getById(shop.getShopId());
                if (otherShop != null) {
                    Messages.sendPlayerMessage(player, Messages.SHOP_OWNED_BY, otherShop.getName(), otherShop.getCommand());
                    return;
                }
            }
        } else {
            shop = new ChestShop();
        }

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

        StringBuilder notifyMessage = new StringBuilder();
        notifyMessage.append(selectedShop).append(" ").append(qty).append("x ").append(item).append(" (");
        if (buyPrice != null && sellPrice != null) {
            notifyMessage.append("B/S)");
        }else if (buyPrice != null) {
            notifyMessage.append("B)");
        } else {
            notifyMessage.append("S)");
        }
        notifyMessage.append(" (");

        if (shop.isFull()) {
            notifyMessage.append("full");
        } else if (stock == 0) {
            notifyMessage.append("empty");
        } else {
            notifyMessage.append(stock);
        }

        notifyMessage.append(")");

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
