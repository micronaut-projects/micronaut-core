package io.micronaut.context

import groovy.transform.CompileStatic
import io.micronaut.context.conditions.MatchesAbsenceOfClassesCondition
import io.micronaut.context.conditions.MatchesEnvironmentCondition
import io.micronaut.context.conditions.MatchesMissingPropertyCondition
import io.micronaut.context.conditions.MatchesPresenceOfClassesCondition
import io.micronaut.context.conditions.MatchesPresenceOfEntitiesCondition
import io.micronaut.context.conditions.MatchesPropertyCondition
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.writer.BeanDefinitionDescriptor
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.lang.annotation.Annotation
import java.lang.reflect.Array
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate
import java.util.function.UnaryOperator

/**
 * The lazy references of this module's test classes, read with the verification on: what each answers from its
 * descriptor is what its definition answers, the verification finds no difference in them, and it finds each kind
 * of difference a descriptor can have.
 *
 * <p>The processor's descriptor is package-private, so it is only held in untyped variables here; the questions are
 * asked in statically compiled methods, which do not introspect the classes of the definitions.</p>
 */
class LazyBeanDefinitionReferenceAnswersSpec extends Specification {

    private static final String ENTRIES = 'META-INF/micronaut/' + BeanDefinitionReference.name + '/'
    private static final String VERIFY = DefaultBeanDefinitionsProvider.LAZY_VERIFY_PROPERTY
    private static final String REPORT = DefaultBeanDefinitionsProvider.LAZY_VERIFY_REPORT_PROPERTY
    // the values of the processor's constants, which this class cannot read
    private static final int FLAG_SINGLETON = 1 << 4
    private static final int FLAG_POST_LOAD_CONDITIONS = 1 << 9
    private static final int FLAG_DECLARED_INDEXES = 1 << 10
    private static final int FLAG_PROPERTY_EXPRESSIONS = 1 << 11
    private static final int MEMBERSHIP_STEREOTYPE = 1 << 3
    /** Annotations a bean may or may not have, asked of every reference besides the ones its definition has. */
    private static final List<String> OTHERS = [
        AnnotationUtil.NAMED, AnnotationUtil.SINGLETON, AnnotationUtil.QUALIFIER, AnnotationUtil.SCOPE,
        'io.micronaut.context.annotation.Requires', 'io.micronaut.context.annotation.Requirements',
        'io.micronaut.context.annotation.Primary', 'io.micronaut.context.annotation.Context',
        'io.micronaut.context.annotation.Executable', 'io.micronaut.core.annotation.Indexed',
        'io.micronaut.core.annotation.Indexes', 'io.micronaut.context.annotation.Property', 'not.an.Annotation'
    ]

    @Shared
    ClassLoader loader = getClass().classLoader

    @TempDir
    Path temp

    void "every lazy reference answers from its descriptor what its definition answers, and the verification agrees"() {
        given:
        List<LazyBeanDefinitionReference<?>> lazy = withVerification { LazyBeanDefinitions.lazy(LazyBeanDefinitions.provide(loader)) }

        when:
        Map<String, Object> result = compare(new DefaultBeanDefinitionsProvider().provide(loader), lazy)

        then:
        (result.compared as int) > 500
        (result.differences as List).take(20) == []

        when: "once loaded, they are asked again"
        Map<String, Object> again = compare(new DefaultBeanDefinitionsProvider().provide(loader), lazy)

        then:
        (again.compared as int) == (result.compared as int)
        (again.differences as List).take(20) == []
    }

    void "the verification fails the load of a definition whose descriptor disagrees with it: #field"() {
        given:
        Path report = temp.resolve('report.txt')
        def real = sample()
        def wrong = copy(real, change(field, real))
        LazyBeanDefinitionReference<?> reference = withVerification(report) { lazyReference(wrong) }

        when:
        reference.load()

        then:
        IllegalStateException e = thrown()
        e.message.contains(real.definitionName() as String)
        e.message.contains(field)

        and: "every later lookup that reaches it fails too, and the difference is reported"
        failure { reference.instantiate() } instanceof IllegalStateException
        Files.readString(report).contains(field)

        where:
        field << ['flags', 'beanType', 'order', 'exposedTypes', 'indexes', 'annotation not.an.Annotation', 'container of a.Repeated',
                  'nonBindingMembers', 'qualifiers', 'preLoadConditions', 'qualifierDefaults']
    }

