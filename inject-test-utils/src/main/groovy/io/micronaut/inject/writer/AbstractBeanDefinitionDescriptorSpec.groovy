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

import io.micronaut.aop.internal.InterceptorRegistryBean
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.conditions.MatchesAbsenceOfClassesCondition
import io.micronaut.context.conditions.MatchesConfigurationCondition
import io.micronaut.context.conditions.MatchesCurrentNotOsCondition
import io.micronaut.context.conditions.MatchesCurrentOsCondition
import io.micronaut.context.conditions.MatchesEnvironmentCondition
import io.micronaut.context.conditions.MatchesMissingPropertyCondition
import io.micronaut.context.conditions.MatchesNotEnvironmentCondition
import io.micronaut.context.conditions.MatchesPresenceOfClassesCondition
import io.micronaut.context.conditions.MatchesPresenceOfEntitiesCondition
import io.micronaut.context.conditions.MatchesPresenceOfResourcesCondition
import io.micronaut.context.conditions.MatchesPropertyCondition
import io.micronaut.context.conditions.MatchesSdkCondition
import io.micronaut.context.event.ApplicationEventPublisherFactory
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.provider.BeanProviderDefinition
import io.micronaut.inject.provider.JakartaProviderBeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.test.BeanDefinitionDescriptors
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_CONFIGURATION_PROPERTIES
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_CONTAINER_TYPE
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_CONTEXT_SCOPE
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_PARALLEL
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_POST_LOAD_CONDITIONS
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_PRIMARY
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_PROXIED_BEAN
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_REQUIRES_METHOD_PROCESSING
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_SINGLETON
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.MEMBERSHIP_ANNOTATION
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.MEMBERSHIP_DECLARED_ANNOTATION
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.MEMBERSHIP_DECLARED_STEREOTYPE
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.MEMBERSHIP_STEREOTYPE

/**
 * What a processor writes into the {@code META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference} entries
 * of the definitions it generates.
 *
 * <p>The spec of every processor compiles the same beans, written in its language, to a directory as a build does.
 * Each descriptor has to agree with the class it describes. What the processors hold in the same way is expected
 * here for all of them. What they hold differently, in the class and so in the descriptor, is what the specs
 * override.</p>
 */
abstract class AbstractBeanDefinitionDescriptorSpec extends Specification {

    private static final String SERVICE = BeanDefinitionReference.name

    @Shared
    URL[] output

    @Shared
    ClassLoader classLoader

    /**
     * Compiles the beans.
     *
     * @return The directories the classes and the {@code META-INF/micronaut} entries are written to
     */
    protected abstract URL[] compile()

    /**
     * @return The definitions among the classes of the module itself that the comparison has to cover, so that it
     * cannot pass by finding nothing of what this processor compiled
     */
    protected abstract List<String> getModuleDefinitions()

    /**
     * @return The definitions of the compiled beans that the format cannot describe, whose entries are empty
     */
    protected List<String> getUndescribed() {
        // a member of its qualifier is an expression, which is only known once it is evaluated
        return ['test.$Dynamic$Definition']
    }

    /**
     * @return The type of the value of a member that is an array of {@code int}
     */
    protected Class<?> getIntArrayType() {
        return int[]
    }

    /**
     * @return The names the classes {@code int[]} and {@code Object[]} have as values of a member
     */
    protected List<String> getArrayClassNames() {
        return ['[I', '[Ljava.lang.Object;']
    }

    /**
     * @return The types of the values of the members that are given no element: an array of strings, of
     * {@code int} and of annotations
     */
    protected List<Class<?>> getEmptyArrayTypes() {
        return [String[], int[], AnnotationValue[]]
    }

    void setupSpec() {
        output = compile()
        classLoader = new URLClassLoader(output, getClass().classLoader)
    }

    void "the descriptor of every definition on the class path agrees with the loaded reference"() {
        when:
        def comparison = BeanDefinitionDescriptors.compareAll(getClass().classLoader)

        then:
        comparison.differences.isEmpty()
        comparison.compared.containsAll(moduleDefinitions)
    }

