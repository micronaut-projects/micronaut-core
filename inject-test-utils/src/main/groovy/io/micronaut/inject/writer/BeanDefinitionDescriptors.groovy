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
package io.micronaut.inject.writer

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import io.micronaut.context.AbstractInitializableBeanDefinitionAndReference
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.annotation.AnnotationMetadataSupport

import java.lang.reflect.Field

import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_CONFIGURATION_PROPERTIES
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_CONTAINER_TYPE
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_CONTEXT_SCOPE
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_PARALLEL
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_POST_LOAD_CONDITIONS
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_PRIMARY
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_PROXIED_BEAN
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_PROXY_TARGET
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_REQUIRES_METHOD_PROCESSING
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_SINGLETON
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.MEMBERSHIP_ANNOTATION
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.MEMBERSHIP_DECLARED_ANNOTATION
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.MEMBERSHIP_DECLARED_STEREOTYPE
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.MEMBERSHIP_STEREOTYPE

/**
 * Reads the descriptors that the {@code META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference} entries
 * carry and compares them with the references they describe: a descriptor is only of use if it answers what the
 * loaded class answers.
 *
 * <p>Shared by the specs of every processor, which must all write descriptors that agree with their classes.</p>
 */
@CompileStatic
class BeanDefinitionDescriptors {

    /**
     * The directory of the entries.
     */
    private static final String ENTRIES = "META-INF/micronaut/" + BeanDefinitionReference.name + "/"

    private static final Map<String, Integer> FLAGS = [
        CONTEXT_SCOPE             : FLAG_CONTEXT_SCOPE,
        PARALLEL                  : FLAG_PARALLEL,
        PROXIED_BEAN              : FLAG_PROXIED_BEAN,
        PROXY_TARGET              : FLAG_PROXY_TARGET,
        SINGLETON                 : FLAG_SINGLETON,
        PRIMARY                   : FLAG_PRIMARY,
        CONFIGURATION_PROPERTIES  : FLAG_CONFIGURATION_PROPERTIES,
        CONTAINER_TYPE            : FLAG_CONTAINER_TYPE,
        REQUIRES_METHOD_PROCESSING: FLAG_REQUIRES_METHOD_PROCESSING,
        POST_LOAD_CONDITIONS      : FLAG_POST_LOAD_CONDITIONS
    ]

    /**
     * The result of comparing the entries a class loader finds.
     */
    static class Comparison {
        /**
         * The definitions whose descriptor was compared with the loaded reference.
         */
        List<String> compared = []
        /**
         * The definitions whose entry is empty.
         */
        List<String> withoutDescriptor = []
        /**
         * The definitions whose class does not load or initialise on this class path.
         */
        List<String> notLoaded = []
        /**
         * The differences found, by the name of the definition.
         */
        Map<String, List<String>> differences = [:]

        @Override
        String toString() {
            "compared ${compared.size()}, without a descriptor ${withoutDescriptor.size()}, not loaded ${notLoaded.size()}, different ${differences.size()}" +
                differences.collect { name, found -> "\n$name\n  " + found.join("\n  ") }.join("")
        }
    }