    void "an answer the descriptor gave before the load that the definition does not give fails the load"() {
        given: "a descriptor that says the bean has a stereotype it does not have"
        Path report = temp.resolve('report.txt')
        def real = sample()
        def wrong = copy(real, [annotations: real.annotations() + ['a.Stereotype': MEMBERSHIP_STEREOTYPE]])
        LazyBeanDefinitionReference<?> reference = withVerification(report) { lazyReference(wrong) }

        expect: "the reference answers from it"
        reference.getAnnotationMetadata().hasStereotype('a.Stereotype')
        reference.getAnnotationMetadata().hasStereotype(['other.Stereotype', 'a.Stereotype'] as String[])
        !reference.isInstantiated()

        when:
        reference.load()

        then:
        IllegalStateException e = thrown()
        e.message.contains('hasStereotype(a.Stereotype)')
        e.message.contains('annotation a.Stereotype')
    }

    void "an answer the descriptor gives once the definition is loaded is compared at once"() {
        given:
        Path report = temp.resolve('report.txt')
        def real = sample()
        def named = copy(real, [qualifiers : [new AnnotationValue<>(AnnotationUtil.NAMED, [value: 'other'] as Map<CharSequence, Object>)],
                                annotations: real.annotations() + [(AnnotationUtil.NAMED): 15]])
        LazyBeanDefinitionReference<?> reference = withVerification(report) { lazyReference(real) }
        LazyBeanDefinitionReference<?> wrong = withVerification(report) { lazyReference(named) }
        reference.load()
        failure { wrong.load() }

        expect: "the one that agrees answers again without a difference"
        reference.getBeanName() == reference.loaded('test').getBeanName()

        when: "the one that does not is asked a question it was not asked yet"
        wrong.getAnnotationMetadata().findDeclaredAnnotation(AnnotationUtil.NAMED)

        then:
        IllegalStateException e = thrown()
        e.message.contains('findDeclaredAnnotation(' + AnnotationUtil.NAMED)
    }

    void "a reference predicate that answers otherwise for the definition fails the lookup"() {
        given:
        Path report = temp.resolve('report.txt')
        def real = sample()
        LazyBeanDefinitionReference<?> reference = withVerification(report) { lazyReference(real) }

        when: "the predicate answers alike"
        reference.verifyPredicate({ true } as Predicate, true)

        then:
        noExceptionThrown()

        when: "the descriptor left out a bean the definition selects"
        reference.verifyPredicate({ true } as Predicate, false)

        then:
        IllegalStateException e = thrown()
        e.message.contains('a reference predicate of a lookup')
        Files.readString(report).contains('a reference predicate of a lookup')
    }

    void "the verification at the stop of a context loads and compares what no lookup loaded, without failing"() {
        given:
        Path report = temp.resolve('report.txt')
        def real = sample()
        // another order than the failing load above, whose difference is reported once per JVM
        def wrong = copy(real, [order: (real.order() as int) + 2])
        List<LazyBeanDefinitionReference<?>> references = withVerification(report) { [lazyReference(real), lazyReference(wrong)] }
        references.each { it.getAnnotationMetadata().hasAnnotation(AnnotationUtil.NAMED) }

        when:
        LazyBeanDefinitionVerifier.verifyUnloaded(references)

        then:
        noExceptionThrown()
        references.every { it.isInstantiated() }
        Files.readString(report).contains('order')

        when: "asked again"
        LazyBeanDefinitionVerifier.verifyUnloaded(references)

        then:
        noExceptionThrown()
    }

