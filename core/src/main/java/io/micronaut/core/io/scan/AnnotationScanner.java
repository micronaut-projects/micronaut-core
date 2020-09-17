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
import java.util.stream.Stream;

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
    Stream<Class> scan(String annotation, String pkg);

    /**
     * Scan the given packages.
     *
     * @param annotation The annotation to scan for
     * @param packages   The packages to scan
     * @return A stream of classes
     */
    default Stream<Class> scan(String annotation, Package... packages) {
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
    default Stream<Class> scan(Class<? extends Annotation> annotation, Package... packages) {
        return scan(annotation.getName(), packages);
    }

    /**
     * Scan the given packages.
     *
     * @param annotation The annotation to scan for
     * @param pkg        The package to scan
     * @return A stream of classes
     */
    default Stream<Class> scan(Class<? extends Annotation> annotation, Package pkg) {
        return scan(annotation.getName(), pkg.getName());
    }

    /**
     * Scans the given package names.
     *
     * @param annotation The annotation name to scan
     * @param packages   The package names
     * @return A stream of classes
     */
    default Stream<Class> scan(String annotation, String... packages) {
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
    default Stream<Class> scan(String annotation, Collection<String> packages) {
        return scan(annotation, packages.parallelStream());
    }

    /**
     * Scans the given package names.
     *
     * @param annotation The annotation name to scan
     * @param packages   The package names
     * @return A stream of classes
     */
    default Stream<Class> scan(Class<? extends Annotation> annotation, Collection<String> packages) {
        return scan(annotation.getName(), packages.parallelStream());
    }

    /**
     * Scans the given package names.
     *
     * @param annotation The annotation name to scan
     * @param packages   The package names
     * @return A stream of classes
     */
    default Stream<Class> scan(String annotation, Stream<String> packages) {
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
    default Stream<Class> scan(Class<? extends Annotation> annotation, String... packages) {
        return scan(annotation.getName(), packages);
    }
}
