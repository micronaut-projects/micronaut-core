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
package io.micronaut.dev;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The callback FSEvents calls, on any platform: it reads the C strings and flags of the events from native memory,
 * and compares the whole tree when they cannot be read rather than throwing into the native side.
 */
class JnaFsEventsTest {

    private static final Path ROOT = Path.of("/watched/root");
    private static final Path REAL_ROOT = Path.of("/private/watched/root");

    @Test
    void theCallbackReadsThePathsAndFlagsOfTheEvents() {
        List<Path> changed = new ArrayList<>();
        JnaFsEvents.EventCallback callback = new JnaFsEvents.EventCallback(new FsEventStreams.Events(ROOT, REAL_ROOT, changed::add));
        String[] paths = {"/private/watched/root/a", "/private/watched/root/b/c", "/private/watched/root/d"};
        Memory strings = new Memory((long) Native.POINTER_SIZE * paths.length);
        List<Memory> held = new ArrayList<>();
        for (int i = 0; i < paths.length; i++) {
            Memory string = new Memory(paths[i].length() + 1L);
            string.setString(0, paths[i], "UTF-8");
            held.add(string);
            strings.setPointer((long) Native.POINTER_SIZE * i, string);
        }
        Memory flags = new Memory(4L * paths.length);
        flags.setInt(0, 0);
        flags.setInt(4, 0);
        // the events under the tree must be looked for
        flags.setInt(8, 0x00000001);

        callback.invoke(null, null, paths.length, strings, flags, null);

        assertEquals(List.of(ROOT.resolve("a"), ROOT.resolve("b/c"), ROOT), changed);
        assertEquals(paths.length, held.size());
    }

    @Test
    void eventsThatCannotBeReadCompareTheWholeTree() {
        List<Path> changed = new ArrayList<>();
        JnaFsEvents.EventCallback callback = new JnaFsEvents.EventCallback(new FsEventStreams.Events(ROOT, REAL_ROOT, changed::add));

        callback.invoke(null, null, 1, null, null, null);

        assertEquals(List.of(ROOT), changed);
    }
}