    void "without the verification a reference keeps no answers"() {
        given:
        def real = sample()
        LazyBeanDefinitionReference<?> reference = withoutVerification { lazyReference(real) }

        expect:
        reference.answers == null
        reference.getAnnotationMetadata().hasAnnotation(AnnotationUtil.NAMED) == reference.loaded('test').getAnnotationMetadata().hasAnnotation(AnnotationUtil.NAMED)
        reference.toString()
    }

    void "a lazy reference answers the questions it leaves to its definition as the definition does"() {
        given:
        def real = sample()
        LazyBeanDefinitionReference<?> reference = withoutVerification { lazyReference(real) }
        BeanDefinitionReference<?> definition = new DefaultBeanDefinitionsProvider().provide(loader).find { it.getBeanDefinitionName() == real.definitionName() }

        expect: "before it is loaded"
        !reference.isInstantiated()
        reference.toString().contains(real.beanType() as String)
        reference.getAnnotationMetadata().toString().contains(real.beanType() as String)
        reference.links()
        nullAnswers(reference.getAnnotationMetadata()) == [false] * 7

        and: "the questions it leaves to the definition, which load it"
        delegated(reference) == delegated(definition)
        reference.isInstantiated()
        reference.links()
        reference.toString() == definition.toString()
    }

    void "the descriptor answers the values of a declared qualifier, and leaves to the definition the ones it cannot"() {
        given:
        def real = sample()
        def qualified = copy(real, [
            qualifiers : [new AnnotationValue<>('a.Qualifier', [names: ['x', 'y'] as String[], name: 'z', ints: [1, 2] as int[], type: new AnnotationClassValue<>(String.name),
                                                               missing: new AnnotationClassValue<>('a.Missing'), none: [] as String[]] as Map<CharSequence, Object>)],
            annotations: real.annotations() + ['a.Qualifier': 15]
        ])
        LazyBeanDefinitionReference<?> reference = withoutVerification { lazyReference(qualified) }
        AnnotationMetadata metadata = reference.getAnnotationMetadata()

        expect:
        metadata.stringValue('a.Qualifier', 'names') == Optional.of('x')
        metadata.stringValue('a.Qualifier', 'name') == Optional.of('z')
        metadata.stringValues('a.Qualifier', 'names') == ['x', 'y'] as String[]
        metadata.stringValues('a.Qualifier', 'name') == ['z'] as String[]
        metadata.classValue('a.Qualifier', 'type') == Optional.of(String)
        metadata.classValue('a.Qualifier', 'missing') == Optional.empty()
        metadata.getValues('a.Qualifier').keySet()*.toString().sort() == ['ints', 'missing', 'name', 'names', 'none', 'type']
        metadata.findAnnotation('a.Qualifier').isPresent()
        metadata.findDeclaredAnnotation('a.Qualifier').isPresent()
        !reference.isInstantiated()

        and: "the values it cannot answer are the definition's, which has no such annotation"
        metadata.stringValue('a.Qualifier', 'ints') == Optional.empty()
        metadata.stringValue('a.Qualifier', 'none') == Optional.empty()
        metadata.stringValues('a.Qualifier', 'ints') == [] as String[]
        metadata.classValue('a.Qualifier', 'name') == Optional.empty()
        reference.isInstantiated()
    }

    void "a reference whose definition, or whose bean type, is not present"() {
        given:
        def real = sample()
        LazyBeanDefinitionReference<?> noDefinition = withoutVerification { lazyReference(copy(real, [definitionName: 'a.$Missing$Definition'])) }
        LazyBeanDefinitionReference<?> noType = withoutVerification { lazyReference(copy(real, [beanType: 'a.Missing', exposedTypes: ['a.Missing']])) }

        expect: "the first cannot link"
        !noDefinition.links()
        !noDefinition.isPresent()
        noDefinition.load() == null
        noDefinition.instantiate() == null
        failure { noDefinition.loaded('test') } instanceof NoClassDefFoundError
        noDefinition.toString()

        and: "the second has no bean type"
        failure { noType.getBeanType() } instanceof NoClassDefFoundError
        noType.getExposedTypes().isEmpty()
        !noType.isPresent()
    }

