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
package io.micronaut.reflection

import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

class IntrospectorWithoutFallbacksSpec extends Specification {

    void "the view without fallbacks answers only with generated introspections"() {
        given: "the reflective fallback serves Book"
        def registration = ReflectionIntrospectionPolicy.configure(["io.micronaut.reflection.*"])
        def introspector = BeanIntrospector.forClassLoader(getClass().classLoader)
        def generatedOnly = introspector.withoutFallbacks()

        expect: "the full introspector reflects over Book, the view does not"
        introspector.findIntrospection(Book).present
        !introspector.findIntrospection(Book).get().generated
        generatedOnly.findIntrospection(Book).empty

        and: "a generated introspection is served by both and says it was generated"
        introspector.findIntrospection(EveryKindBean).get().generated
        generatedOnly.findIntrospection(EveryKindBean).get().generated

        and: "enumeration is the same, the view is stable and is its own view"
        generatedOnly.findIntrospectedTypes { true } == introspector.findIntrospectedTypes { true }
        introspector.withoutFallbacks().is(generatedOnly)
        generatedOnly.withoutFallbacks().is(generatedOnly)

        and: "the reflective introspector drops to its delegate's generated view"
        new ReflectionBeanIntrospector(introspector).withoutFallbacks().is(generatedOnly)

        cleanup:
        registration.close()
    }
}
