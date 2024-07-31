package de.craftery.chestshopmanager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Chestshopmanager implements ModInitializer {
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

    private int waitingTicks = 0;

    @Override
    public void onInitialize() {
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

        ClientTickEvents.START_CLIENT_TICK.register((client) -> {
            if (buyPrice != null || sellPrice != null) {
                if (waitingTicks > 2) {
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

    private void saveShop() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        StringBuilder notifyMessage = new StringBuilder();
        notifyMessage.append("<shopname> ").append(qty).append("x ").append(item).append(" (");
        if (buyPrice != null && sellPrice != null) {
            notifyMessage.append("B/S)");
        }else if (buyPrice != null) {
            notifyMessage.append("B)");
        } else {
            notifyMessage.append("S)");
        }
        notifyMessage.append(" (").append(stock).append(")");

        player.sendMessage(Text.literal(notifyMessage.toString()), true);
    }

    private void initShopMessage() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null) return;

        HitResult hit = player.raycast(20, 0, false);

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            x = blockHit.getBlockPos().getX();
            y = blockHit.getBlockPos().getY();
            z = blockHit.getBlockPos().getZ();
        }
    }
}
