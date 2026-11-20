package io.micronaut.dev.compile;

import java.io.IOException;

/**
 * What a test of another package needs of a {@link SiblingOutput}.
 */
public final class SiblingOutputs {

    private SiblingOutputs() {
    }

    /**
     * Releases this JVM's share of the directory, as the end of the process that held it does.
     *
     * @param siblings The sibling output
     * @throws IOException if the lock cannot be released
     */
    public static void release(SiblingOutput siblings) throws IOException {
        siblings.release();
    }
}
