package io.micronaut.context

import groovy.transform.CompileStatic
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

/**
 * A context with lazy bean definitions over this module's test classes answers the lookups an eager context answers,
 * with the warm-up in each of its modes, and with the verification on.
 *
 * <p>The lookups are made in statically compiled methods, which do not introspect the classes of the definitions.</p>
 */
class LazyBeanDefinitionsContextSpec extends Specification {

    private static final String LAZY = DefaultBeanDefinitionsProvider.LAZY_PROPERTY
    private static final String VERIFY = DefaultBeanDefinitionsProvider.LAZY_VERIFY_PROPERTY
    private static final String REPORT = DefaultBeanDefinitionsProvider.LAZY_VERIFY_REPORT_PROPERTY
    private static final String WARM_UP = DefaultBeanDefinitionsProvider.LAZY_WARM_UP_PROPERTY
    private static final List<String> STEREOTYPES = [
        'jakarta.inject.Singleton', 'io.micronaut.context.annotation.Prototype', 'io.micronaut.context.annotation.Factory',
        'io.micronaut.context.annotation.Bean', 'io.micronaut.context.annotation.Context', 'io.micronaut.context.annotation.Executable',
        'io.micronaut.context.annotation.ConfigurationReader', 'io.micronaut.context.annotation.EachProperty',
        'io.micronaut.context.annotation.EachBean', 'io.micronaut.aop.Around', 'io.micronaut.aop.Introduction',
        'io.micronaut.aop.InterceptorBinding', 'io.micronaut.context.annotation.Requires', 'jakarta.inject.Named',
        'io.micronaut.context.annotation.Primary', 'io.micronaut.runtime.context.scope.Refreshable', 'not.an.Annotation'
    ]

    /** The bean types of the proxy targets of the test classes. */
    private static final Set<String> PROXY_TARGETS = proxyTargets()

    List<ApplicationContext> contexts = []

    def cleanup() {
        contexts.reverse().each { if (it.isRunning()) it.close() }
    }

    void "a lazy context answers the lookups an eager one answers (warm-up #warmUp, verification #verify)"() {
        given:
        ApplicationContext eager = start([:])
        ApplicationContext lazy = start([(LAZY): 'true', (WARM_UP): warmUp, (VERIFY): verify])
        List<Class<?>> types = types(eager)
        Map<String, Object> eagerAnswers = lookups(eager, types)

        expect:
        types.size() > 100
        differences(lookups(lazy, types), eagerAnswers) == []
        isLazy(lazy)
        !isLazy(eager)

        and: "a disabled bean is reported alike"
        disabledFailures(lazy, eager) == disabledFailures(eager, eager)

        and: "whether there is a bean of a type, as the bootstrap of a context asks before the context has started"
        types.take(100).collect { exists(build([(LAZY): 'true']), it) } == types.take(100).collect { exists(build([:]), it) }

        and: "once it has started, for the types it has a bean of"
        types.findAll { eager.containsBean(it) }.take(100).every { exists(lazy, it) }

        when: "the lazy context stops and starts again"
        lazy.stop()
        lazy.start()

        then: "it answers again"
        differences(lookups(lazy, types), eagerAnswers) == []

        where:
        warmUp  | verify
        null    | null
        'start' | null
        'false' | null
        'false' | 'true'
        null    | 'true'
    }

    /**
     * Asks the bean definition service of a context whether it has a bean of a type, as a context asks for the
     * bootstrap property source locators.
     */
    private static boolean exists(ApplicationContext context, Class<?> type) {
        def field = DefaultBeanContext.getDeclaredField('beanDefinitionProvider')
        field.setAccessible(true)
        return field.get(context).exists(type)
    }

    /**
     * Builds a context over the test classes with the given switches, without starting it.
     */
    private ApplicationContext build(Map<String, String> switches) {
        return withSwitches(switches) {
            ApplicationContext.builder().deduceEnvironment(false).environments('lazytest').build()
        }
    }

