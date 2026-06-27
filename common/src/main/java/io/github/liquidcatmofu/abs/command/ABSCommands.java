package io.github.liquidcatmofu.abs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import io.github.liquidcatmofu.abs.library.ABSLibrary;
import io.github.liquidcatmofu.abs.server.ABSHttpServer;
import io.github.liquidcatmofu.abs.server.web.WebSessionStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

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
        if (!ABSHttpServer.isRunning()) {
            source.sendFailure(Component.literal("[ABS] HTTP サーバーが起動していません。"));
            return 0;
        }

        ABSLibrary.ensureRoot(player.getUUID(), player.getGameProfile().getName());

        UUID initToken = WebSessionStore.generateInitToken(player.getUUID());
        String url = "http://localhost:" + ABSHttpServer.DEFAULT_PORT
                + "/api/auth?session=" + initToken;

        Component link = Component.literal("[ABS ダッシュボードを開く]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                        .withUnderlined(true)
                        .withColor(0x55FFFF));

        source.sendSuccess(() -> Component.empty()
                .append(Component.literal("[ABS] "))
                .append(link)
                .append(Component.literal(" (10分で失効します)")), false);

        return Command.SINGLE_SUCCESS;
    }
}