    /**
     * Compares every entry a class loader finds with the reference it names.
     *
     * @param classLoader The class loader
     * @return The comparison
     */
    static Comparison compareAll(ClassLoader classLoader) {
        def comparison = new Comparison()
        for (String name : MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, BeanDefinitionReference.name)) {
            byte[] content = read(classLoader, name)
            if (content == null || content.length == 0) {
                comparison.withoutDescriptor << name
                continue
            }
            BeanDefinitionDescriptor descriptor = BeanDefinitionDescriptor.read(content)
            if (descriptor == null) {
                comparison.differences[name] = ["the entry has content that is not a descriptor"]
                continue
            }
            BeanDefinitionReference<?> reference = load(classLoader, name)
            if (reference == null) {
                comparison.notLoaded << name
                continue
            }
            comparison.compared << name
            List<String> differences = differences(descriptor, reference)
            if (differences) {
                comparison.differences[name] = differences
            }
        }
        return comparison
    }

    /**
     * The content of the entry of a definition.
     *
     * @param classLoader The class loader
     * @param name The name of the definition
     * @return The content, or {@code null} if there is no entry
     */
    @PackageScope
    static byte[] read(ClassLoader classLoader, String name) {
        URL entry = classLoader.getResource(ENTRIES + name)
        return entry == null ? null : entry.bytes
    }

    /**
     * Loads the reference an entry names.
     *
     * @param classLoader The class loader
     * @param name The name of the definition
     * @return The reference, or {@code null} if its class does not load or did not initialise, in which case
     * it answers for nothing
     */
    @PackageScope
    static BeanDefinitionReference<?> load(ClassLoader classLoader, String name) {
        try {
            def reference = (BeanDefinitionReference<?>) classLoader.loadClass(name).getDeclaredConstructor().newInstance()
            return field(reference, "failedInitialization") == null ? reference : null
        } catch (LinkageError | ReflectiveOperationException | TypeNotPresentException ignored) {
            return null
        }
    }

    /**
     * What a descriptor answers differently from the loaded reference.
     *
     * @param descriptor The descriptor
     * @param reference The reference it describes
     * @return The differences, empty if they agree
     */
    private static List<String> differences(BeanDefinitionDescriptor descriptor, BeanDefinitionReference<?> reference) {
        List<String> differences = []
        Closure<Void> check = { String what, Object described, Object answered ->
            if (described != answered) {
                differences.add("$what: the descriptor says $described, the reference $answered".toString())
            }
            return null
        }

        check("flags", flagNames(descriptor.flags()), flagNames(flags(reference)))
        check("beanType", descriptor.beanType(), typeName(reference.getBeanType()))
        check("exposedTypes", descriptor.exposedTypes(), reference.getExposedTypes().collect { Class<?> type -> typeName(type) }.sort())
        check("indexes", descriptor.indexes(), reference.getIndexes().collect { Class<?> type -> typeName(type) })

        AnnotationMetadata metadata = reference.getAnnotationMetadata()
        Set<String> names = new TreeSet<>(descriptor.annotations().keySet())
        names.addAll(metadata.getAnnotationNames())
        names.addAll(metadata.getDeclaredAnnotationNames())
        names.addAll(metadata.getStereotypeAnnotationNames())
        names.addAll(metadata.getDeclaredStereotypeAnnotationNames())
        for (String name : names) {
            int answered = (metadata.hasDeclaredAnnotation(name) ? MEMBERSHIP_DECLARED_ANNOTATION : 0) |
                (metadata.hasAnnotation(name) ? MEMBERSHIP_ANNOTATION : 0) |
                (metadata.hasDeclaredStereotype(name) ? MEMBERSHIP_DECLARED_STEREOTYPE : 0) |
                (metadata.hasStereotype(name) ? MEMBERSHIP_STEREOTYPE : 0)
            check("annotation $name", descriptor.annotations().getOrDefault(name, 0), answered)
        }
        for (Map.Entry<String, String> repeatable : descriptor.repeatableContainers().entrySet()) {
            check("container of ${repeatable.key}", repeatable.value, AnnotationMetadataSupport.getRepeatableAnnotation(repeatable.key))
        }
        // and the other way. The registry is shared by every class, so it does not tell what this one registered:
        // what it has to have registered is the container of each annotation its metadata holds repeated
        for (String name : names) {
            AnnotationValue<?> container = metadata.getAnnotation(name)
            List<AnnotationValue<?>> repeated = container == null ? [] : (List<AnnotationValue<?>>) container.getAnnotations(AnnotationMetadata.VALUE_MEMBER)
            for (String repeatable : repeated*.annotationName.toSet()) {
                if (AnnotationMetadataSupport.getRepeatableAnnotation(repeatable) == name && !AnnotationMetadataSupport.getCoreRepeatableAnnotationsContainers().containsKey(repeatable)) {
                    check("container of $repeatable", descriptor.repeatableContainers().get(repeatable), name)
                }
            }
        }

        // as QualifiedBeanType#getDeclaredQualifier reads them
        AnnotationMetadata declared = (AnnotationMetadata) reference.getTargetAnnotationMetadata()
        if (declared instanceof AnnotationMetadataHierarchy) {
            declared = ((AnnotationMetadataHierarchy) declared).getDeclaredMetadata()
        }
        check("qualifiers", descriptor.qualifiers().collect { shape(it) }, AnnotationUtil.findQualifierAnnotations(declared).collect { shape(it) })
        check("nonBindingMembers", descriptor.nonBindingMembers(), AnnotationUtil.resolveNonBindingMembers(declared).toList())

        if (reference instanceof AbstractInitializableBeanDefinitionAndReference) {
            Object[] preLoadConditions = (Object[]) field(reference, "preLoadConditions")
            check("preLoadConditions", descriptor.preLoadConditions(), preLoadConditions == null ? [] : preLoadConditions.toList())
        }
        return differences
    }

    /**
     * The flags a reference answers.
     *
     * @param reference The reference
     * @return The combination of the flags of a descriptor
     */
    private static int flags(BeanDefinitionReference<?> reference) {
        Object[] postLoadConditions = (Object[]) field(reference, "postLoadConditions")
        return (reference.isContextScope() ? FLAG_CONTEXT_SCOPE : 0) |
            (reference.isParallel() ? FLAG_PARALLEL : 0) |
            (reference.isProxiedBean() ? FLAG_PROXIED_BEAN : 0) |
            (reference.isProxyTarget() ? FLAG_PROXY_TARGET : 0) |
            (reference.isSingleton() ? FLAG_SINGLETON : 0) |
            (reference.isPrimary() ? FLAG_PRIMARY : 0) |
            (reference.isConfigurationProperties() ? FLAG_CONFIGURATION_PROPERTIES : 0) |
            (reference.isContainerType() ? FLAG_CONTAINER_TYPE : 0) |
            (reference.requiresMethodProcessing() ? FLAG_REQUIRES_METHOD_PROCESSING : 0) |
            (postLoadConditions != null && postLoadConditions.length > 0 ? FLAG_POST_LOAD_CONDITIONS : 0)
    }

    /**
     * The names of the flags of a descriptor, for a readable difference.
     *
     * @param flags The flags
     * @return Their names
     */
    private static List<String> flagNames(int flags) {
        FLAGS.findAll { (flags & it.value) != 0 }.keySet().toList()
    }

    /**
     * The name a descriptor gives a type: the name of the class, with {@code []} for each dimension of an array.
     *
     * @param type The type
     * @return The name
     */
    private static String typeName(Class<?> type) {
        type.isArray() ? typeName(type.componentType) + "[]" : type.name
    }

    /**
     * A value of an annotation member with the type it has, so that two values are only the same if a qualifier
     * comparing them would find them the same: an array of strings is not an array of objects.
     *
     * @param value The value
     * @return The value in a shape that compares by content
     */
    private static Object shape(Object value) {
        if (value instanceof AnnotationValue) {
            Map<String, Object> values = [:]
            for (Map.Entry<CharSequence, Object> member : ((AnnotationValue<?>) value).getValues().entrySet()) {
                values.put(member.key.toString(), shape(member.value))
            }
            return [annotation: ((AnnotationValue<?>) value).getAnnotationName(), values: values]
        }
        if (value instanceof AnnotationClassValue) {
            return [type: ((AnnotationClassValue<?>) value).getName(), instantiated: ((AnnotationClassValue<?>) value).isInstantiated()]
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> elements = []
            for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) {
                elements.add(shape(java.lang.reflect.Array.get(value, i)))
            }
            return [array: value.getClass().componentType.name, elements: elements]
        }
        return [type: value == null ? null : value.getClass().name, value: value]
    }

    private static Object field(BeanDefinitionReference<?> reference, String name) {
        if (!(reference instanceof AbstractInitializableBeanDefinitionAndReference)) {
            return null
        }
        Field field = AbstractInitializableBeanDefinitionAndReference.getDeclaredField(name)
        field.accessible = true
        return field.get(reference)
    }
}
