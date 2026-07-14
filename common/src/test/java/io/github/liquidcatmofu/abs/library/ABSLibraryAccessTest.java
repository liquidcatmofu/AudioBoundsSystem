package io.github.liquidcatmofu.abs.library;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ABSLibraryAccessTest {
    @TempDir
    Path tempDir;

    private UUID owner;
    private UUID guest;
    private LibraryFolder root;
    private LibraryFolder shared;
    private LibraryFolder descendant;

    @BeforeEach
    void createFolderTree() throws IOException {
        ABSLibrary.init(tempDir);
        owner = UUID.fromString("12345678-1234-5678-1234-567812345678");
        guest = UUID.fromString("87654321-4321-8765-4321-876543218765");
        root = ABSLibrary.ensureRoot(owner, "Owner");
        shared = ABSLibrary.createSubFolder(root, "Shared");
        descendant = ABSLibrary.createSubFolder(shared, "Descendant");
        shared.allowedPlayers.add(guest.toString());
        ABSLibrary.saveFolder(shared);
    }

    @Test
    void ownerAndOperatorHaveOwnerAccess() {
        assertEquals(FolderAccess.OWNER, ABSLibrary.access(descendant, owner, false));
        assertEquals(FolderAccess.OWNER, ABSLibrary.access(descendant, guest, true));
    }

    @Test
    void sharingIsInheritedByDescendantsButNotAncestors() {
        assertEquals(FolderAccess.ALLOWED, ABSLibrary.access(shared, guest, false));
        assertEquals(FolderAccess.ALLOWED, ABSLibrary.access(descendant, guest, false));
        assertEquals(FolderAccess.NONE, ABSLibrary.access(root, guest, false));
        assertEquals(FolderAccess.NONE, ABSLibrary.access(null, guest, false));
        assertEquals(FolderAccess.NONE, ABSLibrary.access(shared, null, false));
    }

    @Test
    void accessibleListingContainsOwnedAndSharedSubtrees() {
        assertEquals(Set.of(root.id, shared.id, descendant.id), ids(ABSLibrary.listAccessible(owner, false)));
        assertEquals(Set.of(shared.id, descendant.id), ids(ABSLibrary.listAccessible(guest, false)));
        assertEquals(Set.of(root.id, shared.id, descendant.id), ids(ABSLibrary.listAccessible(guest, true)));
    }

    @Test
    void cyclicParentMetadataTerminatesWithoutGrantingAccess() throws IOException {
        shared.allowedPlayers.clear();
        shared.parentId = descendant.id;
        descendant.parentId = shared.id;
        ABSLibrary.saveFolder(shared);
        ABSLibrary.saveFolder(descendant);

        assertEquals(FolderAccess.NONE, ABSLibrary.access(descendant, guest, false));
    }

    private static Set<String> ids(List<LibraryFolder> folders) {
        return folders.stream().map(folder -> folder.id).collect(Collectors.toSet());
    }
}
