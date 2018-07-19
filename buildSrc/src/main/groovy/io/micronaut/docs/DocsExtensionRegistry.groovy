/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package io.micronaut.docs

import org.asciidoctor.extension.spi.ExtensionRegistry
import org.asciidoctor.extension.JavaExtensionRegistry
import org.asciidoctor.Asciidoctor

class DocsExtensionRegistry implements ExtensionRegistry{
    @Override
    void register(Asciidoctor asciidoctor) {

        final JavaExtensionRegistry javaExtensionRegistry = asciidoctor.javaExtensionRegistry()
        javaExtensionRegistry.inlineMacro 'api', ApiMacro
        javaExtensionRegistry.inlineMacro 'ann', AnnotationMacro
        javaExtensionRegistry.inlineMacro 'pkg', PackageMacro
        javaExtensionRegistry.inlineMacro 'jdk', JdkApiMacro
        javaExtensionRegistry.inlineMacro 'jee', JeeApiMacro
        javaExtensionRegistry.inlineMacro 'rs', ReactiveStreamsApiMacro
        javaExtensionRegistry.inlineMacro 'rx', RxJavaApiMacro


    }
}
