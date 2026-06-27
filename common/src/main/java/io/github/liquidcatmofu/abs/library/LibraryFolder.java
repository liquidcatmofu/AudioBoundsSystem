package io.github.liquidcatmofu.abs.library;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * フォルダノード。フラットストア + parentId 参照でツリーを構成する。
 * ルートフォルダは parentId == null、id はオーナーの UUID 文字列。
 * サブフォルダは ownerUuid をルートから引き継ぐ。
 */
public class LibraryFolder {
    public String id;
    public String displayName;
    public String parentId;       // null = ルート
    public String ownerUuid;
    public String ownerName;
    public List<String> allowedPlayers = new ArrayList<>();

    public LibraryFolder() {}

    public LibraryFolder(String id, String displayName, String parentId, UUID ownerUuid, String ownerName) {
        this.id = id;
        this.displayName = displayName;
        this.parentId = parentId;
        this.ownerUuid = ownerUuid.toString();
        this.ownerName = ownerName;
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public boolean isOwner(UUID playerUuid) {
        return playerUuid.toString().equals(ownerUuid);
    }
}
