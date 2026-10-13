package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationClassValue
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

    void "a caller asking with no loader of its own is served the registered type"() {
        expect: "the bootstrap loader defines no copy of an application annotation, so resolving again would only\
 answer from whatever loader the fallback picks"
        AnnotationMetadataSupport.getAnnotationType(Portable.name, null).get().is(Portable)
    }

    void "a caller naming no loader is served for the thread context loader"() {
        given: "a child-first deployment loader under the application loader, defining its own copy of an annotation the application loader also sees"
        def deployment = new OwnCopy(Deployed.classLoader)
        def own = deployment.define(Deployed.name)

        and: "the generated metadata of that deployment registering its copy"
        AnnotationMetadataSupport.registerAnnotationType(new AnnotationClassValue<>(own))

        and: "metadata of that deployment carrying the annotation"
        def metadata = new MutableAnnotationMetadata()
        metadata.addAnnotation(Deployed.name, [:])

        and: "another deployment defining a copy of its own, which the registry does not hold"
        def other = new OwnCopy(Deployed.classLoader)
        def otherOwn = other.define(Deployed.name)

        expect: "the deployment that registered its copy is served that copy, not the one the loader of the support class resolves again"
        !own.is(Deployed)
        withContext(deployment) { AnnotationMetadataSupport.getAnnotationType(Deployed.name).get() }.is(own)
        withContext(deployment) { metadata.getAnnotationType(Deployed.name).get() }.is(own)

        and: "so is a thread of the application the deployment runs in, whose context loader is a parent of the deployment"
        withContext(Deployed.classLoader) { AnnotationMetadataSupport.getAnnotationType(Deployed.name).get() }.is(own)

        and: "so is a thread with no context loader"
        withContext(null) { AnnotationMetadataSupport.getAnnotationType(Deployed.name).get() }.is(own)

        and: "another deployment is served its own copy"
        withContext(other) { AnnotationMetadataSupport.getAnnotationType(Deployed.name).get() }.is(otherOwn)
        withContext(other) { metadata.getAnnotationType(Deployed.name).get() }.is(otherOwn)
    }

    void "a name nothing registered is loaded through the thread context loader first"() {
        given: "a deployment loader defining its own copy of an annotation nothing registered"
        def deployment = new OwnCopy()
        def own = deployment.define(Unregistered.name)

        expect:
        !own.is(Unregistered)
        withContext(deployment) { AnnotationMetadataSupport.getAnnotationType(Unregistered.name).get() }.is(own)
    }

    private static <T> T withContext(ClassLoader loader, Closure<T> action) {
        def thread = Thread.currentThread()
        def previous = thread.contextClassLoader
        thread.contextClassLoader = loader
        try {
            return action.call()
        } finally {
            thread.contextClassLoader = previous
        }
    }

    void "a registration by a live sibling loader does not replace the registered type"() {
        given: "two live loaders, sibling deployments, each defining a copy of an annotation"
        def first = new OwnCopy(Reloaded.classLoader)
        def second = new OwnCopy(Reloaded.classLoader)
        def firstCopy = first.define(Reloaded.name)
        def secondCopy = second.define(Reloaded.name)

        expect:
        !AnnotationMetadataSupport.getRegisteredAnnotationType(Reloaded.name).isPresent()

        when: "both register their copy, in turn and again"
        AnnotationMetadataSupport.registerAnnotationType(new AnnotationClassValue<>(firstCopy))
        AnnotationMetadataSupport.registerAnnotationType(new AnnotationClassValue<>(secondCopy))
        AnnotationMetadataSupport.registerAnnotationType(new AnnotationClassValue<>(firstCopy))
        AnnotationMetadataSupport.registerAnnotationType(new AnnotationClassValue<>(secondCopy))

        then: "the first registration is kept rather than flipping between the two"
        AnnotationMetadataSupport.getRegisteredAnnotationType(Reloaded.name).get().is(firstCopy)

        cleanup:
        AnnotationMetadataSupport.retireClassLoader(first)
        AnnotationMetadataSupport.retireClassLoader(second)
    }

    void "a retired loader's types are forgotten, and the generation replacing it registers its own"() {
        given: "two generations of an application loader, each defining a copy of an annotation"
        def first = new OwnCopy(Reloaded.classLoader)
        def second = new OwnCopy(Reloaded.classLoader)
        def firstCopy = first.define(Reloaded.name)
        def secondCopy = second.define(Reloaded.name)

        and: "the first generation registered its copy and made a proxy of it"
        AnnotationMetadataSupport.registerAnnotationType(new AnnotationClassValue<>(firstCopy))
        AnnotationMetadataSupport.getProxyFactory(firstCopy)

        expect:
        AnnotationMetadataSupport.getRegisteredAnnotationType(Reloaded.name).get().is(firstCopy)
        AnnotationMetadataSupport.ANNOTATION_PROXY_CACHE.containsKey(firstCopy)

        when: "the launcher retires the first generation"
        AnnotationMetadataSupport.retireClassLoader(first)

        then: "nothing keeps its class"
        !AnnotationMetadataSupport.getRegisteredAnnotationType(Reloaded.name).isPresent()
        !AnnotationMetadataSupport.ANNOTATION_PROXY_CACHE.containsKey(firstCopy)

        when: "a straggling thread of the retired generation registers its copy, then the next generation its own"
        AnnotationMetadataSupport.registerAnnotationType(new AnnotationClassValue<>(firstCopy))
        AnnotationMetadataSupport.registerAnnotationType(new AnnotationClassValue<>(secondCopy))

        then: "the registry holds the next generation's copy"
        AnnotationMetadataSupport.getRegisteredAnnotationType(Reloaded.name).get().is(secondCopy)

        when: "the straggling thread makes a proxy of its copy, and resolves a type nothing registered"
        def unregistered = first.define(Straggling.name)
        def factory = AnnotationMetadataSupport.getProxyFactory(firstCopy)
        def resolved = AnnotationMetadataSupport.getAnnotationType(Straggling.name, first)

        then: "it is served, but neither is kept"
        factory != null
        resolved.get().is(unregistered)
        !AnnotationMetadataSupport.ANNOTATION_PROXY_CACHE.containsKey(firstCopy)
        !AnnotationMetadataSupport.getRegisteredAnnotationType(Straggling.name).isPresent()

        cleanup:
        AnnotationMetadataSupport.retireClassLoader(second)
    }

    void "the type a retired loader registered before it was retired is replaced by the next generation's"() {
        given: "a generation that registers its copy after the launcher retired it, as a straggling thread may"
        def first = new OwnCopy(Reloaded.classLoader)
        def second = new OwnCopy(Reloaded.classLoader)
        def firstCopy = first.define(Reloaded.name)
        def secondCopy = second.define(Reloaded.name)
        AnnotationMetadataSupport.registerAnnotationType(new AnnotationClassValue<>(firstCopy))
        AnnotationMetadataSupport.RETIRED_LOADERS.put(first, Boolean.TRUE)
        AnnotationMetadataSupport.loadersRetired = true

        expect: "its entry is still there"
        AnnotationMetadataSupport.getRegisteredAnnotationType(Reloaded.name).get().is(firstCopy)

        when:
        AnnotationMetadataSupport.registerAnnotationType(new AnnotationClassValue<>(secondCopy))

        then:
        AnnotationMetadataSupport.getRegisteredAnnotationType(Reloaded.name).get().is(secondCopy)

        cleanup:
        AnnotationMetadataSupport.retireClassLoader(first)
        AnnotationMetadataSupport.retireClassLoader(second)
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

        OwnCopy(ClassLoader parent = ClassLoader.platformClassLoader) {
            super(parent)
        }

        Class<?> define(String name) {
            def bytes = AnnotationTypeClassLoaderSpec.classLoader
                    .getResourceAsStream(name.replace('.' as char, '/' as char) + ".class").bytes
            defineClass(name, bytes, 0, bytes.length)
        }
    }
}
