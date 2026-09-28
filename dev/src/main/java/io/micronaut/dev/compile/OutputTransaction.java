/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.dev.compile;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The replacement of output files as one unit: files removed are parked in a backup directory and
 * staged files are moved into place, and either the whole set is committed or every move is undone.
 *
 * <p>A running generation reads the live output, so files are moved into place whole, never written
 * in place; and a failure half way through a promotion, a full disk or a file that cannot be moved,
 * rolls the output back to what it was rather than leaving it a mix of two compilations.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
final class OutputTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(OutputTransaction.class);

    private final Path backup;
    private final Deque<Move> moves = new ArrayDeque<>();
    private final Set<Path> removed = new LinkedHashSet<>();
    private int parked;

    /**
     * Starts a transaction.
     *
     * @param backup Where removed files are parked until the commit; emptied first
     * @throws IOException if the backup directory cannot be prepared
     */
    OutputTransaction(Path backup) throws IOException {
        this.backup = backup;
        deleteRecursively(backup);
    }

    /**
     * Removes a file, keeping it for a rollback.
     *
     * @param file The file; a missing file is not an error
     * @return Whether the file existed
     * @throws IOException if the file cannot be moved aside
     */
    boolean remove(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        Files.createDirectories(backup);
        Path parkedFile = backup.resolve(String.valueOf(parked++));
        Files.move(file, parkedFile);
        moves.push(new Move(file, parkedFile));
        removed.add(file);
        return true;
    }

    /**
     * Moves a staged file into place, parking the file it replaces.
     *
     * @param staged The staged file
     * @param target Where it goes
     * @throws IOException if the file cannot be moved
     */
    void put(Path staged, Path target) throws IOException {
        remove(target);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // staging and output on different file systems: a copy beside the target, then a rename
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.copy(staged, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            Files.delete(staged);
        }
        moves.push(new Move(staged, target));
    }

    /**
     * Moves every file under a staging directory into place under a target directory.
     *
     * @param staging The staging directory; a missing one has nothing to promote
     * @param target The target directory
     * @throws IOException if a file cannot be moved
     */
    void promote(Path staging, Path target) throws IOException {
        if (!Files.isDirectory(staging)) {
            return;
        }
        Files.createDirectories(target);
        Files.walkFileTree(staging, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                put(file, target.resolve(staging.relativize(file).toString()));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * The files removed so far, whether or not a later move put a new file at the same path.
     *
     * @return The removed files
     */
    Set<Path> removed() {
        return removed;
    }

    /**
     * Makes the moves final by discarding the parked files.
     *
     * @throws IOException if the backup directory cannot be deleted
     */
    void commit() throws IOException {
        moves.clear();
        deleteRecursively(backup);
    }

    /**
     * Undoes every move, latest first, as far as the file system allows.
     */
    void rollback() {
        while (!moves.isEmpty()) {
            Move move = moves.pop();
            try {
                Path parent = move.from().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.move(move.to(), move.from(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOG.warn("Cannot restore {} from {} while rolling back a compilation: {}", move.from(), move.to(), e.getMessage());
            }
        }
        removed.clear();
        try {
            deleteRecursively(backup);
        } catch (IOException e) {
            LOG.debug("Cannot delete backup directory {}", backup, e);
        }
    }

    static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    /**
     * A move made, undone by moving {@code to} back to {@code from}.
     *
     * @param from Where the file was
     * @param to Where it went
     */
    private record Move(Path from, Path to) {
    }
}
