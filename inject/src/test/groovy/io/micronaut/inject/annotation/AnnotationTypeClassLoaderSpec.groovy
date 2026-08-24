package io.micronaut.inject.annotation

import io.micronaut.inject.reflection.Portable
import spock.lang.Specification

class AnnotationTypeClassLoaderSpec extends Specification {

    void "a registered annotation type is resolved again through the class loader of the caller"() {
        given: "a loader defining its own copy of the annotation, as a child-first deployment loader does"
        def child = new OwnCopy()
        def own = child.define(Portable.name)

        and: "the type registered by an earlier resolution, from the application loader"
        AnnotationMetadataSupport.getAnnotationType(Portable.name, Portable.classLoader).get().is(Portable)

        when: "the same name is resolved for the other loader"
        def resolved = AnnotationMetadataSupport.getAnnotationType(Portable.name, child)

        then: "the copy that loader defines is the one returned: it is the one the caller compares with"
        resolved.get().is(own)
        !resolved.get().is(Portable)
        resolved.get().name == Portable.name

        and: "the registered type is still served to a caller of the loader that registered it"
        AnnotationMetadataSupport.getAnnotationType(Portable.name, Portable.classLoader).get().is(Portable)
    }

    void "a loader that has no copy of its own falls back to the registered type"() {
        given: "a loader that defines nothing"
        def empty = new OwnCopy()

        expect:
        AnnotationMetadataSupport.getAnnotationType(Portable.name, empty).get().is(Portable)
    }

    void "an annotation type of the JDK is served whatever the loader asks"() {
        expect: "a type the bootstrap loader defines cannot be shadowed"
        Deprecated.classLoader == null
        AnnotationMetadataSupport.getAnnotationType(Deprecated.name, Deprecated.classLoader).get().is(Deprecated)
        AnnotationMetadataSupport.getAnnotationType(Deprecated.name, new OwnCopy()).get().is(Deprecated)
    }

    /**
     * A loader defining the classes it is asked for itself, rather than delegating to the loader that
     * already defines them.
     */
    static class OwnCopy extends ClassLoader {

        OwnCopy() {
            super(ClassLoader.platformClassLoader)
        }

        Class<?> define(String name) {
            def bytes = AnnotationTypeClassLoaderSpec.classLoader
                    .getResourceAsStream(name.replace('.' as char, '/' as char) + ".class").bytes
            defineClass(name, bytes, 0, bytes.length)
        }
    }
}
