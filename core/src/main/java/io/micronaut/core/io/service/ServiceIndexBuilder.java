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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
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
     * @return The index
     * @throws IOException If the class path cannot be read
     */
    public static ServiceIndex build(ClassLoader classLoader, Collection<String> serviceTypes) throws IOException {
        Map<String, Set<String>> micronautServices = MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(classLoader, true);
        Map<String, List<String>> standardServices = new LinkedHashMap<>();
        for (String serviceType : serviceTypes) {
            List<String> names = new ArrayList<>();
            Enumeration<URL> serviceConfigs = classLoader.getResources(SoftServiceLoader.META_INF_SERVICES + '/' + serviceType);
            while (serviceConfigs.hasMoreElements()) {
                names.addAll(ServiceScanner.readStandardServiceNames(serviceConfigs.nextElement()));
            }
            standardServices.put(serviceType, names);
        }
        return new ServiceIndex(classLoader, micronautServices, standardServices);
    }
}