    private static <T> T withSwitches(Map<String, String> switches, Closure<T> closure) {
        List<String> keys = [LAZY, VERIFY, REPORT, WARM_UP]
        Map<String, String> previous = keys.collectEntries { [(it): System.getProperty(it)] }
        keys.each { System.clearProperty(it) }
        switches.each { key, value -> if (value != null) System.setProperty(key, value) }
        try {
            return closure.call()
        } finally {
            previous.each { key, value -> value == null ? System.clearProperty(key) : System.setProperty(key, value) }
        }
    }

    /**
     * Starts a context over the test classes with the given switches, read as a context reads them when it is
     * created.
     */
    private ApplicationContext start(Map<String, String> switches) {
        List<String> keys = [LAZY, VERIFY, REPORT, WARM_UP]
        Map<String, String> previous = keys.collectEntries { [(it): System.getProperty(it)] }
        keys.each { System.clearProperty(it) }
        switches.each { key, value -> if (value != null) System.setProperty(key, value) }
        try {
            ApplicationContext context = ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments('lazytest')
                .start()
            contexts << context
            return context
        } finally {
            previous.each { key, value -> value == null ? System.clearProperty(key) : System.setProperty(key, value) }
        }
    }

    @CompileStatic
    private static List<String> differences(Map<String, Object> lazy, Map<String, Object> eager) {
        Set<String> keys = new TreeSet<>(lazy.keySet())
        keys.addAll(eager.keySet())
        return keys.findAll { String key -> lazy.get(key) != eager.get(key) }.collect { String key -> key + ': ' + difference(lazy.get(key), eager.get(key)) }.take(20)
    }

    @CompileStatic
    private static String difference(Object lazy, Object eager) {
        if (lazy instanceof Collection && eager instanceof Collection) {
            List<Object> onlyLazy = new ArrayList<>((Collection<Object>) lazy)
            onlyLazy.removeAll((Collection<Object>) eager)
            List<Object> onlyEager = new ArrayList<>((Collection<Object>) eager)
            onlyEager.removeAll((Collection<Object>) lazy)
            return 'only lazy ' + onlyLazy.take(10) + ', only eager ' + onlyEager.take(10) + ', sizes ' + ((Collection) lazy).size() + ' and ' + ((Collection) eager).size()
        }
        return String.valueOf(lazy) + ' != ' + String.valueOf(eager)
    }

    @CompileStatic
    private static Set<String> proxyTargets() {
        Set<String> targets = new HashSet<>()
        for (BeanDefinitionReference<?> reference : new DefaultBeanDefinitionsProvider().provide(LazyBeanDefinitionsContextSpec.classLoader)) {
            if (reference.isProxyTarget()) {
                try {
                    targets.add(reference.getBeanType().getName())
                } catch (LinkageError ignored) {
                    // a target whose type does not load is not listed
                }
            }
        }
        return targets
    }

    @CompileStatic
    private static boolean isLazy(ApplicationContext context) {
        return context.getBeanDefinitionReferences().any { BeanDefinitionReference<?> it -> it instanceof LazyBeanDefinitionReference }
    }

    /**
     * The types the beans of the test classes are exposed as, in a fixed order.
     */
    @CompileStatic
    private static List<Class<?>> types(ApplicationContext eager) {
        Set<String> seen = new TreeSet<>()
        List<Class<?>> types = []
        List<BeanDefinitionReference<Object>> references = new ArrayList<>(eager.getBeanDefinitionReferences())
        references.sort { BeanDefinitionReference<?> it -> it.getBeanDefinitionName() }
        for (BeanDefinitionReference<?> reference : references) {
            try {
                for (Class<?> type : reference.getExposedTypes()) {
                    if (!type.is(Object) && seen.add(type.getName())) {
                        types.add(type)
                    }
                }
            } catch (LinkageError ignored) {
                // not exposed as a type that loads
            }
        }
        return types
    }