    void "a reference read through a child class loader loads its classes from the loader that has its entry"() {
        given:
        def real = sample()
        URLClassLoader child = new URLClassLoader(new URL[0], loader)
        LazyBeanDefinitionReference<?> reference = withoutVerification { lazyReference(real, child) }

        expect:
        reference.getBeanType().getName() == real.beanType()
        reference.getBeanType().getClassLoader() == loader
        !reference.getExposedTypes().isEmpty()

        cleanup:
        child.close()
    }

    void "the conditions of a descriptor are checked without its definition"() {
        given:
        def real = sample()
        // a lazy context, which keeps the reasons of a disabled lazy reference without loading its definition
        System.setProperty(DefaultBeanDefinitionsProvider.LAZY_PROPERTY, 'true')
        ApplicationContext context = ApplicationContext.builder().deduceEnvironment(false).environments('lazytest').start()
        System.clearProperty(DefaultBeanDefinitionsProvider.LAZY_PROPERTY)
        LazyBeanDefinitionReference<?> missing = withoutVerification {
            lazyReference(copy(real, [preLoadConditions: [new MatchesPresenceOfClassesCondition([new AnnotationClassValue<>('a.Missing')] as AnnotationClassValue[])]]))
        }
        LazyBeanDefinitionReference<?> met = withoutVerification {
            lazyReference(copy(real, [preLoadConditions: [
                new MatchesPresenceOfClassesCondition([new AnnotationClassValue<>(String.name)] as AnnotationClassValue[]),
                new MatchesEnvironmentCondition(['lazytest'] as String[]),
                new MatchesMissingPropertyCondition('a.missing.property')
            ]]))
        }
        LazyBeanDefinitionReference<?> classes = withoutVerification {
            lazyReference(copy(real, [preLoadConditions: [
                new MatchesPresenceOfClassesCondition([new AnnotationClassValue<>(String.name)] as AnnotationClassValue[]),
                new MatchesAbsenceOfClassesCondition([new AnnotationClassValue<>('a.Missing')] as AnnotationClassValue[])
            ]]))
        }
        LazyBeanDefinitionReference<?> entities = withoutVerification {
            lazyReference(copy(real, [preLoadConditions: [new MatchesPresenceOfEntitiesCondition([new AnnotationClassValue<>('a.Entity')] as AnnotationClassValue[])]]))
        }

        expect:
        !missing.isPreLoadEnabled(context, null)
        !missing.isEnabled(context)
        !missing.isEnabledByClasses()
        met.isPreLoadEnabled(context, null)
        met.isEnabled(context, null)
        !met.isEnabledByClasses()
        classes.isEnabledByClasses()
        classes.isEnabled(context)
        !entities.isPreLoadEnabled(context, null)
        [missing, met, classes, entities].every { !it.isInstantiated() }

        cleanup:
        context.close()
    }

    void "the descriptors of the writers and revisions given to ignore are not used"() {
        given:
        def real = sample()
        String property = DefaultBeanDefinitionsProvider.LAZY_IGNORE_WRITERS_PROPERTY

        when:
        int all = LazyBeanDefinitions.lazy(LazyBeanDefinitions.provide(loader)).size()
        System.setProperty(property, " ${real.writer()}, r99, rx, r, ,other".toString())
        int byWriter = LazyBeanDefinitions.lazy(LazyBeanDefinitions.provide(loader)).size()
        System.setProperty(property, "r${real.revision()}".toString())
        int byRevision = LazyBeanDefinitions.lazy(LazyBeanDefinitions.provide(loader)).size()

        then:
        all > 500
        byWriter < all
        byRevision < all

        cleanup:
        System.clearProperty(property)
    }

