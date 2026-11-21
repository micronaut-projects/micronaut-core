package io.micronaut.dev.compile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A process that holds a share of a lock file, as another development process of the project holds its share of the
 * sibling output: run with the JDK alone on its class path.
 */
public final class SharedLockHolder {

    private SharedLockHolder() {
    }

    /**
     * Holds a share of the lock file given as the argument and prints {@code held} once it does; lets go when its
     * standard input closes.
     *
     * @param args The lock file
     * @throws IOException if the lock cannot be taken
     */
    public static void main(String[] args) throws IOException {
        try (FileChannel channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock(0, Long.MAX_VALUE, true)) {
            System.out.println("held");
            System.out.flush();
            InputStream in = System.in;
            while (in.read() != -1) {
                // until the test lets go
            }
        }
    }
}
