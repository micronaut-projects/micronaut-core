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
package io.micronaut.reflection;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The types the shared {@link io.micronaut.core.beans.BeanIntrospector} may describe reflectively when it has
 * no generated introspection for them.
 *
 * <p>Reading a class back is what the framework is built to avoid, and a reflective introspection exposes every
 * member of a class to whatever asks for it, so no type is allowed until an application says so. A type is
 * allowed when its name matches one of the patterns of the {@value #PROPERTY_ALLOW_REFLECTION} property, set
 * either as a system property or in the application configuration, or one of the patterns
 * {@linkplain #allow(String...) allowed} programmatically. A pattern is a class name where {@code *} stands for
 * any sequence of characters, matched against the whole name: {@code com.example.model.*} allows the classes of
 * a package and of its sub packages, and only those - {@code com.example.modelling.Order} is not one of them -
 * {@code com.example.Order} allows one class, {@code *.Order} every class of that name and {@code *} every
 * class.</p>
 *
 * <p>The policy guards only the fallback of the shared introspector. Code that reflects on a type explicitly -
 * {@link ReflectionBeanIntrospection#of(Class)}, {@link ReflectionBeanIntrospector} - is not subject to it: that
 * code made its choice.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class ReflectionIntrospectionPolicy {

    /**
     * The property listing the patterns of the types the shared introspector may describe reflectively.
     */
    public static final String PROPERTY_ALLOW_REFLECTION = "micronaut.introspection.allow-reflection";

    private static final Predicate<Class<?>> NONE = type -> false;
    private static final Predicate<Class<?>> SYSTEM = patterns(split(System.getProperty(PROPERTY_ALLOW_REFLECTION)));
    // the policy is static, while a JVM can run several contexts at once: each configuration contributes its own
    // patterns and withdraws exactly those when it shuts down, so what applies is the union of the live ones and
    // one context neither replaces what another allowed nor takes it away by closing
    private static final Collection<Predicate<Class<?>>> CONFIGURED = new CopyOnWriteArrayList<>();
    // a predicate is added rather than folded into an or-chain, which makes the update atomic and bounds what a
    // check has to walk to the number of calls
    private static final Collection<Predicate<Class<?>>> ALLOWED = new CopyOnWriteArrayList<>();

    private ReflectionIntrospectionPolicy() {
    }

    /**
     * Whether a type is allowed.
     *
     * @param type The type
     * @return Whether the shared introspector may describe the type reflectively
     */
    public static boolean isAllowed(Class<?> type) {
        return SYSTEM.test(type) || anyAllows(CONFIGURED, type) || anyAllows(ALLOWED, type);
    }

    /**
     * Allows the types matching the patterns, in addition to the ones already allowed.
     *
     * @param patterns The patterns
     */
    public static void allow(String... patterns) {
        allow(List.of(patterns));
    }

    /**
     * Allows the types matching the patterns, in addition to the ones already allowed.
     *
     * @param patterns The patterns
     */
    public static void allow(Collection<String> patterns) {
        allow(patterns(patterns));
    }

    /**
     * Allows the types a predicate accepts, in addition to the ones already allowed. Callers of this method
     * never lose one another's predicate, and a type is allowed as soon as one of them accepts it.
     *
     * @param types The predicate
     */
    public static void allow(Predicate<Class<?>> types) {
        ALLOWED.add(types);
    }

    /**
     * Contributes the patterns an application configuration allows, in addition to the patterns the other
     * contributions, the system property and the programmatically allowed types allow. A contribution is not
     * a replacement: the contexts running at the same time in one JVM each contribute their own patterns, and
     * a context takes back exactly the patterns it contributed by
     * {@linkplain Registration#close() closing} the registration it was given.
     *
     * @param patterns The patterns
     * @return The registration of these patterns, to close when the configuration that contributed them goes
     * away
     */
    public static Registration configure(Collection<String> patterns) {
        Predicate<Class<?>> contribution = patterns(patterns);
        CONFIGURED.add(contribution);
        return new Contribution(contribution);
    }

    /**
     * Forgets everything configured and allowed: the patterns of every contribution, whether or not the
     * configuration that contributed them is still running, and every programmatically allowed type. The
     * system property still applies, as it is not something that can be taken back.
     */
    public static void reset() {
        CONFIGURED.clear();
        ALLOWED.clear();
    }

    /**
     * The predicate matching the class names of the given patterns.
     *
     * @param patterns The patterns, {@code *} standing for any sequence of characters
     * @return The predicate
     */
    static Predicate<Class<?>> patterns(Collection<String> patterns) {
        List<Pattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            String trimmed = pattern.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("*".equals(trimmed) || "**".equals(trimmed)) {
                return type -> true;
            }
            // the literals between the stars are quoted, so a dot of a package name is a dot and not any
            // character, and a star becomes the only wildcard there is; the regex is matched against the whole
            // name, which is why a leading star has to be written out as well as one in the middle
            String[] literals = trimmed.split("\\*", -1);
            StringBuilder regex = new StringBuilder(trimmed.length() + 8);
            for (int i = 0; i < literals.length; i++) {
                if (i > 0) {
                    regex.append(".*");
                }
                if (!literals[i].isEmpty()) {
                    regex.append(Pattern.quote(literals[i]));
                }
            }
            compiled.add(Pattern.compile(regex.toString()));
        }
        if (compiled.isEmpty()) {
            return NONE;
        }
        List<Pattern> matchers = List.copyOf(compiled);
        return type -> {
            String name = type.getName();
            for (Pattern matcher : matchers) {
                if (matcher.matcher(name).matches()) {
                    return true;
                }
            }
            return false;
        };
    }

    private static List<String> split(String value) {
        if (StringUtils.isEmpty(value)) {
            return List.of();
        }
        return List.of(value.split(","));
    }

    private static boolean anyAllows(Collection<Predicate<Class<?>>> predicates, Class<?> type) {
        for (Predicate<Class<?>> predicate : predicates) {
            if (predicate.test(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The patterns one {@link ReflectionIntrospectionPolicy#configure(Collection)} call contributed, which
     * closing takes back.
     */
    public interface Registration extends AutoCloseable {

        /**
         * Takes back the contributed patterns, leaving what the other contributions allow. Closing a
         * registration that was closed does nothing.
         */
        @Override
        void close();
    }

    private static final class Contribution implements Registration {

        private final Predicate<Class<?>> patterns;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Contribution(Predicate<Class<?>> patterns) {
            this.patterns = patterns;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                // by identity: two configurations contributing the same patterns hold two contributions, and
                // closing one has to leave the other one's
                CONFIGURED.remove(patterns);
            }
        }
    }
}