    /**
     * Runs the given closure with the verification switched on, and its report written to the given file, as a
     * context reads them.
     */
    private static <T> T withVerification(Path report = null, Closure<T> closure) {
        Map<String, String> previous = [VERIFY, REPORT].collectEntries { [(it): System.getProperty(it)] }
        System.setProperty(VERIFY, 'true')
        if (report != null) {
            System.setProperty(REPORT, report.toString())
        } else {
            System.clearProperty(REPORT)
        }
        try {
            return closure.call()
        } finally {
            previous.each { key, value -> value == null ? System.clearProperty(key) : System.setProperty(key, value) }
        }
    }

    private static <T> T withoutVerification(Closure<T> closure) {
        String previous = System.getProperty(VERIFY)
        System.clearProperty(VERIFY)
        try {
            return closure.call()
        } finally {
            if (previous != null) {
                System.setProperty(VERIFY, previous)
            }
        }
    }

    /**
     * A lazy reference with the given descriptor, read as a context reads it.
     */
    private LazyBeanDefinitionReference<?> lazyReference(descriptor, ClassLoader classLoader = loader) {
        LazyBeanDefinitions definitions = LazyBeanDefinitions.of(LazyBeanDefinitions.provide(classLoader))
        BeanDefinitionDescriptorData data = BeanDefinitionDescriptorReader.read(descriptor.toByteArray() as byte[], descriptor.definitionName() as String, UnaryOperator.identity())
        assert data != null
        return new LazyBeanDefinitionReference<>(data, classLoader, definitions)
    }

    /**
     * The descriptor of a singleton of the test classes that has no property expression, no condition checked after
     * the load and nothing else the tests change.
     */
    private sample() {
        List<String> names = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(loader, BeanDefinitionReference.name).sort()
        for (String name : names) {
            byte[] content = content(name)
            def descriptor = content ? BeanDefinitionDescriptor.read(content) : null
            if (descriptor != null && !descriptor.exposedTypes().isEmpty()
                && ((descriptor.flags() as int) & (FLAG_SINGLETON | FLAG_PROPERTY_EXPRESSIONS | FLAG_POST_LOAD_CONDITIONS | FLAG_DECLARED_INDEXES)) == FLAG_SINGLETON
                && descriptor.qualifiers().isEmpty() && descriptor.preLoadConditions().isEmpty() && descriptor.indexes().isEmpty()
                && descriptor.repeatableContainers().isEmpty() && descriptor.qualifierDefaults().isEmpty()
                && name.startsWith('io.micronaut.inject.')) {
                return descriptor
            }
        }
        return null
    }

    /**
     * What a field of a descriptor is changed to, for each field the verification compares.
     */
    private static Map<String, Object> change(String field, d) {
        switch (field) {
            case 'flags': return [flags: (d.flags() as int) ^ FLAG_SINGLETON]
            case 'beanType': return [beanType: Runnable.name]
            case 'order': return [order: (d.order() as int) + 1]
            case 'exposedTypes': return [exposedTypes: d.exposedTypes() + Runnable.name]
            case 'indexes': return [indexes: d.indexes() + Runnable.name, flags: (d.flags() as int) | FLAG_DECLARED_INDEXES]
            case 'annotation not.an.Annotation': return [annotations: d.annotations() + ['not.an.Annotation': 15]]
            case 'container of a.Repeated': return [repeatableContainers: d.repeatableContainers() + ['a.Repeated': 'a.Repeateds']]
            case 'nonBindingMembers': return [nonBindingMembers: d.nonBindingMembers() + 'extra']
            case 'qualifiers': return [qualifiers : d.qualifiers() + new AnnotationValue<>(AnnotationUtil.NAMED, [value: 'wrong'] as Map<CharSequence, Object>),
                                       annotations: d.annotations() + [(AnnotationUtil.NAMED): 15]]
            case 'preLoadConditions': return [preLoadConditions: d.preLoadConditions() + new MatchesPropertyCondition('a.property', null, null, MatchesPropertyCondition.Condition.CONTAINS)]
            case 'qualifierDefaults': return [qualifierDefaults: d.qualifierDefaults() + new AnnotationValue<>('a.Qualifier',
                [size: 'large', type: new AnnotationClassValue<>('a.Default'), kinds: ['x'] as String[]] as Map<CharSequence, Object>)]
            default: throw new IllegalArgumentException(field)
        }
    }

