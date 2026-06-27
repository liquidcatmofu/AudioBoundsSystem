package io.github.liquidcatmofu.abs.library;

public enum FolderAccess {
    /** ルートオーナー本人、または OP。構造変更・権限管理が可能 */
    OWNER,
    /** 許可プレイヤー（自分または祖先フォルダで許可）。閲覧・使用が可能 */
    ALLOWED,
    /** アクセス権なし */
    NONE;

    public boolean canView() {
        return this != NONE;
    }

    public boolean canManage() {
        return this == OWNER;
    }
}
