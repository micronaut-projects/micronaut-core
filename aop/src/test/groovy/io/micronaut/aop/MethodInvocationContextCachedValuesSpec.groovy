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
package io.micronaut.aop

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import spock.lang.Specification

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

class MethodInvocationContextCachedValuesSpec extends Specification {

    void "an invocation that sees the metadata of its method asks the method for the values it keeps"() {
        given:
        def metadata = metadata()
        def kept = new Object()
        ExecutableMethod method = [
            getAnnotationMetadata   : { metadata },
            getCachedValue          : { Class key, java.util.function.Function factory -> kept },
            getCachedAnnotationValue: { Class type -> AnnotationValue.builder(Marked).value("kept").build() },
            getCachedAnnotation     : { Class type -> null }
        ] as ExecutableMethod
        MethodInvocationContext context = invocation(method, metadata)

        expect:
        context.getCachedValue(Object, m -> new Object()).is(kept)
        context.getCachedAnnotationValue(Marked).stringValue().get() == "kept"
        context.getCachedAnnotation(Marked) == null
    }

    void "an invocation with metadata of its own computes the values"() {
        given:
        def methodMetadata = metadata()
        def invocationMetadata = metadata()
        ExecutableMethod method = [
            getAnnotationMetadata: { methodMetadata },
            getCachedValue       : { Class key, java.util.function.Function factory -> throw new AssertionError("asked the method") }
        ] as ExecutableMethod
        MethodInvocationContext context = invocation(method, invocationMetadata)

        expect:
        context.getCachedValue(String, m -> m.is(context) ? "invocation" : "other") == "invocation"
        context.getCachedAnnotationValue(Marked).stringValue().get() == "marked"

        when:
        context.getCachedAnnotation(Marked)

        then:
        // computed for the invocation: no builder is registered for the annotation
        thrown(IllegalStateException)
    }

    private static AnnotationMetadata metadata() {
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(Marked.name, [value: "marked"])
        return metadata
    }

    private static MethodInvocationContext invocation(ExecutableMethod method, AnnotationMetadata metadata) {
        return [
            getExecutableMethod  : { method },
            getAnnotationMetadata: { metadata }
        ] as MethodInvocationContext
    }

    @Retention(RetentionPolicy.RUNTIME)
    static @interface Marked {
        String value()
    }
}