    private byte[] content(String name) {
        URL entry = loader.getResource(ENTRIES + name)
        return entry == null ? null : entry.openStream().withCloseable { it.readAllBytes() }
    }

    private static copy(d, Map<String, Object> changes) {
        Map<String, Object> fields = [
            flags               : d.flags(), beanType: d.beanType(), exposedTypes: d.exposedTypes(), indexes: d.indexes(),
            annotations         : d.annotations(), repeatableContainers: d.repeatableContainers(), nonBindingMembers: d.nonBindingMembers(),
            qualifiers          : d.qualifiers(), preLoadConditions: d.preLoadConditions(), definitionName: d.definitionName(), order: d.order(),
            writer              : d.writer(), revision: d.revision(), qualifierDefaults: d.qualifierDefaults()
        ] + changes
        return new BeanDefinitionDescriptor(
            fields.flags as int, fields.beanType as String, fields.exposedTypes as List, fields.indexes as List,
            new TreeMap<String, Integer>(fields.annotations as Map), new TreeMap<String, String>(fields.repeatableContainers as Map),
            fields.nonBindingMembers as List, fields.qualifiers as List, fields.preLoadConditions as List, fields.definitionName as String,
            fields.order as int, fields.writer as String, fields.revision as int, fields.qualifierDefaults as List
        )
    }

    private static Throwable failure(Closure<?> closure) {
        try {
            closure.call()
            return null
        } catch (Throwable e) {
            return e
        }
    }

    /**
     * Asks each lazy reference, then loads its definition, and compares the answers with the eager reference's.
     *
     * @return The number of references compared and the differences found
     */
    @CompileStatic
    private Map<String, Object> compare(List<BeanDefinitionReference<?>> eagerReferences, List<LazyBeanDefinitionReference<?>> lazy) {
        Map<String, BeanDefinitionReference<?>> eager = [:]
        for (BeanDefinitionReference<?> reference : eagerReferences) {
            eager.put(reference.getBeanDefinitionName(), reference)
        }
        List<String> differences = []
        int compared = 0
        for (LazyBeanDefinitionReference<?> reference : lazy) {
            BeanDefinitionReference<?> definition = eager.get(reference.getBeanDefinitionName())
            if (definition == null) {
                continue
            }
            Collection<String> names = names(definition.getAnnotationMetadata())
            Map<String, Object> described = new LinkedHashMap<>(referenceAnswers(reference))
            described.putAll(metadataAnswers(reference.getAnnotationMetadata(), names))
            // the verification compares the descriptor with the definition, and asks it again every answer above
            reference.load()
            Map<String, Object> answered = new LinkedHashMap<>(referenceAnswers(definition))
            answered.putAll(metadataAnswers(definition.getAnnotationMetadata(), names))
            for (String question : described.keySet()) {
                if (described.get(question) != answered.get(question)) {
                    differences.add(reference.getBeanDefinitionName() + ' ' + question + ': ' + described.get(question) + ' != ' + answered.get(question))
                }
            }
            compared++
        }
        Map<String, Object> result = [:]
        result.put('compared', compared)
        result.put('differences', differences)
        return result
    }

