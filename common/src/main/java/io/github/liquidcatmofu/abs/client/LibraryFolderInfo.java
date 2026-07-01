package io.github.liquidcatmofu.abs.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

/** サーバーからクライアントへ送信されるライブラリフォルダのサマリー。 */
@Environment(EnvType.CLIENT)
public record LibraryFolderInfo(String id, String displayName, @Nullable String parentId) {}
