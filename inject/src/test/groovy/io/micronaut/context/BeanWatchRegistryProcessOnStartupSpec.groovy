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

import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Mapper
import spock.lang.Specification

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

class BeanWatchRegistryProcessOnStartupSpec extends Specification {

    void "an annotation is processed at startup when it or a stereotype of it is annotated @Executable(processOnStartup = true)"() {
        expect:
        BeanWatchRegistry.isProcessedOnStartup(annotationType) == processed

        where:
        annotationType     | processed
        Mapper             | true
        StartupProcessed   | true
        StartupStereotype  | true
        ExecutableOnly     | false
        PlainAnnotation    | false
        Executable         | false
    }

    @Executable(processOnStartup = true)
    @Retention(RetentionPolicy.RUNTIME)
    @Target([ElementType.METHOD, ElementType.ANNOTATION_TYPE])
    static @interface StartupProcessed {
    }

    @StartupProcessed
    @Retention(RetentionPolicy.RUNTIME)
    @Target([ElementType.METHOD, ElementType.ANNOTATION_TYPE])
    static @interface StartupStereotype {
    }

    @Executable
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    static @interface ExecutableOnly {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    static @interface PlainAnnotation {
    }
}
