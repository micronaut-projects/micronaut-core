/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.core.optim;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is a generic container for pre-computed data
 * which can be injected at initialization time. Every class
 * which needs static optimizations should go through this
 * class to get its static state.
 *
 * @since 3.2.0
 */
@SuppressWarnings("unchecked")
@Internal
public abstract class StaticOptimizations {

    private static final boolean CAPTURE_STACKTRACE_ON_READ = Boolean.getBoolean("micronaut.optimizations.capture.read.trace");

    // The system property that GraalVM sets in image code, to "buildtime" or to "runtime". NativeImageUtils reads
    // it too, and is not used here so that this package does not depend on io.micronaut.core.util, which already
    // depends on this package through io.micronaut.core.reflect
    private static final String IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";

    private static final Map<Class<?>, Object> OPTIMIZATIONS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, StackTraceElement[]> CHECKED = new ConcurrentHashMap<>();
    private static final StackTraceElement[] EMPTY_STACK_TRACE_ELEMENT_ARRAY = new StackTraceElement[0];

    private static boolean cacheEnvironment = false;

    static {
        // from here on SetOnce.find reads what the loaders have set so far, and does not wait for them
        SetOnceValues.loadersStarted = true;
        reset();
    }

    /**
     * Resets the internal state of the optimization container.
     * Visible for testing.
     */
    static void reset() {
        OPTIMIZATIONS.clear();
        SetOnceValues.BY_CLASS_NAME.clear();
        CHECKED.clear();
        ServiceLoader.load(Loader.class).forEach(loader -> set(loader.load()));
    }

    /**
     * Enables environment caching. If enabled, both the environment variables
     * and system properties will be deemed immutable.
     */
    public static void cacheEnvironment() {
        cacheEnvironment = true;
    }

    /**
     * Returns, if available, the optimization data of the requested
     * type. Those optimizations are singletons, which is why they
     * are accessed by type.
     *
     * @param optimizationClass the type of the optimization class
     * @param <T> the optimization type
     * @return the optimization if set, otherwise empty
     */
    public static <T> Optional<T> get(Class<T> optimizationClass) {
        CHECKED.put(optimizationClass, maybeCaptureStackTrace());
        T value = (T) OPTIMIZATIONS.get(optimizationClass);
        return Optional.ofNullable(value);
    }

    private static StackTraceElement[] maybeCaptureStackTrace() {
        if (CAPTURE_STACKTRACE_ON_READ) {
            return new Exception().getStackTrace();
        }
        return EMPTY_STACK_TRACE_ELEMENT_ARRAY;
    }

    /**
     * Does nothing. Calling it initializes this class, which runs the loaders, as calling any of its static methods
     * does.
     */
    @SuppressWarnings("java:S3398") // in SetOnce, calling it would not initialize this class
    private static void runLoaders() {
        // the loaders run in the static initializer
    }

    /**
     * Injects an optimization. Optimizations must be immutable final
     * data classes: there's a one-to-one mapping between an optimization
     * class and its associated data. A value replaces the previous value
     * of its class, except for a {@link SetOnce} optimization, which can
     * only be set once. A {@link JvmOnly} optimization is not stored in
     * native image code.
     * @param value the optimization to store
     * @param <T> the type of the optimization
     */
    public static <T> void set(T value) {
        Class<?> optimizationClass = value.getClass();
        if (value instanceof JvmOnly && System.getProperty(IMAGE_CODE_PROPERTY) != null) {
            // nothing reads the value in a native image, and this class is initialized when the image is built, so
            // storing the value would only copy it into the image heap
            return;
        }
        SetOnce setOnce = value instanceof SetOnce s ? s : null;
        if (setOnce != null && SetOnceValues.BY_CLASS_NAME.containsKey(optimizationClass.getName())) {
            throw alreadySet(optimizationClass);
        }
        if (CHECKED.containsKey(optimizationClass)) {
            if (!CAPTURE_STACKTRACE_ON_READ) {
                throw new IllegalStateException("Optimization state for " + optimizationClass + " was read before it was set. Run with -Dmicronaut.optimizations.capture.read.trace=true to enable stack trace capture.");
            }
            StringBuilder sb = new StringBuilder("Optimization state for " + optimizationClass + " was read before it was set. Stack trace:\n");
            StackTraceElement[] stackTrace = CHECKED.get(optimizationClass);
            for (StackTraceElement element : stackTrace) {
                sb.append("\t").append(element.toString()).append("\n");
            }
            throw new IllegalStateException(sb.toString());
        }
        if (setOnce != null && SetOnceValues.BY_CLASS_NAME.putIfAbsent(optimizationClass.getName(), setOnce) != null) {
            // a concurrent call set a value after the check above, so exactly one of the calls succeeds
            throw alreadySet(optimizationClass);
        }
        OPTIMIZATIONS.put(optimizationClass, value);
    }

