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
package io.micronaut.core.io.service;

import io.micronaut.core.annotation.Internal;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link ServiceIndex} by scanning a class path the way service loading does at run time.
 *
 * <p>The {@code META-INF/micronaut} entries are those that
 * {@link MicronautMetaServiceLoaderUtils#findAllMicronautMetaServices(ClassLoader)} finds, except that the entries of
 * a directory are listed in the order of their names, so the index does not depend on the file system it is built
 * on. The {@code META-INF/services} names of each requested type are read with the parser of the scan, from each file
 * in the order of the class path. A name listed by two files is kept twice, as the scan loads it twice.</p>
 *
 * <p>The index also lists the class path of the class loader, with the size of each file, when that class path is
 * known: see {@link ServiceIndex}. It is listed by the rule the first lookup applies at run time, so a class loader
 * of a single JAR is listed together with the entries that the {@code Class-Path} attribute of its manifest names,
 * as that JAR is when it is started with {@code java -jar}, and a class loader of several JARs without what their
 * manifests name. The list made for a class loader of a thin JAR alone, or of that JAR and the libraries its manifest
 * names, therefore matches the class path of {@code java -jar}. The class path of the parents of the class loader
 * is not listed, although their services are indexed, so a change to it is not detected at run time.</p>
 *
 * <p>The list is only right for the files as they are when the index is built. A producer has to correct it for
 * what it does afterwards: add an entry for a file it adds to the class path, such as the JAR that holds the
 * generated index, give no size for a file it has yet to write or that it changes, and give every file the name it
 * has when the application runs, which a JAR that is renamed when it is deployed does not keep. When it cannot know
 * those names, it has to register the index without a class path.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
@Internal
public final class ServiceIndexBuilder {

    private ServiceIndexBuilder() {
    }

    /**
     * Builds the index of a class path.
     *
     * @param classLoader  The class loader of the class path, which the index is built for
     * @param serviceTypes The service types whose {@code META-INF/services} files are indexed. A type without such a
     *                     file is indexed with no names. The {@code META-INF/micronaut} entries of every type are
     *                     always indexed
     * @return The index, which holds its own copies of the names
     * @throws IOException If the class path cannot be read
     */
    public static ServiceIndex build(ClassLoader classLoader, Collection<String> serviceTypes) throws IOException {
        Map<String, Set<String>> micronautServices = MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(classLoader, true);
        Map<String, List<String>> standardServices = new LinkedHashMap<>();
        for (String serviceType : serviceTypes) {
            standardServices.put(serviceType, ServiceScanner.readStandardServiceNames(classLoader, serviceType));
        }
        return ServiceIndex.copyOf(classLoader, micronautServices, standardServices, ServiceIndex.classPathOf(classLoader));
    }
}
