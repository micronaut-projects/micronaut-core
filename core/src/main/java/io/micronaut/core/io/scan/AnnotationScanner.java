/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.io.scan;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import io.micronaut.core.annotation.NonNull;

/**
 * Interface for classes that scan for classes with a given annotation.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotationScanner {
    /**
     * Scan the given packages.
     *
     * @param annotation The annotation to scan for
     * @param pkg        The package to scan
     * @return A stream of classes
     */
    @NonNull Stream<Class<?>> scan(@NonNull String annotation, @NonNull String pkg);

    /**
     * Scan the given packages.
     *
     * @param annotation The annotation to scan for
     * @param packages   The packages to scan
     * @return A stream of classes
     */
    default @NonNull Stream<Class<?>> scan(@NonNull String annotation, @NonNull Package... packages) {
        Objects.requireNonNull(annotation, "Annotation type cannot be null");
        Objects.requireNonNull(packages, "Packages to scan cannot be null");

        return Arrays.stream(packages)
                     .parallel()
                     .flatMap(pkg -> scan(annotation, pkg.getName()));
    }

    /**
     * Scans the given package names.
     *
     * @param annotation The annotation to scan
     * @param packages   The packages
     * @return A stream of classes
     */
    default @NonNull Stream<Class<?>> scan(@NonNull Class<? extends Annotation> annotation, @NonNull Package... packages) {
        Objects.requireNonNull(annotation, "Annotation type cannot be null");
        Objects.requireNonNull(packages, "Packages to scan cannot be null");

        return scan(annotation.getName(), packages);
    }

    /**
     * Scan the given packages.
     *
     * @param annotation The annotation to scan for
     * @param pkg        The package to scan
     * @return A stream of classes
     */
    default @NonNull Stream<Class<?>> scan(@NonNull Class<? extends Annotation> annotation, @NonNull Package pkg) {
        return scan(annotation.getName(), pkg.getName());
    }

    /**
     * Scans the given package names.
     *
     * @param annotation The annotation name to scan
     * @param packages   The package names
     * @return A stream of classes
     */
    default @NonNull Stream<Class<?>> scan(@NonNull String annotation, @NonNull String... packages) {
        Objects.requireNonNull(annotation, "Annotation type cannot be null");
        Objects.requireNonNull(packages, "Packages to scan cannot be null");

        Stream<String> stream = Arrays.stream(packages);
        return scan(annotation, stream);
    }

    /**
     * Scans the given package names.
     *
     * @param annotation The annotation name to scan
     * @param packages   The package names
     * @return A stream of classes
     */
    default @NonNull Stream<Class<?>> scan(@NonNull String annotation, @NonNull Collection<String> packages) {
        Objects.requireNonNull(annotation, "Annotation type cannot be null");
        Objects.requireNonNull(packages, "Packages to scan cannot be null");

        return scan(annotation, packages.parallelStream());
    }

    /**
     * Scans the given package names.
     *
     * @param annotation The annotation name to scan
     * @param packages   The package names
     * @return A stream of classes
     */
    default @NonNull Stream<Class<?>> scan(@NonNull Class<? extends Annotation> annotation, @NonNull Collection<String> packages) {
        Objects.requireNonNull(annotation, "Annotation type cannot be null");
        Objects.requireNonNull(packages, "Packages to scan cannot be null");
        return scan(annotation.getName(), packages.parallelStream());
    }

    /**
     * Scans the given package names.
     *
     * @param annotation The annotation name to scan
     * @param packages   The package names
     * @return A stream of classes
     */
    default @NonNull Stream<Class<?>> scan(@NonNull String annotation, @NonNull Stream<String> packages) {
        Objects.requireNonNull(annotation, "Annotation type cannot be null");
        Objects.requireNonNull(packages, "Packages to scan cannot be null");

        return packages
            .parallel()
            .flatMap(pkg -> scan(annotation, pkg));
    }

    /**
     * Scans the given package names.
     *
     * @param annotation The annotation to scan
     * @param packages   The package names
     * @return A stream of classes
     */
    default @NonNull Stream<Class<?>> scan(@NonNull Class<? extends Annotation> annotation, @NonNull String... packages) {
        Objects.requireNonNull(annotation, "Annotation type cannot be null");
        Objects.requireNonNull(packages, "Packages to scan cannot be null");

        return scan(annotation.getName(), packages);
    }
}