    /**
     * The questions a lazy reference leaves to its definition, which load it.
     */
    @CompileStatic
    private static List<Object> delegated(BeanDefinitionReference<?> reference) {
        AnnotationMetadata metadata = reference.getAnnotationMetadata()
        return [
            reference.getName(),
            reference.isAnnotationPresent(AnnotationUtil.SINGLETON), reference.isDeclaredAnnotationPresent(AnnotationUtil.SINGLETON),
            reference.hasDeclaredStereotype(AnnotationUtil.SINGLETON, AnnotationUtil.NAMED),
            reference.hasStereotypeNonRepeating(jakarta.inject.Singleton),
            normalize(reference.getValues(AnnotationUtil.SINGLETON)),
            reference.enumValuesSet('io.micronaut.context.annotation.Requires', 'os', io.micronaut.context.annotation.Requires.Family),
            reference.enumValuesSet(io.micronaut.context.annotation.Requires, 'os', io.micronaut.context.annotation.Requires.Family),
            metadata.enumValuesSet('io.micronaut.context.annotation.Requires', 'os', io.micronaut.context.annotation.Requires.Family),
            metadata.enumValuesSet(io.micronaut.context.annotation.Requires, 'os', io.micronaut.context.annotation.Requires.Family),
            reference.asArgument().getType().getName(),
            reference.getGenericBeanType().getType().getName(),
            reference.isCandidateBean(io.micronaut.core.type.Argument.of(reference.getBeanType())),
            reference.resolveDynamicQualifier() == null
        ]
    }

    /**
     * What a descriptor metadata answers for null arguments.
     */
    @CompileStatic
    private static List<Boolean> nullAnswers(AnnotationMetadata metadata) {
        return [
            metadata.hasAnnotation((Class<? extends Annotation>) null), metadata.hasDeclaredAnnotation((Class<? extends Annotation>) null),
            metadata.hasStereotype((Class<? extends Annotation>) null), metadata.hasDeclaredStereotype((Class<? extends Annotation>) null),
            metadata.hasStereotype((Class<? extends Annotation>[]) null), metadata.hasStereotype((String[]) null),
            metadata.hasDeclaredStereotype((Class<? extends Annotation>[]) null)
        ]
    }

    @CompileStatic
    private static Collection<String> names(AnnotationMetadata metadata) {
        Set<String> names = new TreeSet<>(OTHERS)
        names.addAll(metadata.getAnnotationNames())
        names.addAll(metadata.getDeclaredAnnotationNames())
        names.addAll(metadata.getStereotypeAnnotationNames())
        names.addAll(metadata.getDeclaredStereotypeAnnotationNames())
        return names
    }

    /**
     * What a reference answers without its definition, when it is lazy.
     */
    @CompileStatic
    private static Map<String, Object> referenceAnswers(BeanDefinitionReference<?> reference) {
        Map<String, Object> answers = new LinkedHashMap<>()
        answers.put('name', reference.getBeanDefinitionName())
        answers.put('order', reference.getOrder())
        answers.put('flags', [reference.isContextScope(), reference.isParallel(), reference.isProxiedBean(), reference.isProxyTarget(),
                              reference.isSingleton(), reference.isPrimary(), reference.isConfigurationProperties(), reference.isContainerType(),
                              reference.requiresMethodProcessing(), reference.isPresent()])
        answers.put('beanName', reference.getBeanName().orElse(null))
        answers.put('declaredQualifier', reference.getDeclaredQualifier() != null)
        answers.put('beanType', reference.getBeanType().getName())
        answers.put('exposedTypes', reference.getExposedTypes().collect { Class<?> type -> type.getName() }.sort())
        answers.put('indexes', reference.getIndexes().collect { Class<?> type -> type.getName() }.sort())
        return answers
    }

