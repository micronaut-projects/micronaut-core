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
package io.micronaut.context

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.context.watch.Buzz
import io.micronaut.inject.context.watch.MixedTocker
import io.micronaut.inject.context.watch.Tock
import io.micronaut.inject.context.watch.TockStereotype
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BeanWatchRegistryProcessOnStartupSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['spec.name': 'BeanWatchRegistryProcessOnStartupSpec'])

    void "the compiled metadata of a method proves an annotation is processed at startup only when it is the method's one executable annotation"() {
        expect:
        BeanWatchRegistry.provesProcessedOnStartup(metadata(method), annotationType) == proves

        where:
        method       | annotationType  | proves
        'only'       | Tock            | true
        // processed at startup through a stereotype: the metadata records Tock as the annotation carrying @Executable,
        // and Tock is not present on the method, so neither annotation is proven and a watch scans the definitions
        'stereotype' | TockStereotype  | false
        'stereotype' | Tock            | false
        // the processOnStartup of the method may come from either annotation
        'both'       | Tock            | false
        'both'       | Buzz            | false
        'plain'      | Buzz            | false
    }

    void "the methods processed at startup decide whether the index of processed definitions holds every method of an annotation"() {
        given:
        List<BeanDefinition<?>> processed = [context.getBeanDefinition(MixedTocker)]

        expect:
        BeanWatchRegistry.isProcessedOnStartup(Tock, processed)
        !BeanWatchRegistry.isProcessedOnStartup(TockStereotype, processed)
        !BeanWatchRegistry.isProcessedOnStartup(Buzz, processed)
        !BeanWatchRegistry.isProcessedOnStartup(Tock, [])
    }

    private AnnotationMetadata metadata(String name) {
        context.getBeanDefinition(MixedTocker).executableMethods.find { it.methodName == name }.annotationMetadata
    }
}
