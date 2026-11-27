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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Cleanup of domain socket files a previous process left behind.
 */
@Internal
final class DomainSocketFiles {

    private static final Logger LOG = LoggerFactory.getLogger(DomainSocketFiles.class);

    private DomainSocketFiles() {
    }

    /**
     * Remove the socket file a previous process left behind when it did not close its listener,
     * e.g. after a crash or kill -9. The file is only removed when it is a socket and nothing
     * accepts connections on it, so a live server keeps its socket and the bind fails as before.
     *
     * @param path The path of the domain socket
     */
    static void removeStale(String path) {
        if (path.startsWith("\0")) {
            // abstract sockets have no file
            return;
        }
        Path file;
        try {
            file = Path.of(path);
            if (!isSocketFile(file) || !isRefusingConnections(file)) {
                return;
            }
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            return;
        }
        try {
            if (Files.deleteIfExists(file) && LOG.isInfoEnabled()) {
                LOG.info("Removed stale domain socket file {}", path);
            }
        } catch (IOException e) {
            LOG.debug("Failed to remove stale domain socket file {}", path, e);
        }
    }

    private static boolean isRefusingConnections(Path file) {
        try {
            // a server accepts connections on it
            SocketChannel.open(UnixDomainSocketAddress.of(file)).close();
            return false;
        } catch (ConnectException e) {
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    private static boolean isSocketFile(Path file) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        // S_IFMT (0170000) / S_IFSOCK (0140000) of the unix file mode
        int mode = (Integer) Files.getAttribute(file, "unix:mode", LinkOption.NOFOLLOW_LINKS);
        return (mode & 0xF000) == 0xC000;
    }
}
