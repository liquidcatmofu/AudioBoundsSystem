package io.github.liquidcatmofu.abs.client.gui;

import dev.architectury.networking.NetworkManager;
import io.github.liquidcatmofu.abs.client.LibraryEntryInfo;
import io.github.liquidcatmofu.abs.client.LibraryFolderInfo;
import io.github.liquidcatmofu.abs.network.ABSNetwork;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * ライブラリをブラウズして音声・シーケンスを選択するインゲーム画面。
 * フォルダを階層ツリーで一覧表示し、選択フォルダのエントリを下段に表示する。
 */
@Environment(EnvType.CLIENT)
public final class LibraryBrowserScreen extends Screen {
    private static final int PANEL_W        = 340;
    private static final int ITEM_H         = 20;
    private static final int FOLDER_VISIBLE = 6;
    private static final int ENTRY_VISIBLE  = 5;
    private static final int PADDING        = 8;
    private static final int SECTION_GAP    = 6;
    private static final int INDENT_W       = 8;
    private static final int SEL_BTN_W      = 48;

    private enum State { LOADING_FOLDERS, LOADING_CONTENTS, BROWSING }

    private final Screen parent;
    private final boolean includeSequences;
    private final BiConsumer<String, String> onSelect;

    private State state = State.LOADING_FOLDERS;

    private List<LibraryFolderInfo> allFolders    = List.of();
    private List<TreeNode>          folderTree     = List.of();
    private LibraryFolderInfo       selectedFolder = null;
    private List<LibraryEntryInfo>  currentEntries = List.of();

    private int folderScroll = 0;
    private int entryScroll  = 0;

    private record TreeNode(LibraryFolderInfo folder, int depth) {}

    public LibraryBrowserScreen(Screen parent, boolean includeSequences, BiConsumer<String, String> onSelect) {
        super(Component.literal("音声を選択"));
        this.parent           = parent;
        this.includeSequences = includeSequences;
        this.onSelect         = onSelect;
    }

    // ── パケット応答コールバック ──────────────────────────────

    public void onFoldersReceived(List<LibraryFolderInfo> folders) {
        this.allFolders   = new ArrayList<>(folders);
        this.folderTree   = buildTree();
        this.state        = State.BROWSING;
        this.folderScroll = 0;
        rebuildWidgets();
    }

    public void onContentsReceived(String folderId, List<LibraryEntryInfo> audio,
                                   List<LibraryEntryInfo> tts, List<LibraryEntryInfo> seqs) {
        if (selectedFolder == null || !selectedFolder.id().equals(folderId)) return;
        List<LibraryEntryInfo> all = new ArrayList<>(audio);
        all.addAll(tts);
        if (includeSequences) all.addAll(seqs);
        this.currentEntries = all;
        this.state          = State.BROWSING;
        this.entryScroll    = 0;
        rebuildWidgets();
    }

    // ── Screen ライフサイクル ────────────────────────────────

