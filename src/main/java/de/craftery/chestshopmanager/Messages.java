package de.craftery.chestshopmanager;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public enum Messages {
    SHOP_CREATED("Created Shop %0 at %1", 2),
    SHOP_NOT_EXISTING("This shop does not exist", 0),
    SHOP_SELECTED("Shop %0 (%1) selected", 2),
    SHOP_NOT_FOUND("No shop for %0 found", 1),
    BUY_HEADER("Places to buy %0:", 1),
    NO_SHOP_SELECTED("No Shop selected (/cshop use <shop>)", 0),
    SHOP_OWNED_BY("Shop is owned by %0 (%1)", 2),
    NO_SHOP_FOUND("No shop found there", 0),
    SHOP_DELETED("Shop was deleted successfully", 0);

    private final String template;
    private final int args;

    Messages(String template, int args) {
        this.template = template;
        this.args = args;
    }

    private static MutableText getPrefix() {
        MutableText prefix = Text.literal("[ChestShopManager] ").formatted(Formatting.LIGHT_PURPLE);
        return Text.empty().append(prefix);
    }

    private static MutableText buildMessage(Messages message, String... arguments) {
        if (arguments.length != message.args) {
            throw new RuntimeException("Message needs " + message.args + " arguments, but received " + arguments.length);
        }

        MutableText response = getPrefix();

        String msg = message.template;
        for (int i = 0; i < arguments.length; i++) {
            msg = msg.replace("%" + i, arguments[i]);
        }

        response.append(Text.literal(msg));
        return response;
    }

    public static void sendPlayerMessage(ClientPlayerEntity player, Messages message, String... arguments) {
        player.sendMessage(buildMessage(message, arguments));
    }

    public static void sendCommandFeedback(CommandContext<FabricClientCommandSource> context, Messages message, String... arguments) {
        context.getSource().sendFeedback(buildMessage(message, arguments));
    }
}