    void "the descriptor of every compiled definition agrees with the loaded reference"() {
        when:
        def comparison = BeanDefinitionDescriptors.compareAll(new URLClassLoader(output, getClass().classLoader) {
            @Override
            Enumeration<URL> getResources(String name) {
                // only what was compiled here
                return findResources(name)
            }
        })

        then:
        comparison.differences.isEmpty()
        comparison.notLoaded.isEmpty()
        comparison.withoutDescriptor as Set == undescribed as Set
        comparison.compared.size() == definitions().size() - undescribed.size()
    }

    void "the flags are the answers of the reference"() {
        expect:
        flags('Plain') == [FLAG_SINGLETON]
        flags('Eager') == [FLAG_CONTEXT_SCOPE, FLAG_SINGLETON]
        flags('InParallel') == [FLAG_PARALLEL, FLAG_SINGLETON]
        flags('First') == [FLAG_SINGLETON, FLAG_PRIMARY]
        flags('Each') == []
        flags('Settings') == [FLAG_SINGLETON, FLAG_CONFIGURATION_PROPERTIES]
        flags('Startup') == [FLAG_SINGLETON, FLAG_REQUIRES_METHOD_PROCESSING]
        flags('Conditional') == [FLAG_SINGLETON, FLAG_POST_LOAD_CONDITIONS]
        flags(describedAs('test.Product[]')) == [FLAG_SINGLETON, FLAG_CONTAINER_TYPE]
    }

    void "the bean type, the exposed types and the indexes are described by name"() {
        expect:
        descriptor('Plain').beanType() == 'test.Plain'
        descriptor('Plain').exposedTypes() == ['test.Api', 'test.Plain']
        descriptor('Plain').indexes() == []

        and:
        descriptor('Typed').beanType() == 'test.Typed'
        descriptor('Typed').exposedTypes() == ['test.Api']
        descriptor('Typed').indexes() == ['test.Other']

        and:
        describedAs('test.Product[]').exposedTypes() == ['test.Product', 'test.Product[]']

        and: "a bean of a factory declares no index and is indexed as its factory is"
        descriptor('Products').indexes() == ['test.Other']
        describedAs('test.Product').indexes() == ['test.Other']
    }

    void "the annotations are the ones the annotation metadata of the definition has"() {
        given:
        def descriptor = descriptor('Tagged')
        int all = MEMBERSHIP_DECLARED_ANNOTATION | MEMBERSHIP_ANNOTATION | MEMBERSHIP_DECLARED_STEREOTYPE | MEMBERSHIP_STEREOTYPE

        expect: "the annotations that are declared"
        descriptor.annotations()['test.Stereotyped'] == all
        descriptor.annotations()['test.Tags'] == all

        and: "the ones that come with them"
        descriptor.annotations()['test.Marker'] == (MEMBERSHIP_DECLARED_STEREOTYPE | MEMBERSHIP_STEREOTYPE)
        descriptor.annotations()['jakarta.inject.Singleton'] == (MEMBERSHIP_DECLARED_STEREOTYPE | MEMBERSHIP_STEREOTYPE)
        descriptor.has('jakarta.inject.Scope', MEMBERSHIP_STEREOTYPE)
        descriptor.is(FLAG_SINGLETON)

        and: "an annotation of source retention is not in the metadata of the class"
        !descriptor.annotations().containsKey('test.Draft')

        and: "a repeated annotation is held by its container, which the class registers when it is initialised"
        !descriptor.annotations().containsKey('test.Tag')
        descriptor.repeatableContainers() == ['test.Tag': 'test.Tags']
    }

    void "the annotations of a bean of a factory are the ones of the method and of the factory"() {
        given:
        def descriptor = describedAs('test.Product')

        expect:
        descriptor.annotations()['jakarta.inject.Named'] == (MEMBERSHIP_DECLARED_ANNOTATION | MEMBERSHIP_ANNOTATION | MEMBERSHIP_DECLARED_STEREOTYPE | MEMBERSHIP_STEREOTYPE)
        descriptor.annotations()['io.micronaut.context.annotation.Factory'] == (MEMBERSHIP_ANNOTATION | MEMBERSHIP_STEREOTYPE)
        descriptor.qualifiers()*.annotationName == ['jakarta.inject.Named']
        descriptor.qualifiers()[0].stringValue().get() == 'first'
    }

