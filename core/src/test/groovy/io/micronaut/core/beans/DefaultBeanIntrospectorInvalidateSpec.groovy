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

import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils
import spock.lang.Specification
import spock.lang.TempDir

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class DefaultBeanIntrospectorInvalidateSpec extends Specification {

    private static final String REFERENCE_ENTRY = "META-INF/micronaut/" + BeanIntrospectionReference.name

    @TempDir
    Path root

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

    void "invalidating the introspector's own loader scans it again, with the service entries cached for the process"() {
        given: "an introspector of a loader that lists no introspection of the bean yet"
        URLClassLoader own = new URLClassLoader([root.toUri().toURL()] as URL[], getClass().classLoader)
        DefaultBeanIntrospector introspector = new DefaultBeanIntrospector(own)

        expect: "the index of its own loader is read directly: a lookup by bean type would also index, and so scan, the bean's loader"
        !introspector.getIntrospections(own).containsKey(ReloadedBean.name)

        when: "the loader starts listing one, as a recompiled application does"
        listReference()

        then: "the cached index still answers"
        !introspector.getIntrospections(own).containsKey(ReloadedBean.name)

        when: "its own loader is invalidated, but not the service entries cached for the process"
        introspector.invalidate(own)

        then: "the scan reads the cached entries, which do not list it: invalidating the introspector clears no other cache"
        !introspector.getIntrospections(own).containsKey(ReloadedBean.name)

        when: "the service entries are cleared and a loader below the introspector's own changes"
        MicronautMetaServiceLoaderUtils.invalidate()
        introspector.invalidate(new URLClassLoader(new URL[0], own))

        then: "nothing the introspector indexed changes"
        !introspector.getIntrospections(own).containsKey(ReloadedBean.name)

        when: "its own loader is invalidated after the service entries were cleared, as a launcher does"
        introspector.invalidate(own)

        then: "the introspection is found"
        introspector.findIntrospection(ReloadedBean).get().beanType == ReloadedBean

        cleanup:
        MicronautMetaServiceLoaderUtils.invalidate()
        own.close()
    }

    void "the shared introspector forgets only what it indexed for the invalidated loader"() {
        given: "the shared introspector, and two throwaway loaders listing an introspection"
        listReference()
        DefaultBeanIntrospector shared = (DefaultBeanIntrospector) BeanIntrospector.SHARED
        URLClassLoader changed = new URLClassLoader([root.toUri().toURL()] as URL[], getClass().classLoader)
        URLClassLoader unrelated = new URLClassLoader([root.toUri().toURL()] as URL[], getClass().classLoader)
        def ownIndex = shared.getIntrospections(shared.@classLoader)
        def changedIndex = shared.getIntrospections(changed)
        def unrelatedIndex = shared.getIntrospections(unrelated)

        expect:
        changedIndex.containsKey(ReloadedBean.name)
        shared.@otherIntrospections.containsKey(changed)

        when:
        ReloadableBeanIntrospector.invalidateShared(changed)

        then: "the invalidated loader is scanned again"
        !shared.@otherIntrospections.containsKey(changed)
        !shared.getIntrospections(changed).is(changedIndex)

        and: "the other loader and the shared introspector's own index are kept"
        shared.getIntrospections(unrelated).is(unrelatedIndex)
        shared.getIntrospections(shared.@classLoader).is(ownIndex)

        cleanup:
        ReloadableBeanIntrospector.invalidateShared(changed)
        ReloadableBeanIntrospector.invalidateShared(unrelated)
        MicronautMetaServiceLoaderUtils.invalidate()
        changed.close()
        unrelated.close()
    }

    private void listReference() {
        Path entries = Files.createDirectories(root.resolve(REFERENCE_ENTRY))
        Files.writeString(entries.resolve(ReloadedReference.name), "")
    }

    static class ReloadedBean {
    }

    /**
     * The introspection reference a loader lists through its {@code META-INF/micronaut} entries.
     */
    static class ReloadedReference implements BeanIntrospectionReference<ReloadedBean> {

        @Override
        boolean isPresent() {
            return true
        }

        @Override
        Class<ReloadedBean> getBeanType() {
            return ReloadedBean
        }

        @Override
        BeanIntrospection<ReloadedBean> load() {
            return (BeanIntrospection<ReloadedBean>) Proxy.newProxyInstance(
                    ReloadedReference.classLoader,
                    [BeanIntrospection] as Class[],
                    { proxy, method, args -> method.name == 'getBeanType' ? ReloadedBean : null } as InvocationHandler)
        }

        @Override
        String getName() {
            return ReloadedBean.name
        }
    }
}
