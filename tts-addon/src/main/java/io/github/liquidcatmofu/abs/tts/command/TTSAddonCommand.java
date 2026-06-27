package io.github.liquidcatmofu.abs.tts.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import io.github.liquidcatmofu.abs.tts.TTSAddon;
import io.github.liquidcatmofu.abs.tts.TTSProviderRegistry;
import io.github.liquidcatmofu.abs.tts.api.SynthesisResult;
import io.github.liquidcatmofu.abs.tts.api.TTSProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class TTSAddonCommand {
    private TTSAddonCommand() {}

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, selection) ->
            dispatcher.register(
                Commands.literal("abstts")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("synth")
                        .then(Commands.argument("speakerId", StringArgumentType.word())
                            .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(TTSAddonCommand::executeSynth))))
                    .then(Commands.literal("check")
                        .executes(TTSAddonCommand::executeCheck))
                    .then(Commands.literal("provider")
                        .then(Commands.literal("list")
                            .executes(TTSAddonCommand::executeProviderList))
                        .then(Commands.literal("set")
                            .then(Commands.argument("id", StringArgumentType.word())
                                .executes(TTSAddonCommand::executeProviderSet))))
            )
        );
    }

    private static int executeSynth(CommandContext<CommandSourceStack> ctx) {
        String speakerId = StringArgumentType.getString(ctx, "speakerId");
        String text = StringArgumentType.getString(ctx, "text");
        CommandSourceStack source = ctx.getSource();

        TTSProvider provider = TTSProviderRegistry.getActive();
        if (provider == null) {
            source.sendFailure(Component.literal("[ABS TTS] プロバイダーが登録されていません。"));
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal("[ABS TTS] 合成開始: provider=" + provider.getId()
                + " speaker=" + speakerId + " / " + text),
            false
        );

        CompletableFuture.supplyAsync(() -> {
            try {
                return provider.synthesize(text, speakerId);
            } catch (Exception e) {
                TTSAddon.LOGGER.error("ABS TTS: synthesis failed", e);
                return null;
            }
        }).thenAccept(result -> {
            if (result == null) {
                source.sendFailure(Component.literal("[ABS TTS] 合成失敗。ログを確認してください。"));
                return;
            }
            SynthesisResult sr = result;
            source.sendSuccess(
                () -> Component.literal("[ABS TTS] 合成完了: " + sr.cacheFileName()
                    + "  → SpeakerBlock の audioFile にこのパスを設定してください。"),
                false
            );
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int executeCheck(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        TTSProvider provider = TTSProviderRegistry.getActive();
        if (provider == null) {
            source.sendFailure(Component.literal("[ABS TTS] プロバイダーが登録されていません。"));
            return 0;
        }
        boolean available = provider.isAvailable();
        if (available) {
            source.sendSuccess(
                () -> Component.literal("[ABS TTS] " + provider.getId() + " 接続OK"),
                false
            );
        } else {
            source.sendFailure(Component.literal(
                "[ABS TTS] " + provider.getId() + " に接続できません。エンジンを起動してください。"
            ));
        }
        return available ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeProviderList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String active = TTSProviderRegistry.getActiveId();
        String list = TTSProviderRegistry.getAll().stream()
                .map(p -> (p.getId().equals(active) ? "* " : "  ") + p.getId())
                .collect(Collectors.joining("\n"));
        source.sendSuccess(() -> Component.literal("[ABS TTS] 登録済みプロバイダー:\n" + list), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeProviderSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        if (!TTSProviderRegistry.setActive(id)) {
            source.sendFailure(Component.literal("[ABS TTS] プロバイダー '" + id + "' が見つかりません。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[ABS TTS] アクティブプロバイダーを '" + id + "' に変更しました。"), false);
        return Command.SINGLE_SUCCESS;
    }
}