    /**
     * What the context answers: the definitions of each type, of each stereotype and of each name, whether it has
     * a bean of each type, the present references and the disabled beans.
     */
    @CompileStatic
    private static Map<String, Object> lookups(ApplicationContext context, List<Class<?>> types) {
        Map<String, Object> answers = new TreeMap<>()
        for (Class<?> type : types) {
            answers.put('type ' + type.getName(), answer { definitions(context.getBeanDefinitions((Class<Object>) type)) })
            answers.put('contains ' + type.getName(), answer { context.containsBean(type) })
            answers.put('proxy target ' + type.getName(), answer {
                Optional<BeanDefinition<Object>> target = context.findProxyTargetBeanDefinition((Class<Object>) type, null)
                target.isPresent() ? target.get().getClass().getName() : null
            })
        }
        for (String stereotype : STEREOTYPES) {
            answers.put('stereotype ' + stereotype, answer { definitions(context.getBeanDefinitions(Qualifiers.byStereotype(stereotype))) })
        }
        Set<String> names = new TreeSet<>()
        for (BeanDefinitionReference<?> reference : context.getBeanDefinitionReferences()) {
            reference.getBeanName().ifPresent { String name -> names.add(name) }
        }
        for (String name : names.take(50)) {
            answers.put('named ' + name, answer { definitions(context.getBeanDefinitions(Qualifiers.<Object> byName(name))) })
        }
        // the definitions registered at run time are numbered in the order they are created, across contexts
        answers.put('references', context.getBeanDefinitionReferences()
            .collect { BeanDefinitionReference<?> it -> it.getBeanDefinitionName().replaceAll('DynamicDefinition[0-9]+$', 'DynamicDefinition') }
            .sort())
        // the beans the check of every reference, which runs on the common pool once the context started, found
        // disabled, each once: a bean is listed again each time a lookup meets it. A proxy target is checked when a
        // lookup needs it, which in eager mode is any lookup of a proxy target and in lazy mode one of its type.
        awaitEnabledCheck(context)
        answers.put('disabled', new TreeSet<String>(context.getDisabledBeans()
            .collect { DisabledBean<?> it -> it.type().getTypeName() + ' ' + it.reasons() }
            .findAll { String it -> !PROXY_TARGETS.contains(it.substring(0, it.indexOf(' '))) }))
        return answers
    }

    /**
     * What asking each context for the type of a bean the eager context disabled fails with.
     */
    @CompileStatic
    private static List<String> disabledFailures(ApplicationContext context, ApplicationContext eager) {
        List<String> failures = []
        for (DisabledBean<?> disabled : eager.getDisabledBeans().take(20)) {
            Class<?> type = disabled.type().getType()
            if (type.is(Object) || eager.containsBean(type)) {
                continue
            }
            try {
                context.getBean(type)
                failures.add(type.getName() + ' found')
            } catch (NoSuchBeanException e) {
                failures.add(type.getName() + (e.getMessage().contains('disabled') ? ' disabled' : ' missing'))
            } catch (Throwable e) {
                failures.add(type.getName() + ' failed: ' + e.getClass().getName())
            }
        }
        return failures
    }

    /**
     * Waits for the check of the enabled beans a context runs in the background once it has started.
     */
    private static void awaitEnabledCheck(ApplicationContext context) {
        def field = DefaultBeanContext.getDeclaredField('checkEnabledBeans')
        field.setAccessible(true)
        java.util.concurrent.ForkJoinTask<?> check = field.get(context) as java.util.concurrent.ForkJoinTask<?>
        check?.quietlyJoin()
    }

    @CompileStatic
    private static List<String> definitions(Collection<? extends BeanDefinition<?>> definitions) {
        return definitions.collect { BeanDefinition<?> it -> it.getClass().getName() }.sort()
    }

    @CompileStatic
    private static Object answer(Closure<?> closure) {
        try {
            return closure.call()
        } catch (Throwable e) {
            return 'failed: ' + e.getClass().getName()
        }
    }
}
