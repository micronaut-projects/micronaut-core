package io.micronaut.dev;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceChangesTest {

    @TempDir
    Path directory;

    @Test
    void aLateReportOfAWriteDoesNotHideTheDeletionThatFollowedIt() {
        Path gone = directory.resolve("Gone.java");
        // the harness reports the deletion, then the watcher its earlier write: merged in that order, the write wins
        SourceChanges merged = new SourceChanges(Set.of(), Set.of(gone)).merge(new SourceChanges(Set.of(gone), Set.of()));
        assertEquals(Set.of(gone), merged.changed());
        // the file system settles it
        SourceChanges settled = merged.settled();
        assertEquals(Set.of(), settled.changed());
        assertEquals(Set.of(gone), settled.deleted());
    }

    @Test
    void aLateReportOfADeletionDoesNotHideTheWriteThatFollowedIt() throws Exception {
        Path back = Files.writeString(directory.resolve("Back.java"), "class Back { }");
        Path gone = directory.resolve("Gone.java");
        SourceChanges merged = new SourceChanges(Set.of(back, gone), Set.of()).merge(new SourceChanges(Set.of(), Set.of(back)));
        assertEquals(Set.of(back), merged.deleted());
        SourceChanges settled = merged.settled();
        assertEquals(Set.of(back), settled.changed());
        assertEquals(Set.of(gone), settled.deleted());
    }

    @Test
    void changesTheFileSystemAgreesWithAreKept() throws Exception {
        Path here = Files.writeString(directory.resolve("Here.java"), "class Here { }");
        SourceChanges changes = new SourceChanges(Set.of(here), Set.of(directory.resolve("Gone.java")));
        assertEquals(changes, changes.settled());
    }
}