    private static IllegalStateException alreadySet(Class<?> optimizationClass) {
        return new IllegalStateException("An optimization of " + optimizationClass + " was already set: it can only be set once");
    }

    /**
     * Returns true if the environment should be cached, that is to say
     * if the environment variables and system properties are deemed
     * immutable during the whole application run time.
     * @return true if the environment is cached
     */
    public static boolean isEnvironmentCached() {
        return cacheEnvironment;
    }

    /**
     * Marks an optimization that can be set at most once: setting a second value of its class fails, instead of
     * replacing the first one. It is for an optimization that describes the whole application, of which two values
     * cannot both be right. Such an optimization can be read with {@link #find(String)}.
     *
     * @since 5.3.0
     */
    @Internal
    public interface SetOnce {

        /**
         * Returns, if it was set, the set-once optimization of the named class.
         *
         * <p>Unlike {@link StaticOptimizations#get(Class)}, the lookup is by class name, so a caller does not have
         * to load the class of an optimization that was never set, and the read is not recorded, so it does not make
         * a later {@link StaticOptimizations#set(Object)} fail. That is safe for a value that is never replaced, as
         * long as the caller calls this method on each use instead of keeping the result: a read that comes before
         * the value is set, for example from a {@link Loader} that runs before the loader of the value, finds
         * nothing, and a later read finds the value.</p>
         *
         * <p>The first call runs the loaders, if nothing has run them yet. A call that is made once a thread has
         * started to run them does not wait for that thread: it returns what is set so far. Any static method of
         * {@link StaticOptimizations} would wait, and a caller that the thread of the loaders waits for in turn
         * would then never return. That is the case of a loader that looks services up with fork-join tasks, when
         * one of those services looks a service up from the thread of the pool that instantiates it. It is why
         * this method is not a static method of {@link StaticOptimizations}.</p>
         *
         * @param optimizationClassName the name of the optimization class
         * @return the optimization, or null if it is not set
         */
        static @Nullable SetOnce find(String optimizationClassName) {
            if (!SetOnceValues.loadersStarted) {
                // no thread has started to run the loaders: this one runs them, or waits for the one that does
                runLoaders();
            }
            return SetOnceValues.BY_CLASS_NAME.get(optimizationClassName);
        }
    }

    /**
     * Marks an optimization that is only read on the JVM. {@link StaticOptimizations#set(Object)} does not store it
     * when it is called in native image code, which includes the build of the image.
     *
     * @since 5.3.0
     */
    @Internal
    public interface JvmOnly {
    }

    /**
     * The {@link SetOnce} optimizations, and whether the loaders have started to run. They are in a class of their
     * own so that {@link SetOnce#find(String)} can read them without initializing {@link StaticOptimizations}, which
     * would wait for the thread that runs the loaders.
     */
    private static final class SetOnceValues {
        static final Map<String, SetOnce> BY_CLASS_NAME = new ConcurrentHashMap<>();
        // set when the initialization of StaticOptimizations starts, and never reset
        static volatile boolean loadersStarted;

        private SetOnceValues() {
        }
    }

    /**
     * Interface for an optimization which will be injected via
     * service loading.
     *
     * @param <T> the type of the optimization
     * @since 3.3.0
     */
    @FunctionalInterface
    public interface Loader<T> {
        /**
         * The static optimization to be injected. The {@link StaticOptimizations#set(Object)} method
         * will automatically be called with the value returned by this method.
         * @return the optimization value.
         */
        T load();
    }
}
