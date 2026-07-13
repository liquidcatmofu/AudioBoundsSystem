package io.github.liquidcatmofu.abs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.library.ABSLibrary;
import io.github.liquidcatmofu.abs.network.ABSNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ABSCommands {
    private ABSCommands() {}

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, selection) ->
            dispatcher.register(
                Commands.literal("abs")
                    .then(Commands.literal("ui")
                        .executes(ABSCommands::executeUi))
            )
        );
    }

    private static int executeUi(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[ABS] プレイヤーのみ実行可能です。"));
            return 0;
        }
        ABSLibrary.ensureRoot(player.getUUID(), player.getGameProfile().getName());
        NetworkManager.sendToPlayer(player, ABSNetwork.OPEN_WEB_UI,
                new FriendlyByteBuf(Unpooled.buffer()));
        source.sendSuccess(() -> Component.literal("[ABS] クライアントにダッシュボードを準備しています。"), false);

        return Command.SINGLE_SUCCESS;
    }
}