    @Override
    protected void init() {
        if (state == State.LOADING_FOLDERS) {
            NetworkManager.sendToServer(ABSNetwork.REQUEST_LIBRARY_FOLDERS,
                    new FriendlyByteBuf(Unpooled.buffer()));
        }
        buildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();
        int x    = (width - PANEL_W) / 2;
        int topY = topY();

        if (state == State.LOADING_FOLDERS) return;

        // ── フォルダツリー ───────────────────────────────────
        int folderListTop = topY + ITEM_H;
        List<TreeNode> folderPage = pagedList(folderTree, folderScroll, FOLDER_VISIBLE);
        for (int i = 0; i < folderPage.size(); i++) {
            TreeNode node   = folderPage.get(i);
            int      y      = folderListTop + i * ITEM_H;
            int      indent = node.depth() * INDENT_W;
            boolean  sel    = node.folder().equals(selectedFolder);
            String   label  = (sel ? "▼ " : "▶ ") + node.folder().displayName();
            addRenderableWidget(Button.builder(Component.literal(label), btn -> selectFolder(node.folder()))
                    .bounds(x + indent, y, PANEL_W - indent, ITEM_H - 2).build());
        }
        if (folderTree.size() > FOLDER_VISIBLE) {
            addRenderableWidget(Button.builder(Component.literal("▲"),
                    btn -> { if (folderScroll > 0) { folderScroll--; rebuildWidgets(); } })
                .bounds(x + PANEL_W + 2, folderListTop, 14, 14).build());
            addRenderableWidget(Button.builder(Component.literal("▼"),
                    btn -> { if (folderScroll + FOLDER_VISIBLE < folderTree.size()) { folderScroll++; rebuildWidgets(); } })
                .bounds(x + PANEL_W + 2, folderListTop + (FOLDER_VISIBLE - 1) * ITEM_H, 14, 14).build());
        }

        // ── エントリリスト ───────────────────────────────────
        int entryListTop = topY + ITEM_H * (FOLDER_VISIBLE + 1) + SECTION_GAP + ITEM_H;
        if (selectedFolder != null && state == State.BROWSING) {
            List<LibraryEntryInfo> entryPage = pagedList(currentEntries, entryScroll, ENTRY_VISIBLE);
            for (int i = 0; i < entryPage.size(); i++) {
                LibraryEntryInfo e = entryPage.get(i);
                int y = entryListTop + i * ITEM_H;
                addRenderableWidget(Button.builder(Component.literal(entryLabel(e)), btn -> {})
                        .bounds(x, y, PANEL_W - SEL_BTN_W - 2, ITEM_H - 2).build());
                addRenderableWidget(Button.builder(Component.literal("選択"), btn -> selectEntry(e))
                        .bounds(x + PANEL_W - SEL_BTN_W, y, SEL_BTN_W, ITEM_H - 2).build());
            }
            if (currentEntries.size() > ENTRY_VISIBLE) {
                addRenderableWidget(Button.builder(Component.literal("▲"),
                        btn -> { if (entryScroll > 0) { entryScroll--; rebuildWidgets(); } })
                    .bounds(x + PANEL_W + 2, entryListTop, 14, 14).build());
                addRenderableWidget(Button.builder(Component.literal("▼"),
                        btn -> { if (entryScroll + ENTRY_VISIBLE < currentEntries.size()) { entryScroll++; rebuildWidgets(); } })
                    .bounds(x + PANEL_W + 2, entryListTop + (ENTRY_VISIBLE - 1) * ITEM_H, 14, 14).build());
            }
        }

        // キャンセルボタン
        int bottomY = entryListTop + ENTRY_VISIBLE * ITEM_H + PADDING;
        addRenderableWidget(Button.builder(Component.literal("キャンセル"), btn -> onClose())
                .bounds(x + PANEL_W - 80, bottomY, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x    = (width - PANEL_W) / 2;
        int topY = topY();

        g.drawCenteredString(font, title, width / 2, topY - 14, 0xFFFFFF);

        if (state == State.LOADING_FOLDERS) {
            g.drawCenteredString(font, Component.literal("読み込み中..."), width / 2, topY + ITEM_H * 2, 0xA0A0A0);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        // フォルダセクションラベル
        g.drawString(font, Component.literal("フォルダ"), x, topY + 4, 0xA0A0A0);
        if (folderTree.isEmpty()) {
            g.drawString(font, Component.literal("アクセスできるフォルダがありません"),
                    x, topY + ITEM_H + 6, 0x606060);
        }

        // 区切り線
        int divY = topY + ITEM_H * (FOLDER_VISIBLE + 1) + SECTION_GAP / 2;
        g.fill(x, divY, x + PANEL_W, divY + 1, 0x50FFFFFF);

        // エントリセクションラベル
        int entryLabelY = topY + ITEM_H * (FOLDER_VISIBLE + 1) + SECTION_GAP;
        String entryHeader = selectedFolder != null ? selectedFolder.displayName() : "← フォルダを選択";
        g.drawString(font, Component.literal(entryHeader), x, entryLabelY + 4, 0xA0A0A0);

        if (state == State.LOADING_CONTENTS) {
            int entryListTop = entryLabelY + ITEM_H;
            g.drawString(font, Component.literal("読み込み中..."), x, entryListTop + 6, 0x808080);
        } else if (selectedFolder != null && currentEntries.isEmpty()) {
            int entryListTop = entryLabelY + ITEM_H;
            g.drawString(font, Component.literal("音声がありません"), x, entryListTop + 6, 0x606060);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int topY             = topY();
        int folderZoneBottom = topY + ITEM_H * (FOLDER_VISIBLE + 1) + SECTION_GAP;
        int dir              = (int) Math.signum(-delta);
        if (mouseY < folderZoneBottom) {
            int next = clamp(folderScroll + dir, 0, Math.max(0, folderTree.size() - FOLDER_VISIBLE));
            if (next != folderScroll) { folderScroll = next; rebuildWidgets(); return true; }
        } else {
            int next = clamp(entryScroll + dir, 0, Math.max(0, currentEntries.size() - ENTRY_VISIBLE));
            if (next != entryScroll) { entryScroll = next; rebuildWidgets(); return true; }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    // ── アクション ────────────────────────────────────────────

    private void selectFolder(LibraryFolderInfo folder) {
        if (folder.equals(selectedFolder)) return;
        selectedFolder = folder;
        currentEntries = List.of();
        state          = State.LOADING_CONTENTS;
        entryScroll    = 0;
        rebuildWidgets();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(folder.id(), 128);
        NetworkManager.sendToServer(ABSNetwork.REQUEST_FOLDER_CONTENTS, buf);
    }

    private void selectEntry(LibraryEntryInfo entry) {
        if (selectedFolder == null) return;
        String ref   = "lib:" + selectedFolder.id() + "/" + entry.type() + "/" + entry.id();
        String label = buildFolderPath() + " / " + entry.displayName();
        onSelect.accept(ref, label);
        onClose();
    }

    /** 選択中フォルダのルートからのパス文字列を返す（例: "BGM / 環境音"）。 */
    private String buildFolderPath() {
        Map<String, LibraryFolderInfo> byId = allFolders.stream()
                .collect(Collectors.toMap(LibraryFolderInfo::id, f -> f));
        List<String> names = new ArrayList<>();
        LibraryFolderInfo cur = selectedFolder;
        while (cur != null) {
            names.add(0, cur.displayName());
            cur = cur.parentId() != null ? byId.get(cur.parentId()) : null;
        }
        return String.join(" / ", names);
    }

    // ── ツリー構築 ────────────────────────────────────────────

    private List<TreeNode> buildTree() {
        Set<String> allIds = allFolders.stream().map(LibraryFolderInfo::id).collect(Collectors.toSet());
        Map<String, List<LibraryFolderInfo>> childMap = new LinkedHashMap<>();
        for (LibraryFolderInfo f : allFolders) {
            String key = (f.parentId() != null && allIds.contains(f.parentId())) ? f.parentId() : null;
            childMap.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }
        List<TreeNode> result = new ArrayList<>();
        dfs(childMap, null, 0, result);
        return result;
    }

    private void dfs(Map<String, List<LibraryFolderInfo>> childMap, String parentId,
                     int depth, List<TreeNode> out) {
        for (LibraryFolderInfo f : childMap.getOrDefault(parentId, List.of())) {
            out.add(new TreeNode(f, depth));
            dfs(childMap, f.id(), depth + 1, out);
        }
    }

    // ── ヘルパ ────────────────────────────────────────────────

    private int topY() {
        int totalH = ITEM_H                          // フォルダラベル
                   + FOLDER_VISIBLE * ITEM_H         // ツリー
                   + SECTION_GAP + ITEM_H            // 区切り + エントリラベル
                   + ENTRY_VISIBLE * ITEM_H          // エントリ
                   + PADDING + ITEM_H;               // キャンセル
        return Math.max(8, (height - totalH) / 2);
    }

    private <T> List<T> pagedList(List<T> list, int offset, int max) {
        int from = Math.min(offset, list.size());
        return list.subList(from, Math.min(from + max, list.size()));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private String entryLabel(LibraryEntryInfo e) {
        String prefix = switch (e.type()) {
            case "audio"    -> "♪ ";
            case "tts"      -> "TTS ";
            case "sequence" -> "⟳ ";
            default         -> "";
        };
        String dur = e.durationTicks() > 0
                ? " (" + e.durationTicks() / 20 / 60 + ":" + String.format("%02d", e.durationTicks() / 20 % 60) + ")"
                : "";
        return prefix + e.displayName() + dur;
    }
}
