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
package io.micronaut.web.router.resource;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A bounded in-memory cache of the content of small static resources. An entry of a file of the
 * file system is checked against the last-modified time and the length of the file on every hit,
 * and loaded again when they changed; a resource of the classpath does not change.
 *
 * @since 5.3.0
 */
@Internal
public final class StaticResourceCache {

    private static final String FILE_PROTOCOL = "file";

    private final int maxEntries;
    private final int maxEntrySize;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * @param maxEntries   The most entries
     * @param maxEntrySize The largest content cached, in bytes
     */
    public StaticResourceCache(int maxEntries, int maxEntrySize) {
        if (maxEntries <= 0 || maxEntrySize <= 0) {
            throw new IllegalArgumentException("The cache of static resources needs a positive size, not " + maxEntries + " entries of " + maxEntrySize + " bytes");
        }
        this.maxEntries = maxEntries;
        this.maxEntrySize = maxEntrySize;
    }

    /**
     * The content of a resource, from the cache, else loaded into it.
     *
     * @param url The URL of the resource
     * @return The content, or {@code null} if the resource is too large, or can not be read
     */
    public @Nullable Entry get(URL url) {
        String key = url.toExternalForm();
        Entry entry = entries.get(key);
        File file = toFile(url);
        if (file != null) {
            long lastModified = file.lastModified();
            long length = file.length();
            if (entry != null && entry.lastModified() == lastModified && entry.content().length == length) {
                return entry;
            }
            if (!file.isFile() || length > maxEntrySize) {
                entries.remove(key);
                return null;
            }
        } else if (entry != null) {
            return entry;
        }
        Entry loaded = load(url, file);
        if (loaded == null) {
            entries.remove(key);
            return null;
        }
        if (!entries.containsKey(key)) {
            evictIfFull();
        }
        entries.put(key, loaded);
        return loaded;
    }

    /**
     * @return The number of entries
     */
    public int size() {
        return entries.size();
    }

    private void evictIfFull() {
        Iterator<String> keys = entries.keySet().iterator();
        while (entries.size() >= maxEntries && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    private @Nullable Entry load(URL url, @Nullable File file) {
        try {
            URLConnection connection = url.openConnection();
            long length = connection.getContentLengthLong();
            if (length > maxEntrySize) {
                connection.getInputStream().close();
                return null;
            }
            long lastModified = file != null ? file.lastModified() : connection.getLastModified();
            byte[] content;
            try (InputStream stream = connection.getInputStream()) {
                content = stream.readNBytes(maxEntrySize + 1);
            }
            if (content.length > maxEntrySize) {
                return null;
            }
            if (file != null && (file.lastModified() != lastModified || file.length() != content.length)) {
                // changed while read
                return null;
            }
            return new Entry(content, lastModified);
        } catch (IOException | UncheckedIOException e) {
            return null;
        }
    }

    private static @Nullable File toFile(URL url) {
        if (!FILE_PROTOCOL.equals(url.getProtocol())) {
            return null;
        }
        try {
            return Paths.get(url.toURI()).toFile();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * The cached content of a resource.
     *
     * @param content      The content, never modified
     * @param lastModified The last-modified time of the resource
     */
    @SuppressWarnings("java:S6218") // compared by identity
    public record Entry(byte[] content, long lastModified) {
    }
}
