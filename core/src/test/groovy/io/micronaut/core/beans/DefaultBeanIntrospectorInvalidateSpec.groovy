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
package io.micronaut.core.beans

import spock.lang.Specification

class DefaultBeanIntrospectorInvalidateSpec extends Specification {

    void "the index of another loader is kept per loader until that loader, or one it delegates to, is invalidated"() {
        given:
        DefaultBeanIntrospector introspector = new DefaultBeanIntrospector()
        URLClassLoader app = new URLClassLoader(new URL[0], getClass().classLoader)
        URLClassLoader child = new URLClassLoader(new URL[0], app)
        URLClassLoader other = new URLClassLoader(new URL[0], getClass().classLoader)

        when: "the index of a loader is looked up twice"
        def first = introspector.getIntrospections(app)

        then: "it is scanned once and then served from the cache, so a later entry of that loader is not seen"
        introspector.getIntrospections(app).is(first)

        when:
        def childIndex = introspector.getIntrospections(child)
        def otherIndex = introspector.getIntrospections(other)
        introspector.getFallbacks(child)
        introspector.invalidate(app)

        then: "the invalidated loader and the loader delegating to it are scanned again"
        !introspector.getIntrospections(app).is(first)
        !introspector.getIntrospections(child).is(childIndex)
        !introspector.@otherFallbacks.containsKey(child)

        and: "an unrelated loader keeps its index"
        introspector.getIntrospections(other).is(otherIndex)

        cleanup:
        child.close()
        app.close()
        other.close()
    }

    void "invalidating the introspector's own loader forgets its own index"() {
        given:
        DefaultBeanIntrospector introspector = new DefaultBeanIntrospector(getClass().classLoader)
        def own = introspector.getIntrospections(getClass().classLoader)

        expect:
        introspector.getIntrospections(getClass().classLoader).is(own)

        when:
        introspector.invalidate(new URLClassLoader(new URL[0], getClass().classLoader))

        then: "a loader below its own changes nothing it indexed"
        introspector.getIntrospections(getClass().classLoader).is(own)

        when:
        introspector.invalidate(getClass().classLoader)

        then:
        !introspector.getIntrospections(getClass().classLoader).is(own)
        introspector.findIntrospection(DefaultBeanIntrospectorInvalidateSpec).isEmpty()
    }

    void "the shared introspector is reloadable"() {
        expect:
        BeanIntrospector.SHARED instanceof ReloadableBeanIntrospector

        when:
        ReloadableBeanIntrospector.invalidateShared(getClass().classLoader)

        then:
        notThrown(Exception)
    }
}
