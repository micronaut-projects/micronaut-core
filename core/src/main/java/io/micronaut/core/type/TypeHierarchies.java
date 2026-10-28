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
import io.micronaut.core.util.CollectionUtils;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Finds the {@link TypeHierarchy} records a compilation wrote.
 *
 * <p>A record is a generated class, registered as a {@code META-INF/micronaut} service of {@link TypeHierarchy},
 * whose name says which type it describes and which top-level class of the compilation recorded it. A lookup reads
 * the names of the service entries of the class loader, which the service index of a packaged application already
 * holds, and loads the record of the type asked for: no record of another type is loaded, and the class asked about
 * is not read reflectively. A type recorded from several classes has a record for each, and a record written outside
 * the package of the type can have left out super types it could not name
 * ({@link TypeHierarchy#getOmittedSuperTypeNames()}): a record in the package of the type is preferred, otherwise the
 * one that left out the fewest, the first by name among those, whatever order the class path lists them in. Each
 * record found is instantiated once: the lookups that follow in the class loader find the same instance, which does
 * not keep the class loader of the record from being collected.</p>
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
    private static final String ORIGIN_SEPARATOR = "$$";

    // the names of the records of a class loader, by the name of the type each describes, the preferred one first, and
    // the records found so far, held weakly: nothing of the index holds a class of the loader, which stays collectable
    private static final Map<ClassLoader, Index> INDEXES = Collections.synchronizedMap(new WeakHashMap<>());
    // the record found of a record class, held by the class: a record a disposable class loader defines is collected
    // with it, which a cache keyed by the loader, or by the class the record describes, would prevent
    private static final ClassValue<AtomicReference<Found>> FOUND = new ClassValue<>() {
        @Override
        protected AtomicReference<Found> computeValue(Class<?> type) {
            return new AtomicReference<>();
        }
    };
    private static final WeakReference<TypeHierarchy> NOT_FOUND = new WeakReference<>(null);
    private static final AtomicInteger GENERATION = new AtomicInteger();

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
     * Finds the hierarchy recorded for a type by its name, among the records a class loader sees. Of several records
     * of the type, written from several packages, the one in the package of the type is found, otherwise the one that
     * left out the fewest super types, the first by name among those.
     *
     * @param typeName    The name of the type, as {@link Class#getName()} gives it
     * @param classLoader The class loader
     * @return The hierarchy, if one was recorded
     */
    public static Optional<TypeHierarchy> find(String typeName, ClassLoader classLoader) {
        ArgumentUtils.requireNonNull("typeName", typeName);
        ArgumentUtils.requireNonNull("classLoader", classLoader);
        Index index = index(classLoader);
        List<String> recordNames = index.recordNames.get(typeName);
        if (recordNames == null) {
            return Optional.empty();
        }
        WeakReference<TypeHierarchy> found = index.found.get(typeName);
        TypeHierarchy hierarchy = found == null ? null : found.get();
        if (hierarchy == null && found != NOT_FOUND) {
            hierarchy = load(typeName, recordNames, classLoader);
            index.found.put(typeName, hierarchy == null ? NOT_FOUND : new WeakReference<>(hierarchy));
        }
        return Optional.ofNullable(hierarchy);
    }

    private static @Nullable TypeHierarchy load(String typeName, List<String> recordNames, ClassLoader classLoader) {
        String typePackage = packageOf(typeName);
        TypeHierarchy preferred = null;
        for (String recordName : recordNames) {
            Optional<TypeHierarchy> hierarchy = SoftServiceLoader.load(TypeHierarchy.class, classLoader, recordName::equals).firstAvailable();
            if (hierarchy.isEmpty()) {
                continue;
            }
            if (packageOf(recordName).equals(typePackage)) {
                // a record in the package of the type, first in the list, names every class of the package: the
                // records of the several classes that recorded the type there are the same
                return once(hierarchy.get());
            }
            // records written from other packages can each have left out other super types: the one that left out
            // the fewest, the first by name among those, so that the choice does not depend on the class path order
            if (preferred == null || hierarchy.get().getOmittedSuperTypeNames().size() < preferred.getOmittedSuperTypeNames().size()) {
                preferred = hierarchy.get();
            }
        }
        return preferred == null ? null : once(preferred);
    }

    // the record of its class found first, unless the records were invalidated since
    private static TypeHierarchy once(TypeHierarchy hierarchy) {
        AtomicReference<Found> reference = FOUND.get(hierarchy.getClass());
        int generation = GENERATION.get();
        while (true) {
            Found found = reference.get();
            if (found != null && found.generation == generation) {
                return found.hierarchy;
            }
            if (reference.compareAndSet(found, new Found(hierarchy, generation))) {
                return hierarchy;
            }
        }
    }

    /**
     * Forgets the records found so far, for a class path that changed. A lookup that overlaps the call can still
     * publish what it read before the change; a caller that reloads classes invalidates after the change is complete.
     */
    public static void invalidate() {
        // the names of the service entries are cached as well, by the loader utilities
        MicronautMetaServiceLoaderUtils.invalidate();
        GENERATION.incrementAndGet();
        INDEXES.clear();
    }

    /**
     * The name of the record of a type, as written before records were named after the class that recorded them,
     * see {@link #recordClassName(String, String, String)}. A record of that name is still found.
     *
     * @param typeName      The name of the type
     * @param recordPackage The package the record is written into
     * @return The name of the record
     */
    @Internal
    public static String recordClassName(String typeName, String recordPackage) {
        return recordClassName(typeName, recordPackage, null);
    }

    /**
     * The name of the record of a type. A record written into the package of the type is named after the type,
     * {@code com.example.$Foo$TypeHierarchy} for {@code com.example.Foo}. A record written into another package, as a
     * record of a public class of a library or of the JDK is, goes into a sub-package of that package named after
     * the package of the type, with {@code $$} in front of its first segment: {@code com.example.$$java.util.$ArrayList$TypeHierarchy}.
     * Every segment stays a Java identifier, so no character of a type name is escaped and the type name reads back
     * without ambiguity; a source package whose segment starts with {@code $$} is not expected.
     *
     * <p>Each top-level class of a compilation that records a type writes a record of its own, named after it with
     * {@code $$} in front: {@code com.example.$Foo$TypeHierarchy$$Origin}, its dollar signs replaced by underscores.
     * The record and its service entry then belong to that class alone, and an incremental compilation that
     * recompiles or deletes the class drops its record only, not one other classes still need.</p>
     *
     * @param typeName      The name of the type
     * @param recordPackage The package the record is written into
     * @param originName    The simple name of the top-level class that records the type, or null for a record
     *                      that belongs to no class
     * @return The name of the record
     */
    @Internal
    public static String recordClassName(String typeName, String recordPackage, @Nullable String originName) {
        String recordName = recordClassNameOf(typeName, recordPackage);
        if (originName == null || originName.isEmpty()) {
            return recordName;
        }
        return recordName + ORIGIN_SEPARATOR + originName.replace('$', '_');
    }

    private static String recordClassNameOf(String typeName, String recordPackage) {
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
     * The name of the type a record describes, the inverse of {@link #recordClassName(String, String, String)}, for
     * the names with and without the class that recorded the type.
     *
     * @param recordClassName The name of the record
     * @return The name of the type, or {@code null} if the name is not that of a record
     */
    @Internal
    public static @Nullable String recordedTypeName(String recordClassName) {
        String name;
        int origin = recordClassName.lastIndexOf(RECORD_SUFFIX + ORIGIN_SEPARATOR);
        int originStart = origin + RECORD_SUFFIX.length() + ORIGIN_SEPARATOR.length();
        if (origin > 0 && originStart < recordClassName.length() && recordClassName.indexOf('$', originStart) < 0
            && recordClassName.indexOf('.', originStart) < 0) {
            // the origin, a class name without dollar signs, follows the suffix
            name = recordClassName.substring(0, origin);
        } else if (recordClassName.endsWith(RECORD_SUFFIX)) {
            name = recordClassName.substring(0, recordClassName.length() - RECORD_SUFFIX.length());
        } else {
            return null;
        }
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

    private static Index index(ClassLoader classLoader) {
        Index index = INDEXES.get(classLoader);
        if (index == null) {
            index = new Index(buildIndex(classLoader), new ConcurrentHashMap<>());
            Index existing = INDEXES.putIfAbsent(classLoader, index);
            if (existing != null) {
                index = existing;
            }
        }
        return index;
    }

    private static Map<String, List<String>> buildIndex(ClassLoader classLoader) {
        Set<String> recordNames;
        try {
            recordNames = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, SERVICE_NAME);
        } catch (IOException e) {
            throw new ServiceConfigurationError("Failed to read the type hierarchy records: " + e.getMessage(), e);
        }
        Map<String, List<String>> index = CollectionUtils.newHashMap(recordNames.size());
        for (String recordName : recordNames) {
            String typeName = recordedTypeName(recordName);
            if (typeName != null) {
                index.computeIfAbsent(typeName, name -> new ArrayList<>(1)).add(recordName);
            }
        }
        if (index.isEmpty()) {
            return Map.of();
        }
        // a type can be recorded from several classes and packages, which name what they could: the records in the
        // package of the type first, then the others, each by name, whatever order the class path lists them in
        index.replaceAll((typeName, names) -> {
            String typePackage = packageOf(typeName);
            names.sort(Comparator.comparing((String name) -> !packageOf(name).equals(typePackage)).thenComparing(Comparator.naturalOrder()));
            return List.copyOf(names);
        });
        return Map.copyOf(index);
    }

    private static String packageOf(String typeName) {
        int lastDot = typeName.lastIndexOf('.');
        return lastDot < 0 ? "" : typeName.substring(0, lastDot);
    }

    /**
     * The records of a class loader.
     *
     * @param recordNames The names of the records, by the name of the type each describes, the preferred one first
     * @param found       The records found, by the name of the type each describes
     */
    private record Index(Map<String, List<String>> recordNames, ConcurrentMap<String, WeakReference<TypeHierarchy>> found) {
    }

    /**
     * The record of a record class found first.
     *
     * @param hierarchy  The record
     * @param generation The {@link #invalidate() generation} it was found in
     */
    private record Found(TypeHierarchy hierarchy, int generation) {
    }
}