    void "the qualifiers are described with the values of their members"() {
        given:
        def painted = descriptor('Painted')
        Map<CharSequence, Object> values = painted.qualifiers()[0].values

        expect:
        descriptor('NamedOne').qualifiers()*.annotationName == ['jakarta.inject.Named']
        descriptor('NamedOne').qualifiers()[0].stringValue().get() == 'one'
        descriptor('NamedOne').nonBindingMembers() == []
        descriptor('Plain').qualifiers() == []

        and:
        painted.qualifiers()*.annotationName == ['test.Colored']
        values.keySet() == ['name', 'shade', 'dark', 'mode', 'type', 'tags', 'levels', 'types', 'detail', 'comment', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        values.name == 'red'
        values.shade == 3
        values.dark == true
        values.mode == 'STRIPED'
        values.type == new AnnotationClassValue<>('java.lang.String')
        values.tags == ['a', 'b'] as String[]
        values.levels.getClass() == intArrayType
        values.levels as List == [1, 2]
        values.detail == new AnnotationValue<>('test.Detail', [value: 'fine'] as Map<CharSequence, Object>)
        values.comment == 'ignored'

        and: "the members that are not compared, which the metadata names in a member of its own"
        painted.nonBindingMembers() == ['comment', AnnotationUtil.NON_BINDING_ATTRIBUTE]
        values[AnnotationUtil.NON_BINDING_ATTRIBUTE] == ['comment', AnnotationUtil.NON_BINDING_ATTRIBUTE] as String[]

        and: "a class that is an array has the name of the class the definition loads, not the one of the source"
        values.types.getClass() == AnnotationClassValue[]
        values.types*.name == arrayClassNames
    }

    void "a member that is given no element is described as the array the definition holds"() {
        given:
        Map<CharSequence, Object> values = descriptor('Blank').qualifiers()[0].values

        expect:
        values.keySet() == ['name', 'tags', 'levels', 'details'] as Set
        [values.tags, values.levels, values.details]*.getClass() == emptyArrayTypes
        [values.tags, values.levels, values.details].every { java.lang.reflect.Array.getLength(it) == 0 }
    }

    void "an array of classes that is given no element is described where the definition holds one of class values"() {
        expect:
        undescribed.contains('test.$Untyped$Definition') ||
            descriptor('Untyped').qualifiers()[0].values.types == [] as AnnotationClassValue[]
    }

    void "the conditions that are checked before the definition is loaded are described in their order"() {
        expect:
        descriptor('Conditional').preLoadConditions() == [
            new MatchesPropertyCondition('descriptor.enabled', 'true', 'true', MatchesPropertyCondition.Condition.EQUALS),
            new MatchesMissingPropertyCondition('descriptor.disabled'),
            new MatchesEnvironmentCondition(['test'] as String[]),
            new MatchesNotEnvironmentCondition(['cloud'] as String[]),
            new MatchesPresenceOfClassesCondition([new AnnotationClassValue<>('java.lang.String'), new AnnotationClassValue<>(arrayClassNames[0])] as AnnotationClassValue[]),
            new MatchesAbsenceOfClassesCondition([new AnnotationClassValue<>('test.Missing')] as AnnotationClassValue[]),
            new MatchesPresenceOfEntitiesCondition([new AnnotationClassValue<>('test.Marker')] as AnnotationClassValue[]),
            new MatchesConfigurationCondition('test', null),
            new MatchesSdkCondition(Requires.Sdk.JAVA, '17'),
            new MatchesPresenceOfResourcesCondition(['classpath:descriptor.txt'] as String[]),
            new MatchesCurrentOsCondition(EnumSet.allOf(Requires.Family)),
            new MatchesCurrentNotOsCondition(EnumSet.of(Requires.Family.SOLARIS)),
            new MatchesPresenceOfClassesCondition([new AnnotationClassValue<>('test.Plain')] as AnnotationClassValue[])
        ]

        and: "a bean without requirements has none"
        descriptor('Plain').preLoadConditions() == []
        !descriptor('Plain').is(FLAG_POST_LOAD_CONDITIONS)
    }

    void "a bean with around advice has a descriptor for its definition and for the one of its proxy"() {
        given:
        def proxy = descriptor('Advised$Definition$Intercepted')

        expect:
        flags('Advised') == [FLAG_PROXIED_BEAN, FLAG_SINGLETON]
        flags('Advised$Definition$Intercepted') == [FLAG_SINGLETON]
        proxy.beanType() == 'test.$Advised$Definition$Intercepted'
        proxy.exposedTypes().containsAll(['test.Advised', 'test.$Advised$Definition$Intercepted'])
        proxy.has('test.Traced', MEMBERSHIP_DECLARED_ANNOTATION)
    }

    void "a definition the format cannot describe keeps an empty entry"() {
        expect:
        undescribed.contains('test.$Dynamic$Definition')
        undescribed.every { content(it).length == 0 }
        undescribed.every { BeanDefinitionDescriptors.load(classLoader, it) != null }
    }

    void "the entries of the other services are empty"() {
        given:
        Map<String, Set<String>> services = MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(new URLClassLoader(output, null))

        expect:
        services.keySet().containsAll([SERVICE, 'io.micronaut.core.beans.BeanIntrospectionReference'])
        services.findAll { it.key != SERVICE }.every { service, names ->
            names.every { classLoader.getResource("META-INF/micronaut/$service/$it").bytes.length == 0 }
        }
    }

    void "the definitions are found from entries with content as they are from empty ones, in a directory and in a jar"() {
        given: "what was compiled, a copy of it with the entries emptied, and a jar of it"
        Path copies = Files.createTempDirectory("descriptors")
        ClassLoader compiled = new URLClassLoader(output, null)
        ClassLoader emptied = new URLClassLoader(emptiedCopy(copies.resolve("emptied")), null)
        ClassLoader jar = new URLClassLoader([jarOf(copies.resolve("beans.jar")).toUri().toURL()] as URL[], null)

        when:
        Map<String, Set<String>> found = MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(compiled)
        Map<String, Set<String>> foundEmptied = MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(emptied)
        Map<String, Set<String>> foundInJar = MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(jar)

        then:
        found[SERVICE].size() > 15
        sorted(found) == sorted(foundEmptied)
        sorted(found) == sorted(foundInJar)

        and: "the entries of the copy are empty, and the ones of the jar are the ones that were compiled"
        found[SERVICE].count { content(it).length > 0 } == found[SERVICE].size() - undescribed.size()
        found[SERVICE].every { BeanDefinitionDescriptors.read(emptied, it).length == 0 }
        found[SERVICE].every { BeanDefinitionDescriptors.read(jar, it) == content(it) }

        cleanup:
        jar?.close()
        copies?.toFile()?.deleteDir()
    }

    // A smoke test of the beans the other features describe. Nothing in the context reads the content of an entry,
    // so this cannot fail because of what a descriptor says.
    void "a context starts from the definitions that were compiled"() {
        given: "the definitions the loader of the services finds in what was compiled"
        Set<String> names = MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(new URLClassLoader(output, null))[SERVICE]
        ApplicationContext context = ApplicationContext.builder()
            .classLoader(classLoader)
            .environments("test")
            .beanDefinitionsProvider { ClassLoader loader ->
                List<BeanDefinitionReference<?>> references = names.collect { (BeanDefinitionReference<?>) classLoader.loadClass(it).getDeclaredConstructor().newInstance() }
                return references + [new InterceptorRegistryBean(), new BeanProviderDefinition(), new JakartaProviderBeanDefinition(), new ApplicationEventPublisherFactory<>()]
            }
            .start()

        expect: "every definition is in the context, but the one the proxy replaces and, once it is checked, the one whose requirements are not met"
        (names - context.beanDefinitionReferences*.beanDefinitionName - ['test.$Advised$Definition', 'test.$Conditional$Definition']).isEmpty()
        context.getBean(type('Plain')) != null
        context.getBean(type('Api'), Qualifiers.byName('one')).getClass() == type('NamedOne')
        context.getBean(type('Api')).getClass() == type('First')
        context.getBean(type('Product'), Qualifiers.byName('first')) != null
        context.getBean(type('Advised')).hello() == 'hello traced'
        !context.containsBean(type('Conditional'))

        cleanup:
        context.close()
    }

    /**
     * @param bean The simple name of a bean class, or of a definition without its suffix
     * @return The descriptor of its definition
     */
    protected BeanDefinitionDescriptor descriptor(String bean) {
        BeanDefinitionDescriptor.read(content('test.$' + bean + '$Definition'))
    }

    /**
     * @param beanType The name of a bean type
     * @return The descriptor of the one definition of a bean of that type
     */
    protected BeanDefinitionDescriptor describedAs(String beanType) {
        List<BeanDefinitionDescriptor> descriptors = definitions().collect { BeanDefinitionDescriptor.read(content(it)) }.findAll { it?.beanType() == beanType }
        assert descriptors.size() == 1
        return descriptors[0]
    }

    /**
     * @param bean The simple name of a bean class
     * @return The flags of the descriptor of its definition
     */
    protected List<Integer> flags(String bean) {
        flags(descriptor(bean))
    }

    protected List<Integer> flags(BeanDefinitionDescriptor descriptor) {
        (0..<Integer.SIZE).collect { 1 << it }.findAll { descriptor.is(it) }
    }

    protected byte[] content(String definition) {
        byte[] content = BeanDefinitionDescriptors.read(classLoader, definition)
        assert content != null
        return content
    }

    protected Class<?> type(String simpleName) {
        classLoader.loadClass('test.' + simpleName)
    }

    private Set<String> definitions() {
        MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(new URLClassLoader(output, null))[SERVICE]
    }

    private static Map<String, List<String>> sorted(Map<String, Set<String>> services) {
        new TreeMap<String, List<String>>(services.collectEntries { service, names -> [service, names.sort(false)] })
    }

    /**
     * Copies what was compiled with every {@code META-INF/micronaut} entry emptied, which is what a processor
     * without descriptors writes.
     */
    private URL[] emptiedCopy(Path target) {
        output.eachWithIndex { URL url, int i ->
            Path root = Path.of(url.toURI())
            Path copy = Files.createDirectories(target.resolve("out$i"))
            Files.walk(root).withCloseable { paths ->
                paths.filter(Files::isRegularFile).forEach { Path file ->
                    Path to = copy.resolve(root.relativize(file).toString())
                    Files.createDirectories(to.parent)
                    if (root.relativize(file).toString().replace(File.separatorChar, '/' as char).startsWith("META-INF/micronaut/")) {
                        Files.write(to, new byte[0])
                    } else {
                        Files.copy(file, to)
                    }
                }
            }
        }
        return (0..<output.length).collect { target.resolve("out$it").toUri().toURL() } as URL[]
    }

    private Path jarOf(Path target) {
        new JarOutputStream(Files.newOutputStream(target)).withCloseable { jar ->
            Set<String> added = []
            for (URL url : output) {
                Path root = Path.of(url.toURI())
                Files.walk(root).withCloseable { paths ->
                    paths.filter { it != root }.forEach { Path path ->
                        // with the entries of the directories, as the jar of a build has them
                        String name = root.relativize(path).toString().replace(File.separatorChar, '/' as char) + (Files.isDirectory(path) ? '/' : '')
                        if (added.add(name)) {
                            jar.putNextEntry(new JarEntry(name))
                            if (Files.isRegularFile(path)) {
                                jar.write(Files.readAllBytes(path))
                            }
                            jar.closeEntry()
                        }
                    }
                }
            }
        }
        return target
    }
}
