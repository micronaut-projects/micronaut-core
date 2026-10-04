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
package io.micronaut.dev.loader;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The resources known to belong to the directories read live: those there when the loader was created and those seen
 * there since. A resource of that name is the live directory's, and while the live file is absent, deleted by the
 * developer, the build output's copy of it is stale and stays hidden instead of being served in its place. A resource
 * never seen in a live directory, one a processor generated or the build filtered into its output, is served from
 * the build output as before.
 *
 * <p>Names are relative to the outermost live directory that holds the file, as the build copies a resource root into
 * its output: a template under a views root nested in the configuration root is known by its path in the latter.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
public final class LiveResources {

    private static final Logger LOG = LoggerFactory.getLogger(LiveResources.class);

    private final List<Path> roots;
    private final Set<String> names = ConcurrentHashMap.newKeySet();
    private final Set<Path> files = ConcurrentHashMap.newKeySet();

    /**
     * Records the files the live directories hold now.
     *
     * @param liveRoots The directories read live
     */
    public LiveResources(List<Path> liveRoots) {
        List<Path> normalized = liveRoots.stream().map(root -> root.toAbsolutePath().normalize()).distinct()
            .sorted(Comparator.comparingInt(Path::getNameCount)).toList();
        List<Path> outermost = new ArrayList<>();
        for (Path root : normalized) {
            if (outermost.stream().noneMatch(root::startsWith)) {
                outermost.add(root);
            }
        }
        this.roots = List.copyOf(outermost);
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(root)) {
                tree.filter(Files::isRegularFile).forEach(this::seen);
            } catch (IOException | UncheckedIOException e) {
                LOG.debug("Cannot list the live resource root {}", root, e);
            }
        }
    }

    /**
     * Records a file as belonging to a live directory, whether it is there now or was deleted: its build copy is
     * served no more while it is absent.
     *
     * @param file The file
     * @return Whether the file is under a live directory
     */
    public boolean seen(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        String name = nameOf(absolute);
        if (name == null) {
            return false;
        }
        names.add(name);
        files.add(absolute);
        return true;
    }

    /**
     * Whether a resource of the given name belongs to a live directory, so that no other copy of it may stand in for it.
     *
     * @param name The resource name
     * @return True if a live directory held a file of that name
     */
    public boolean belongs(String name) {
        return names.contains(name);
    }

    /**
     * The files known under a path, for a directory that was deleted as a whole: the events name the directory, the
     * resources it held are each gone.
     *
     * @param directory The deleted path
     * @return The known files under it, not the path itself
     */
    public List<Path> knownUnder(Path directory) {
        Path absolute = directory.toAbsolutePath().normalize();
        List<Path> under = new ArrayList<>();
        for (Path file : files) {
            if (!file.equals(absolute) && file.startsWith(absolute)) {
                under.add(file);
            }
        }
        under.sort(Comparator.naturalOrder());
        return under;
    }

    @Nullable
    private String nameOf(Path absolute) {
        for (Path root : roots) {
            if (absolute.startsWith(root) && !absolute.equals(root)) {
                StringBuilder name = new StringBuilder();
                for (Path segment : root.relativize(absolute)) {
                    if (!name.isEmpty()) {
                        name.append('/');
                    }
                    name.append(segment);
                }
                return name.toString();
            }
        }
        return null;
    }
}
