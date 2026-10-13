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
package io.micronaut.python.imports;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;

import java.util.List;

/**
 * Contributes curated Python modules that gather the names of one or more Java packages behind a single
 * import, such as a {@code pyronaut.http} module exporting the HTTP annotations, the HTTP types and the
 * static factories of {@code HttpResponse}.
 *
 * <p>A curated module exists only in the Python namespace: the Python processor resolves every name imported
 * from it to the original Java type, so the annotation metadata, the generated classes and the run time see
 * exactly the types a direct import of the Java package would give.</p>
 *
 * <p>Implementations are loaded with the {@link java.util.ServiceLoader} mechanism
 * ({@code META-INF/services/io.micronaut.python.imports.PythonImportMapper}) from the class path of the
 * Python processor and of the tools that generate editor stubs. Several mappers may contribute to one module:
 * their contributions merge in {@link #getOrder() order}, the contribution of the highest precedence first,
 * unless a contribution {@link PythonModuleMapping#replaces() replaces} those after it.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public interface PythonImportMapper extends Ordered {

    /**
     * The modules this mapper contributes.
     *
     * @return The module mappings
     */
    List<PythonModuleMapping> getMappings();
}
