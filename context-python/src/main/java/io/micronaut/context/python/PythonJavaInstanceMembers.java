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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The snake_case methods and read-only getter properties the Python compiler computed for the Java types
 * the sources reference, which the Java object fallback of every context answers with (see
 * {@link GraalPyContextFactory}).
 * <p>
 * Each compilation writes its names to {@value #RESOURCE}, one line per name: the binary name of the Java
 * type, the Python name, the Java method and {@code property}, {@code method} or {@code blocked}. They are
 * read once per class loader and shared by all its contexts, so a context holds no table of its own. A
 * name applies to the instances of the type and of its implementations: the class of a receiver is
 * matched, once per class, through the names of its superclasses and interfaces, without loading the
 * recorded types or inspecting a member. A name that two of them define differently, or one blocks as
 * ambiguous, is no alias.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonJavaInstanceMembers {

    /** The resource each compilation records its names in. */
    public static final String RESOURCE = "META-INF/pyronaut/java-instance-members.tsv";

    private static final String PROPERTY = "property";
    private static final String METHOD = "method";
    private static final Member BLOCKED = new Member("", false);
    private static final Map<ClassLoader, PythonJavaInstanceMembers> BY_CLASS_LOADER = Collections.synchronizedMap(new WeakHashMap<>());

    // binary name of a Java type -> Python name -> member
    private final Map<String, Map<String, Member>> types;
    // ClassValue does not keep the receiver's class loader alive.
    private final ClassValue<Map<String, Member>> members = new ClassValue<>() {
        @Override
        protected Map<String, Member> computeValue(Class<?> type) {
            Map<String, Member> result = new HashMap<>();
            for (Class<?> current : hierarchy(type)) {
                Map<String, Member> recorded = types.get(current.getName());
                if (recorded != null) {
                    recorded.forEach((name, member) -> result.merge(name, member, (first, second) -> first.equals(second) ? first : BLOCKED));
                }
            }
            return result.isEmpty() ? Map.of() : Map.copyOf(result);
        }
    };

    private PythonJavaInstanceMembers(Map<String, Map<String, Member>> types) {
        this.types = types;
    }

    /**
     * @param classLoader The class loader of the application
     * @return The names its compilations recorded
     */
    static PythonJavaInstanceMembers of(ClassLoader classLoader) {
        return BY_CLASS_LOADER.computeIfAbsent(classLoader, PythonJavaInstanceMembers::load);
    }

    /**
     * @param receiver The Java instance
     * @param name The Python name
     * @return The Java method the snake_case name stands for, or {@code null}
     */
    @UsedByGeneratedCode
    public @Nullable String methodAlias(Object receiver, String name) {
        Member member = members.get(receiver.getClass()).get(name);
        return member != null && !member.property() && !member.isBlocked() ? member.target() : null;
    }

    /**
     * @param receiver The Java instance
     * @param name The Python name
     * @return The zero-argument getter the property stands for, or {@code null}
     */
    @UsedByGeneratedCode
    public @Nullable String propertyGetter(Object receiver, String name) {
        Member member = members.get(receiver.getClass()).get(name);
        return member != null && member.property() ? member.target() : null;
    }

    /**
     * The class, its superclasses and its interfaces: the names of a type's hierarchy, never its members.
     */
    private static Set<Class<?>> hierarchy(Class<?> type) {
        Set<Class<?>> hierarchy = new LinkedHashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            interfaces(current, hierarchy);
        }
        return hierarchy;
    }

    private static void interfaces(Class<?> type, Set<Class<?>> hierarchy) {
        if (hierarchy.add(type)) {
            for (Class<?> parent : type.getInterfaces()) {
                interfaces(parent, hierarchy);
            }
        }
    }

    private static PythonJavaInstanceMembers load(ClassLoader classLoader) {
        Map<String, Map<String, Member>> types = new HashMap<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(RESOURCE);
            while (resources.hasMoreElements()) {
                read(resources.nextElement(), types);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the Java instance member names of the Python sources", e);
        }
        return new PythonJavaInstanceMembers(types);
    }

    private static void read(URL resource, Map<String, Map<String, Member>> types) throws IOException {
        try (InputStream stream = resource.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\t", -1);
                if (columns.length != 4) {
                    continue;
                }
                Member member = switch (columns[3]) {
                    case PROPERTY -> new Member(columns[2], true);
                    case METHOD -> new Member(columns[2], false);
                    default -> BLOCKED;
                };
                // computed from the same class path type by every compilation; a disagreement is no alias
                types.computeIfAbsent(columns[0], type -> new HashMap<>())
                    .merge(columns[1], member, (first, second) -> first.equals(second) ? first : BLOCKED);
            }
        }
    }

    /**
     * @param target The Java method, empty when blocked
     * @param property Whether the name is a read-only property calling the getter
     */
    private record Member(String target, boolean property) {

        boolean isBlocked() {
            return target.isEmpty();
        }
    }
}
