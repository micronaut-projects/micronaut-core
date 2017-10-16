package org.particleframework.context;

import java.io.Closeable;
import java.io.IOException;

/**
 * A life cycle interface providing a start method and extending Closeable which provides a close() method for termination
 *
 * Components can implement this interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface LifeCycle<T extends LifeCycle> extends Closeable, AutoCloseable  {
    /**
     * Starts the lifecyle component
     */
    T start();

    /**
     * Stops the life cycle component
     */
    T stop();

    /**
     * Delegates to {@link #stop()}
     */
    @Override
    default void close() throws IOException {
        stop();
    }

    /**
     * Refreshes the current life cycle object. Effectively this calls {@link #stop()} followed by {@link #start()}
     *
     * @return This lifecycle component
     */
    default T refresh() {
        stop();
        start();
        return (T) this;
    }
}
