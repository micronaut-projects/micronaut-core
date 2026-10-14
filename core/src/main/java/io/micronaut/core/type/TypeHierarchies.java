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
package io.micronaut.core.type;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.util.ArgumentUtils;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Finds the {@link TypeHierarchy} records a compilation wrote.
 *
 * <p>A record is a generated class, registered as a {@code META-INF/micronaut} service of {@link TypeHierarchy},
 * whose name says which type it describes. A lookup reads the names of the service entries of the class loader,
 * which the service index of a packaged application already holds, and loads the one record of the type asked for:
 * no other record is loaded, and the class asked about is not read reflectively. Each lookup instantiates the
 * record anew, which is a handful of {@link Argument}s; a caller that asks for a hierarchy repeatedly keeps it.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class TypeHierarchies {

    /**
     * The suffix of the name of a record.
     */
    public static final String RECORD_SUFFIX = "$TypeHierarchy";

    private static final String SERVICE_NAME = TypeHierarchy.class.getName();
    private static final String RELOCATED_PREFIX = "$$";

    // the names of the records of a class loader, by the name of the type each describes; strings only, so that the
    // loader stays collectable
    private static final Map<ClassLoader, Map<String, String>> INDEXES = Collections.synchronizedMap(new WeakHashMap<>());

    private TypeHierarchies() {
    }

    /**
     * Finds the hierarchy recorded for a type, in the class loader of the type and then in the context class loader
     * of the thread.
     *
     * @param type The type
     * @return The hierarchy, if one was recorded
     */
    public static Optional<TypeHierarchy> find(Class<?> type) {
        ArgumentUtils.requireNonNull("type", type);
        ClassLoader classLoader = type.getClassLoader();
        if (classLoader == null) {
            classLoader = TypeHierarchies.class.getClassLoader();
        }
        Optional<TypeHierarchy> hierarchy = find(type.getName(), classLoader).filter(found -> found.getType() == type);
        if (hierarchy.isEmpty()) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null && contextClassLoader != classLoader) {
                // another loader can define a class of the same name: a record of that class is not one of this type
                hierarchy = find(type.getName(), contextClassLoader).filter(found -> found.getType() == type);
            }
        }
        return hierarchy;
    }

    /**
     * Finds the hierarchy recorded for a type by its name, in the context class loader of the thread or, when the
     * thread has none, the class loader of this class.
     *
     * @param typeName The name of the type, as {@link Class#getName()} gives it
     * @return The hierarchy, if one was recorded
     */
    public static Optional<TypeHierarchy> find(String typeName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return find(typeName, classLoader == null ? TypeHierarchies.class.getClassLoader() : classLoader);
    }

    /**
     * Finds the hierarchy recorded for a type by its name, among the records a class loader sees.
     *
     * @param typeName    The name of the type, as {@link Class#getName()} gives it
     * @param classLoader The class loader
     * @return The hierarchy, if one was recorded
     */
    public static Optional<TypeHierarchy> find(String typeName, ClassLoader classLoader) {
        ArgumentUtils.requireNonNull("typeName", typeName);
        ArgumentUtils.requireNonNull("classLoader", classLoader);
        String recordName = index(classLoader).get(typeName);
        if (recordName == null) {
            return Optional.empty();
        }
        return SoftServiceLoader.load(TypeHierarchy.class, classLoader, recordName::equals).firstAvailable();
    }

    /**
     * Forgets the records found so far, for a class path that changed.
     */
    public static void invalidate() {
        INDEXES.clear();
    }

    /**
     * The name of the record of a type. A record written into the package of the type is named after the type,
     * {@code com.example.$Foo$TypeHierarchy} for {@code com.example.Foo}. A record written into another package, as a
     * record of a public class of a library or of the JDK is, goes into a sub-package of that package named after
     * the package of the type, with {@code $$} in front of its first segment: {@code com.example.$$java.util.$ArrayList$TypeHierarchy}.
     * Every segment stays a Java identifier, so no character of a type name is escaped and the type name reads back
     * without ambiguity; a source package whose segment starts with {@code $$} is not expected.
     *
     * @param typeName      The name of the type
     * @param recordPackage The package the record is written into
     * @return The name of the record
     */
    @Internal
    public static String recordClassName(String typeName, String recordPackage) {
        int lastDot = typeName.lastIndexOf('.');
        String typePackage = lastDot < 0 ? "" : typeName.substring(0, lastDot);
        String simpleName = typeName.substring(lastDot + 1);
        if (typePackage.equals(recordPackage) || typePackage.isEmpty()) {
            // a type of the default package cannot be named from another package: its record stays there
            String prefix = typePackage.isEmpty() ? "" : typePackage + '.';
            return prefix + '$' + simpleName + RECORD_SUFFIX;
        }
        String prefix = recordPackage.isEmpty() ? "" : recordPackage + '.';
        return prefix + RELOCATED_PREFIX + typePackage + ".$" + simpleName + RECORD_SUFFIX;
    }

    /**
     * The name of the type a record describes, the inverse of {@link #recordClassName(String, String)}.
     *
     * @param recordClassName The name of the record
     * @return The name of the type, or {@code null} if the name is not that of a record
     */
    @Internal
    public static @Nullable String recordedTypeName(String recordClassName) {
        if (!recordClassName.endsWith(RECORD_SUFFIX)) {
            return null;
        }
        String name = recordClassName.substring(0, recordClassName.length() - RECORD_SUFFIX.length());
        int lastDot = name.lastIndexOf('.');
        String lastSegment = name.substring(lastDot + 1);
        if (lastSegment.length() < 2 || lastSegment.charAt(0) != '$') {
            return null;
        }
        String simpleName = lastSegment.substring(1);
        String packages = lastDot < 0 ? "" : name.substring(0, lastDot);
        int relocated = packages.startsWith(RELOCATED_PREFIX) ? 0 : packages.indexOf("." + RELOCATED_PREFIX);
        if (relocated < 0) {
            return packages.isEmpty() ? simpleName : packages + '.' + simpleName;
        }
        String typePackage = packages.substring(relocated == 0 ? RELOCATED_PREFIX.length() : relocated + 1 + RELOCATED_PREFIX.length());
        if (typePackage.isEmpty() || typePackage.startsWith(".")) {
            return null;
        }
        return typePackage + '.' + simpleName;
    }

    private static Map<String, String> index(ClassLoader classLoader) {
        Map<String, String> index = INDEXES.get(classLoader);
        if (index == null) {
            index = buildIndex(classLoader);
            INDEXES.put(classLoader, index);
        }
        return index;
    }

    private static Map<String, String> buildIndex(ClassLoader classLoader) {
        Set<String> recordNames;
        try {
            recordNames = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, SERVICE_NAME);
        } catch (IOException e) {
            throw new ServiceConfigurationError("Failed to read the type hierarchy records: " + e.getMessage(), e);
        }
        Map<String, String> index = new HashMap<>(recordNames.size());
        for (String recordName : recordNames) {
            String typeName = recordedTypeName(recordName);
            if (typeName != null) {
                // the same type recorded by two compilations: either record says the same
                index.putIfAbsent(typeName, recordName);
            }
        }
        return index.isEmpty() ? Map.of() : Map.copyOf(index);
    }
}