    /**
     * What an annotation metadata answers about the given annotations: the ones a descriptor answers for first, then
     * the values, which it answers for its declared qualifiers and for the annotations the bean does not have.
     */
    @CompileStatic
    private Map<String, Object> metadataAnswers(AnnotationMetadata metadata, Collection<String> names) {
        Map<String, Object> answers = new LinkedHashMap<>()
        answers.put('empty', metadata.isEmpty())
        String[] nameArray = names.toArray(new String[0])
        answers.put('anyStereotype', [metadata.hasStereotype(nameArray), metadata.hasDeclaredStereotype(nameArray),
                                      metadata.hasStereotype(new String[0]), metadata.hasDeclaredStereotype(new String[0])])
        List<Class<? extends Annotation>> types = []
        for (String name : names) {
            answers.put('has ' + name, [metadata.hasAnnotation(name), metadata.hasDeclaredAnnotation(name), metadata.hasStereotype(name),
                                        metadata.hasDeclaredStereotype(name), metadata.isAnnotationPresent(name), metadata.isDeclaredAnnotationPresent(name),
                                        metadata.findRepeatableAnnotation(name).orElse(null), metadata.isRepeatableAnnotation(name)])
            Class<? extends Annotation> type = annotationType(name)
            if (type != null) {
                types.add(type)
                answers.put('class ' + name, [metadata.hasAnnotation(type), metadata.hasDeclaredAnnotation(type), metadata.hasStereotype(type),
                                              metadata.hasDeclaredStereotype(type), metadata.isAnnotationPresent(type), metadata.isDeclaredAnnotationPresent(type),
                                              metadata.findRepeatableAnnotation(type).orElse(null), metadata.isRepeatableAnnotation(type),
                                              metadata.hasStereotypeNonRepeating(type)])
            }
        }
        Class<? extends Annotation>[] typeArray = (Class<? extends Annotation>[]) types.toArray(new Class[0])
        Class<? extends Annotation>[] noTypes = (Class<? extends Annotation>[]) new Class[0]
        answers.put('anyStereotypeClass', [metadata.hasStereotype(typeArray), metadata.hasDeclaredStereotype(typeArray),
                                           metadata.hasStereotype(noTypes), metadata.hasDeclaredStereotype(noTypes)])
        for (String name : names) {
            if (!metadata.hasAnnotation(name) || isQualifier(name)) {
                answers.put('values ' + name, [
                    normalize(metadata.stringValue(name)), normalize(metadata.stringValues(name)), normalize(metadata.classValue(name)),
                    normalize(metadata.getValues(name)), normalize(metadata.findAnnotation(name)), normalize(metadata.findDeclaredAnnotation(name)),
                    normalize(metadata.getAnnotation(name)), normalize(metadata.getDeclaredAnnotation(name)),
                    normalize(metadata.stringValue(name, AnnotationUtil.NON_BINDING_ATTRIBUTE)),
                    normalize(metadata.stringValues(name, AnnotationUtil.NON_BINDING_ATTRIBUTE))
                ])
            }
        }
        return answers
    }

    @CompileStatic
    private boolean isQualifier(String name) {
        Class<? extends Annotation> type = annotationType(name)
        if (type == null) {
            return false
        }
        for (Annotation annotation : type.getAnnotations()) {
            if (annotation.annotationType().getName() == AnnotationUtil.QUALIFIER) {
                return true
            }
        }
        return false
    }

    @CompileStatic
    private Class<? extends Annotation> annotationType(String name) {
        try {
            Class<?> type = Class.forName(name, false, loader)
            return type.isAnnotation() ? (Class<? extends Annotation>) type : null
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null
        }
    }

    /**
     * An answer in a form that compares by content.
     */
    @CompileStatic
    private static Object normalize(Object value) {
        if (value instanceof Optional) {
            Optional<?> optional = (Optional<?>) value
            return optional.isPresent() ? normalize(optional.get()) : null
        }
        if (value instanceof Map) {
            Map<String, Object> normalized = new TreeMap<>()
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalize(entry.getValue()))
            }
            return normalized
        }
        if (value instanceof AnnotationValue) {
            AnnotationValue<?> annotation = (AnnotationValue<?>) value
            return [annotation.getAnnotationName(), normalize(annotation.getValues())]
        }
        if (value instanceof AnnotationClassValue) {
            return ((AnnotationClassValue<?>) value).getName()
        }
        if (value instanceof Class) {
            return ((Class<?>) value).getName()
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> elements = []
            int length = Array.getLength(value)
            for (int i = 0; i < length; i++) {
                elements.add(normalize(Array.get(value, i)))
            }
            return elements
        }
        return value
    }
}
