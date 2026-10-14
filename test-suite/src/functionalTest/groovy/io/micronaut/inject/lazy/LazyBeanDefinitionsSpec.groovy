package io.micronaut.inject.lazy

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.fixtures.context.ApplicationUnderTest
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Singleton
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * Lazy bean definition references ({@code micronaut.bean-definitions.lazy=true}) on an application compiled by
 * this build's processor, from a directory and from a JAR: what the context answers from the descriptors, when it
 * loads a definition, and that it answers what an eager context answers.
 *
 * <p>The application's classes are only on the class loader each test creates, so whether a {@code $Definition}
 * class was loaded is read off that loader.</p>
 */
class LazyBeanDefinitionsSpec extends Specification {

    static final String LAZY = 'micronaut.bean-definitions.lazy'
    static final String VERIFY = 'micronaut.bean-definitions.lazy.verify'
    static final String WARM_UP = 'micronaut.bean-definitions.lazy.warm-up'
    static final String ENTRIES = 'META-INF/micronaut/' + BeanDefinitionReference.name
    static final Map<String, String> EAGER = [:]
    static final Map<String, String> LAZY_ONLY = [(LAZY): 'true', (WARM_UP): 'false']
    /** A class load of one of the classes only the lazy mode uses, as -verbose:class prints it. */
    static final java.util.regex.Pattern LAZY_CLASSES = java.util.regex.Pattern.compile(
        '\\[class,load\\] io\\.micronaut\\.(context\\.(Lazy|BeanDefinitionDescriptor|DescriptorAnnotationMetadata)' +
            '|context\\.beans\\.DefaultBeanDefinitionService\\$Lazy|core\\.io\\.service\\.MicronautMetaServiceLoaderUtils\\$Described)')

    @Shared
    @TempDir
    Path shared

    @Shared
    Path classes

    @Shared
    Path jar

    @TempDir
    Path temp

    List<AutoCloseable> closeables = []

    def setupSpec() {
        def app = new ApplicationUnderTest(shared.resolve('app').toFile(), classpath('testapp.compile.classpath'), classpath('testapp.annotationprocessor.path'))
        sources().each { String path, String source ->
            Path file = shared.resolve('app/src/main/java').resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, source)
            app.addSourceFile(file.toFile())
        }
        app.compile()
        classes = shared.resolve('app/build/classes')
        // compiled against, missing at run time
        ['demo/Gone.class', 'demo/GoneBase.class'].each { Files.delete(classes.resolve(it)) }
        jar = jar(classes, shared.resolve('app.jar'))
    }

    def cleanup() {
        closeables.reverse().each { it.close() }
    }

    void "a lookup loads only the definitions it returns (#shape)"() {
        given:
        Path location = shape == 'jar' ? jar : classes
        FixtureLoader loader = loader(location)
        ApplicationContext context = start(loader, LAZY_ONLY)

        expect: "no definition of the application is loaded by the start"
        loaded(loader, location).isEmpty()

        when:
        def v8 = context.getBean(loader.loadClass('demo.V8'))

        then:
        v8.getClass().name == 'demo.V8'
        loaded(loader, location) == ['demo.$V8$Definition'] as Set

        when: "a stereotype lookup"
        def handlers = stereotyped(context, loader, 'demo.Handler')

        then:
        handlers == ['demo.HandlerA', 'demo.HandlerB']
        loaded(loader, location) == ['demo.$V8$Definition', 'demo.$HandlerA$Definition', 'demo.$HandlerB$Definition'] as Set

        when: "a name lookup"
        def named = names(context.getBeanDefinitions(Qualifiers.byName('v6'))).findAll { it.startsWith('demo.') }

        then:
        named == ['demo.V6']
        loaded(loader, location) == ['demo.$V8$Definition', 'demo.$HandlerA$Definition', 'demo.$HandlerB$Definition', 'demo.$V6$Definition'] as Set

        where:
        shape << ['directory', 'jar']
    }

    void "ordering, primary and replacement are the eager ones, and an unloaded reference answers its order"() {
        given:
        FixtureLoader eagerLoader = loader(classes)
        ApplicationContext eager = start(eagerLoader, EAGER)
        FixtureLoader lazyLoader = loader(classes)
        ApplicationContext lazy = start(lazyLoader, LAZY_ONLY)

        when: "the reference as the context holds it: the references it reports as present are loaded to tell"
        BeanDefinitionReference<?> v6 = lazy.beanDefinitionProvider.beanReferences.find { it.beanDefinitionName == 'demo.$V6$Definition' }

        then:
        v6 != null
        v6.order == 5
        !lazyLoader.loaded('demo.$V6$Definition')

        and:
        engines(lazy, lazyLoader) == engines(eager, eagerLoader)
        engines(lazy, lazyLoader).containsAll(['demo.V6', 'demo.V8', 'demo.V12Turbo'])
        !engines(lazy, lazyLoader).contains('demo.V12')
        lazy.getBean(lazyLoader.loadClass('demo.Engine')).getClass().name == eager.getBean(eagerLoader.loadClass('demo.Engine')).getClass().name
        lazy.getBean(lazyLoader.loadClass('demo.Engine')).getClass().name == 'demo.V8'
        lazy.getBean(lazyLoader.loadClass('demo.Wheel'), Qualifiers.byName('front')) != null
    }

    void "a failing pre-load condition never loads the definition, and the bean is disabled for the reasons eager mode gives"() {
        given:
        FixtureLoader eagerLoader = loader(classes)
        ApplicationContext eager = start(eagerLoader, EAGER)
        FixtureLoader lazyLoader = loader(classes)
        ApplicationContext lazy = start(lazyLoader, LAZY_ONLY)
        List<String> conditional = ['demo.NeedsGone', 'demo.NeedsProperty', 'demo.NotInTest', 'demo.OnSolaris']

        expect:
        conditional.every { !lazy.containsBean(lazyLoader.loadClass(it)) }
        conditional.every { !eager.containsBean(eagerLoader.loadClass(it)) }
        conditional.every { !lazyLoader.loaded(definition(it)) }
        disabled(lazy, conditional) == disabled(eager, conditional)
        disabled(lazy, conditional).keySet() == conditional as Set
    }

    void "a reference loaded before the background check keeps its post-load conditions for later"() {
        given:
        FixtureLoader loader = loader(classes)
        ApplicationContext context = start(loader, LAZY_ONLY)
        Class<?> needsX = loader.loadClass('demo.NeedsX')

        when: "the reference is loaded, as the warm-up does, and the check of the enabled beans runs after it"
        context.beanDefinitionProvider.beanReferences.find { it.beanDefinitionName == definition('demo.NeedsX') }.load()
        for (def ignored : context.beanDefinitionProvider.getBeanReferences(context)) {
        }
        context.registerSingleton(loader.loadClass('demo.X').getDeclaredConstructor().newInstance())

        then: "X is a bean now, so NeedsX is one too"
        context.getBean(needsX).getClass() == needsX
    }

    void "a definition that cannot link is not present, is not loaded before it is needed, and stops nothing"() {
        given:
        FixtureLoader eagerLoader = loader(classes)
        ApplicationContext eager = start(eagerLoader, EAGER)
        FixtureLoader lazyLoader = loader(classes)
        ApplicationContext lazy = start(lazyLoader, LAZY_ONLY)

        expect:
        !lazyLoader.loaded('demo.$Broken$Definition')
        singletons(lazy) == singletons(eager)
        !singletons(lazy).contains('demo.Broken')
        references(lazy) == references(eager)
        !references(lazy).contains('demo.$Broken$Definition')
    }

    void "the references a context reports leave out the ones that cannot link, as eager mode, whatever was looked up before"() {
        given:
        FixtureLoader eagerLoader = loader(classes)
        ApplicationContext eager = start(eagerLoader, EAGER)
        FixtureLoader lazyLoader = loader(classes)
        ApplicationContext lazy = start(lazyLoader, LAZY_ONLY)

        expect: "as the first call; which conditional ones are still listed depends on when the background check of the enabled beans ran, in both modes"
        !references(eager).contains('demo.$Broken$Definition')
        !references(lazy).contains('demo.$Broken$Definition')
        references(lazy).containsAll(['demo.$V6$Definition', 'demo.$V8$Definition', 'demo.$HandlerA$Definition'])
        lazy.beanDefinitionReferences.every { it.isPresent() }
    }

    void "the first failure of a reference is kept and thrown again, where eager mode fails the start"() {
        given: "a reference whose static initializer throws, given a descriptor"
        Path boom = copy(classes, temp.resolve('boom'))
        Files.write(boom.resolve(ENTRIES).resolve('demo.Boom'), DescriptorBytes.of('demo.Boom', ['jakarta.inject.Singleton': 15])
            .tap { flags = 1 << 4 }
            .withAppended('demo.Boom', 0, 'test', 1)
            .toByteArray())

        when:
        start(loader(boom), EAGER)

        then:
        thrown(Throwable)

        when:
        FixtureLoader loader = loader(boom)
        ApplicationContext context = start(loader, LAZY_ONLY)
        Class<?> type = Class.forName('demo.Boom', false, loader)
        Throwable first = failure { context.getBean(type) }
        Throwable second = failure { context.getBean(type) }

        then:
        first != null
        cause(first, IllegalStateException).message == 'boom'
        cause(second, IllegalStateException).is(cause(first, IllegalStateException))
        context.getBean(loader.loadClass('demo.V8')) != null
    }

    void "a lookup with a type argument does not load a failed sibling it does not select"() {
        given: "BarWriter, whose type argument is missing, so that the static initializer of its definition fails"
        Path failed = copy(classes, temp.resolve('failed'))
        Files.delete(failed.resolve('demo/Bar.class'))
        FixtureLoader eagerLoader = loader(failed)
        ApplicationContext eager = start(eagerLoader, EAGER)
        FixtureLoader lazyLoader = loader(failed)
        ApplicationContext lazy = start(lazyLoader, LAZY_ONLY)

        expect:
        outcome { lazy.getBean(Argument.of(lazyLoader.loadClass('demo.Writer'), lazyLoader.loadClass('demo.Foo'))) } ==
            outcome { eager.getBean(Argument.of(eagerLoader.loadClass('demo.Writer'), eagerLoader.loadClass('demo.Foo'))) }
        outcome { lazy.getBean(Argument.of(lazyLoader.loadClass('demo.Writer'), lazyLoader.loadClass('demo.Foo'))) } == 'demo.FooWriter'

        and: "a lookup that selects it fails in both modes"
        outcome { singletons(lazy) } == outcome { singletons(eager) }
    }

    void "threads that look the same bean up first get one definition and one singleton"() {
        given:
        FixtureLoader loader = loader(classes)
        ApplicationContext context = start(loader, LAZY_ONLY)
        Class<?> counter = loader.loadClass('demo.Counter')
        int threads = 16
        def barrier = new CyclicBarrier(threads)
        List<Object> beans = new CopyOnWriteArrayList<>()
        List<Object> definitions = new CopyOnWriteArrayList<>()

        expect:
        !loader.loaded('demo.$Counter$Definition')

        when:
        List<Thread> started = (1..threads).collect {
            Thread.start {
                barrier.await(30, TimeUnit.SECONDS)
                definitions << context.getBeanDefinition(counter)
                beans << context.getBean(counter)
            }
        }
        started*.join()

        then:
        beans.size() == threads
        definitions.size() == threads
        beans.every { it.is(beans[0]) }
        definitions.every { it.is(definitions[0]) }
        counter.getField('INSTANCES').get(null).get() == 1
    }

    void "entries without a usable descriptor are loaded as before, beside lazy ones (#shape)"() {
        given: "entries emptied, damaged, renamed, cut before the appended fields, or with bytes after them"
        Path mixed = copy(classes, temp.resolve('mixed'))
        Path entries = mixed.resolve(ENTRIES)
        byte[] v6 = Files.readAllBytes(entries.resolve(definition('demo.V6')))
        edit(entries, 'demo.Unused1') { byte[] content -> new byte[0] }
        edit(entries, 'demo.Unused2') { byte[] content -> DescriptorBytes.read(content).tap { magic = 0x4D4E4245 }.toByteArray() }
        edit(entries, 'demo.Unused3') { byte[] content -> DescriptorBytes.read(content).tap { version = 2 }.toByteArray() }
        edit(entries, 'demo.Unused4') { byte[] content -> concat(content, [0] as byte[]) }
        edit(entries, 'demo.HandlerB') { byte[] content -> lengthFixed(Arrays.copyOf(content, content.length - 5)) }
        edit(entries, 'demo.V12') { byte[] content -> v6 }
        edit(entries, 'demo.V12Turbo') { byte[] content -> DescriptorBytes.read(content).tap { appended = new byte[0] }.toByteArray() }
        edit(entries, 'demo.HandlerA') { byte[] content -> lengthFixed(concat(content, [1, 2, 3] as byte[])) }
        edit(entries, 'demo.Unused5') { byte[] content -> DescriptorBytes.read(content).withRevision(2).toByteArray() }
        Path location = shape == 'jar' ? jar(mixed, temp.resolve('mixed.jar')) : mixed

        FixtureLoader eagerLoader = loader(location)
        ApplicationContext eager = start(eagerLoader, EAGER)
        FixtureLoader lazyLoader = loader(location)
        ApplicationContext lazy = start(lazyLoader, LAZY_ONLY)

        expect: "the entries without a usable descriptor, a later revision included, were loaded at the start, the others were not"
        ['demo.Unused1', 'demo.Unused2', 'demo.Unused3', 'demo.Unused4', 'demo.Unused5', 'demo.HandlerB', 'demo.V12', 'demo.V12Turbo'].every { lazyLoader.loaded(definition(it)) }
        ['demo.HandlerA', 'demo.V6', 'demo.V8', 'demo.NeedsGone'].every { !lazyLoader.loaded(definition(it)) }

        and: "the context answers as an eager one"
        engines(lazy, lazyLoader) == engines(eager, eagerLoader)
        stereotyped(lazy, lazyLoader, 'demo.Handler') == stereotyped(eager, eagerLoader, 'demo.Handler')
        stereotyped(lazy, lazyLoader, 'demo.Handler') == ['demo.HandlerA', 'demo.HandlerB']
        singletons(lazy) == singletons(eager)

        where:
        shape << ['directory', 'jar']
    }

    void "the verify mode fails a lookup when a descriptor disagrees with its definition, and reports the others at the stop"() {
        given: "descriptors that say Unused1 is a Handler, and HandlerB is not"
        Path wrong = copy(classes, temp.resolve('wrong'))
        edit(wrong.resolve(ENTRIES), 'demo.Unused1') { byte[] content ->
            DescriptorBytes.read(content).tap { annotations.put('demo.Handler', 8) }.toByteArray()
        }
        edit(wrong.resolve(ENTRIES), 'demo.HandlerB') { byte[] content ->
            DescriptorBytes.read(content).tap { annotations.remove('demo.Handler') }.toByteArray()
        }

        when: "lazy, without the verifier"
        FixtureLoader lazyLoader = loader(wrong)
        ApplicationContext lazy = start(lazyLoader, LAZY_ONLY)
        def selected = stereotyped(lazy, lazyLoader, 'demo.Handler')

        then: "a bean the descriptor selects is asked again once loaded, while one it leaves out is not loaded"
        selected == ['demo.HandlerA']
        lazyLoader.loaded(definition('demo.Unused1'))
        !lazyLoader.loaded(definition('demo.HandlerB'))

        when: "lazy, with the verifier"
        Path report = temp.resolve('report.txt')
        FixtureLoader verifyLoader = loader(wrong)
        ApplicationContext verified = start(verifyLoader, LAZY_ONLY + [(VERIFY): 'true', (VERIFY + '.report'): report.toString()])
        stereotyped(verified, verifyLoader, 'demo.Handler')

        then: "the lookup fails on the first of the two it meets: the bean the descriptor wrongly selects, or the one it wrongly leaves out"
        IllegalStateException e = thrown()
        e.message.contains('demo.$Unused1$Definition') || e.message.contains('demo.$HandlerB$Definition')

        when: "the context stops"
        verified.close()

        then: "both are in the report"
        Files.readString(report).contains('demo.$Unused1$Definition')
        Files.readString(report).contains('demo.$HandlerB$Definition')
    }

    void "with the verifier and the warm-up, a difference found while the warm-up loads the definition fails every lookup that reaches it"() {
        given: "a descriptor that says Unused1 is a Handler"
        Path wrong = copy(classes, temp.resolve('wrong'))
        edit(wrong.resolve(ENTRIES), 'demo.Unused1') { byte[] content ->
            DescriptorBytes.read(content).tap { annotations.put('demo.Handler', 8) }.toByteArray()
        }
        Path report = temp.resolve('report.txt')
        FixtureLoader loader = loader(wrong)
        ApplicationContext context = start(loader, [(VERIFY): 'true', (WARM_UP): 'all', (VERIFY + '.report'): report.toString()])

        expect: "the warm-up loaded it, and found the difference"
        ForkJoinPool.commonPoolParallelism > 1
        eventually(10) { loader.loaded(definition('demo.Unused1')) && Files.exists(report) && Files.readString(report).contains('demo.$Unused1$Definition') }

        when:
        Throwable first = failure { stereotyped(context, loader, 'demo.Handler') }
        Throwable second = failure { context.getBean(loader.loadClass('demo.Unused1')) }

        then:
        first instanceof IllegalStateException
        first.message.contains('demo.$Unused1$Definition')
        cause(second, IllegalStateException)?.message?.contains('demo.$Unused1$Definition')
    }

    void "a repeatable stereotype with a property expression is answered as eager mode answers it, also once its definition is loaded"() {
        given:
        FixtureLoader eagerLoader = loader(classes)
        ApplicationContext eager = start(eagerLoader, EAGER)
        FixtureLoader lazyLoader = loader(classes)
        ApplicationContext lazy = start(lazyLoader, LAZY_ONLY)

        expect: "before the definition is loaded"
        stereotyped(lazy, lazyLoader, 'demo.Tag') == stereotyped(eager, eagerLoader, 'demo.Tag')
        stereotypedByName(lazy, 'demo.Tag') == stereotypedByName(eager, 'demo.Tag')

        when: "a lookup that loads it"
        def tags = stereotyped(lazy, lazyLoader, 'demo.Tags')

        then: "and after"
        tags == stereotyped(eager, eagerLoader, 'demo.Tags')
        lazyLoader.loaded(definition('demo.TaggedWithExpr'))
        stereotyped(lazy, lazyLoader, 'demo.Tag') == stereotyped(eager, eagerLoader, 'demo.Tag')
        stereotypedByName(lazy, 'demo.Tag') == stereotypedByName(eager, 'demo.Tag')
    }

    void "a qualifier compared by its values matches a bean that leaves a member at its default, as eager mode, as the first lookup of a JVM (#mode)"() {
        given: "a JVM of its own, as the defaults of annotations are registered JVM-wide"
        String java = Path.of(System.getProperty('java.home'), 'bin', 'java').toString()
        String classPath = System.getProperty('java.class.path') + File.pathSeparator + classes
        List<String> command = [java, '--enable-preview'] + switches + ['-cp', classPath, LazyQualifierDefaultsMain.name]
        def process = new ProcessBuilder(command).redirectErrorStream(true).start()
        String output = process.inputStream.text

        expect:
        process.waitFor() == 0
        output.contains('ANSWER large=[demo.VanillaLarge] small=[demo.VanillaSmall]')

        where:
        mode                  | switches
        'eager'               | []
        'lazy'                | ['-D' + LAZY + '=true', '-D' + WARM_UP + '=false']
        'lazy, one processor' | ['-D' + LAZY + '=true', '-XX:ActiveProcessorCount=1']
    }

    void "a disabled bean of a type it is not exposed as is reported when that type is looked up, as in eager mode"() {
        given:
        FixtureLoader eagerLoader = loader(classes)
        ApplicationContext eager = start(eagerLoader, EAGER)
        FixtureLoader lazyLoader = loader(classes)
        ApplicationContext lazy = start(lazyLoader, LAZY_ONLY)

        when:
        String eagerMessage = failure { eager.getBean(eagerLoader.loadClass('demo.TypedDisabled')) }.message
        String lazyMessage = failure { lazy.getBean(lazyLoader.loadClass('demo.TypedDisabled')) }.message

        then:
        eagerMessage.contains('is disabled because')
        lazyMessage == eagerMessage
    }

    void "a stopped context that is still referenced keeps none of its lazy references"() {
        given:
        FixtureLoader loader = loader(classes)
        ApplicationContext context = start(loader, LAZY_ONLY)
        WeakReference<Object> reference = new WeakReference<>(heldReference(context, 'demo.$V6$Definition'))
        String held = reference.get()?.getClass()?.simpleName

        when:
        context.stop()

        then: "the context is still referenced here"
        held == 'LazyBeanDefinitionReference'
        context != null
        eventually(10) { System.gc(); reference.get() == null }
    }

    void "the warm-up loads the definitions the context starts with, on threads of its own, and the others only when asked to"() {
        given:
        FixtureLoader loader = loader(classes)
        ApplicationContext context = start(loader, [(LAZY): 'true'])

        expect: "it has finished"
        ForkJoinPool.commonPoolParallelism > 1
        eventually(10) { !Thread.allStackTraces.keySet().any { it.name.startsWith('micronaut-lazy-warm-up-') } }

        and: "the application has none of those, so none of its definitions was loaded"
        !loader.loaded('demo.$Unused5$Definition')
        !loader.loaded('demo.$HandlerA$Definition')
        context.getBean(loader.loadClass('demo.V8')) != null
    }

    void "the whole warm-up loads the definitions with no condition in the background, and never one whose conditions fail"() {
        given:
        FixtureLoader loader = loader(classes)
        ApplicationContext context = start(loader, [(LAZY): 'true', (WARM_UP): 'all'])

        expect:
        ForkJoinPool.commonPoolParallelism > 1
        eventually(10) { loader.loaded('demo.$Unused5$Definition') && loader.loaded('demo.$HandlerA$Definition') }
        !loader.loaded(definition('demo.NeedsGone'))
        !loader.loaded(definition('demo.OnSolaris'))
        !loader.loaded(definition('demo.NeedsProperty'))
        context.getBean(loader.loadClass('demo.V8')) != null
    }

    void "with the switch off, no class of the lazy mode is loaded and the application answers as before (#processors)"() {
        given:
        String java = Path.of(System.getProperty('java.home'), 'bin', 'java').toString()
        String classPath = System.getProperty('java.class.path') + File.pathSeparator + classes
        List<String> command = [java, '--enable-preview', '-verbose:class']
        if (processors != 'all') {
            command << '-XX:ActiveProcessorCount=' + processors
        }
        def process = new ProcessBuilder(command + ['-cp', classPath, LazyOffMain.name])
            .redirectErrorStream(true)
            .start()
        String output = process.inputStream.text
        boolean pool = processors == 'all' ? ForkJoinPool.commonPoolParallelism > 1 : Integer.parseInt(processors) > 2

        expect:
        process.waitFor() == 0
        output.contains('ANSWER v8=demo.V8 handlers=[demo.HandlerA, demo.HandlerB]')
        output.contains('[class,load] io.micronaut.context.DefaultBeanContext ')
        !(output =~ LAZY_CLASSES)

        and: "exactly the nested classes the code without lazy references loads"
        nested(output, 'io.micronaut.context.beans.DefaultBeanDefinitionService') == ['$1', '$2', '$2$1', '$3', '$3$1', '$BeanDefinitionProducer', '$Beans'] as Set
        nested(output, 'io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils') ==
            (['$1', '$CacheEntry', '$MicronautServiceCollector', '$RecursiveActionValuesCollector'] + (pool ? ['$ServiceInstanceLoader'] : [])) as Set

        where:
        processors << ['all', '1']
    }

    // --- the application

    private static Map<String, String> sources() {
        [
            'demo/Application.java'  : 'package demo; public class Application { }',
            'demo/Engine.java'       : 'package demo; public interface Engine { String name(); }',
            'demo/V6.java'           : '''package demo;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
@Singleton @Named("v6") @Order(5)
public class V6 implements Engine { public String name() { return "v6"; } }''',
            'demo/V8.java'           : '''package demo;
import io.micronaut.context.annotation.Primary;
import jakarta.inject.Singleton;
@Singleton @Primary
public class V8 implements Engine { public String name() { return "v8"; } }''',
            'demo/V12.java'          : '''package demo;
import jakarta.inject.Singleton;
@Singleton
public class V12 implements Engine { public String name() { return "v12"; } }''',
            'demo/V12Turbo.java'     : '''package demo;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
@Singleton @Replaces(V12.class)
public class V12Turbo implements Engine { public String name() { return "v12 turbo"; } }''',
            'demo/Handler.java'      : '''package demo;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
@Singleton
@Retention(RetentionPolicy.RUNTIME)
public @interface Handler { }''',
            'demo/HandlerA.java'     : 'package demo; @Handler public class HandlerA { }',
            'demo/HandlerB.java'     : 'package demo; @Handler public class HandlerB { }',
            'demo/Unused1.java'      : 'package demo; @jakarta.inject.Singleton public class Unused1 { }',
            'demo/Unused2.java'      : 'package demo; @jakarta.inject.Singleton public class Unused2 { }',
            'demo/Unused3.java'      : 'package demo; @jakarta.inject.Singleton public class Unused3 { }',
            'demo/Unused4.java'      : 'package demo; @jakarta.inject.Singleton public class Unused4 { }',
            'demo/Unused5.java'      : 'package demo; @jakarta.inject.Singleton public class Unused5 { }',
            'demo/Wheel.java'        : 'package demo; public class Wheel { }',
            'demo/Wheels.java'       : '''package demo;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
@Factory
public class Wheels { @Singleton @Named("front") public Wheel front() { return new Wheel(); } }''',
            'demo/Gone.java'         : 'package demo; public class Gone { }',
            'demo/NeedsGone.java'    : '''package demo;
import io.micronaut.context.annotation.Requires;
@jakarta.inject.Singleton @Requires(classes = Gone.class)
public class NeedsGone { }''',
            'demo/NeedsProperty.java': '''package demo;
import io.micronaut.context.annotation.Requires;
@jakarta.inject.Singleton @Requires(property = "demo.enabled", value = "true")
public class NeedsProperty { }''',
            'demo/NotInTest.java'    : '''package demo;
import io.micronaut.context.annotation.Requires;
@jakarta.inject.Singleton @Requires(notEnv = "lazytest")
public class NotInTest { }''',
            'demo/OnSolaris.java'    : '''package demo;
import io.micronaut.context.annotation.Requires;
@jakarta.inject.Singleton @Requires(os = Requires.Family.SOLARIS)
public class OnSolaris { }''',
            'demo/GoneBase.java'     : 'package demo; public abstract class GoneBase { }',
            'demo/Broken.java'       : 'package demo; @jakarta.inject.Singleton public class Broken extends GoneBase { }',
            'demo/X.java'            : 'package demo; public class X { }',
            'demo/NeedsX.java'       : '''package demo;
import io.micronaut.context.annotation.Requires;
@jakarta.inject.Singleton @Requires(beans = X.class)
public class NeedsX { }''',
            'demo/Writer.java'       : 'package demo; public interface Writer<T> { }',
            'demo/Foo.java'          : 'package demo; public class Foo { }',
            'demo/Bar.java'          : 'package demo; public class Bar { }',
            'demo/FooWriter.java'    : 'package demo; @jakarta.inject.Singleton public class FooWriter implements Writer<Foo> { }',
            'demo/BarWriter.java'    : 'package demo; @jakarta.inject.Singleton public class BarWriter implements Writer<Bar> { }',
            'demo/Counter.java'      : '''package demo;
import java.util.concurrent.atomic.AtomicInteger;
@jakarta.inject.Singleton
public class Counter {
    public static final AtomicInteger INSTANCES = new AtomicInteger();
    public Counter() { INSTANCES.incrementAndGet(); }
}''',
            'demo/Tag.java'          : '''package demo;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
@Repeatable(Tags.class) @Retention(RetentionPolicy.RUNTIME)
public @interface Tag { String value(); }''',
            'demo/Tags.java'         : '''package demo;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
@Retention(RetentionPolicy.RUNTIME)
public @interface Tags { Tag[] value(); }''',
            'demo/Tagged.java'       : 'package demo; @jakarta.inject.Singleton @Tag("c") public class Tagged { }',
            'demo/TaggedWithExpr.java': 'package demo; @jakarta.inject.Singleton @Tag("${demo.tag:a}") @Tag("b") public class TaggedWithExpr { }',
            'demo/Flavor.java'       : '''package demo;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
@jakarta.inject.Qualifier @Retention(RetentionPolicy.RUNTIME)
public @interface Flavor { String value(); String size() default "large"; }''',
            'demo/IceCream.java'     : 'package demo; public interface IceCream { }',
            'demo/VanillaLarge.java' : 'package demo; @jakarta.inject.Singleton @Flavor("vanilla") public class VanillaLarge implements IceCream { }',
            'demo/VanillaSmall.java' : 'package demo; @jakarta.inject.Singleton @Flavor(value = "vanilla", size = "small") public class VanillaSmall implements IceCream { }',
            'demo/Svc.java'          : 'package demo; public interface Svc { }',
            'demo/TypedDisabled.java': '''package demo;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Requires;
@jakarta.inject.Singleton @Bean(typed = Svc.class) @Requires(property = "demo.typed")
public class TypedDisabled implements Svc { }''',
            'demo/Boom.java'         : '''package demo;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
public final class Boom implements BeanDefinitionReference<Boom> {
    static {
        if (Boolean.parseBoolean("true")) {
            throw new IllegalStateException("boom");
        }
    }
    public String getBeanDefinitionName() { return Boom.class.getName(); }
    public BeanDefinition<Boom> load() { throw new UnsupportedOperationException(); }
    public boolean isPresent() { return true; }
    public Class<Boom> getBeanType() { return Boom.class; }
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) { return true; }
}''',
        ]
    }

    // --- helpers

    private static List<File> classpath(String property) {
        System.getProperty(property, '').split(File.pathSeparator).collect { new File(it) }
    }

    private static String definition(String beanType) {
        int dot = beanType.lastIndexOf('.')
        beanType.substring(0, dot + 1) + '$' + beanType.substring(dot + 1) + '$Definition'
    }

    private FixtureLoader loader(Path location) {
        def loader = new FixtureLoader([location.toUri().toURL()] as URL[], getClass().classLoader)
        closeables << loader
        return loader
    }

    /**
     * Starts a context with exactly the given switches, whatever the JVM runs with, as the test tasks may switch lazy
     * mode or its verification on for every test.
     */
    private ApplicationContext start(ClassLoader loader, Map<String, String> switches) {
        Map<String, String> previous = [LAZY, VERIFY, WARM_UP, VERIFY + '.report'].collectEntries { [(it): System.getProperty(it)] }
        previous.keySet().each { System.clearProperty(it) }
        switches.each { key, value -> System.setProperty(key, value) }
        try {
            ApplicationContext context = ApplicationContext.builder()
                .classLoader(loader)
                .deduceEnvironment(false)
                .environments('lazytest')
                .start()
            closeables << context
            return context
        } finally {
            previous.each { key, value ->
                if (value == null) {
                    System.clearProperty(key)
                } else {
                    System.setProperty(key, value)
                }
            }
        }
    }

    /**
     * @return The definitions of the application the loader has loaded
     */
    private static Set<String> loaded(FixtureLoader loader, Path location) {
        Set<String> names = definitions(location)
        names.findAll { loader.loaded(it) } as Set
    }

    private static Set<String> definitions(Path location) {
        if (Files.isDirectory(location)) {
            return Files.list(location.resolve(ENTRIES)).withCloseable { it.map { it.fileName.toString() }.filter { it.startsWith('demo.') }.toList() } as Set
        }
        new java.util.zip.ZipFile(location.toFile()).withCloseable { zip ->
            zip.entries().toList()*.name.findAll { it.startsWith(ENTRIES + '/demo.') }.collect { it.substring(ENTRIES.length() + 1) } as Set
        }
    }

    // statically compiled: Groovy would introspect the class of every definition, some of which name missing classes

    @groovy.transform.CompileStatic
    private static List<String> engines(ApplicationContext context, ClassLoader loader) {
        context.getBeansOfType((Class<Object>) loader.loadClass('demo.Engine')).collect { Object bean -> bean.getClass().getName() }
    }

    @groovy.transform.CompileStatic
    private static List<String> stereotyped(ApplicationContext context, ClassLoader loader, String stereotype) {
        names(context.getBeanDefinitions(Qualifiers.byStereotype((Class) loader.loadClass(stereotype)))).sort()
    }

    private static Object heldReference(ApplicationContext context, String name) {
        context.beanDefinitionProvider.beanReferences.find { it.beanDefinitionName == name }
    }

    @groovy.transform.CompileStatic
    private static List<String> stereotypedByName(ApplicationContext context, String stereotype) {
        names(context.getBeanDefinitions(Qualifiers.byStereotype(stereotype))).findAll { String name -> name.startsWith('demo.') }.sort()
    }

    /**
     * @return The names of the nested classes of a class that a {@code -verbose:class} output says were loaded
     */
    private static Set<String> nested(String output, String outer) {
        def matcher = output =~ ('\\[class,load\\] ' + java.util.regex.Pattern.quote(outer) + '(\\$[^ ]+) ')
        matcher.collect { (String) ((List) it)[1] } as Set
    }

    @groovy.transform.CompileStatic
    private static List<String> singletons(ApplicationContext context) {
        names(context.getBeanDefinitions(Qualifiers.byStereotype(Singleton))).findAll { String name -> name.startsWith('demo.') }.sort()
    }

    @groovy.transform.CompileStatic
    private static List<String> names(Collection<BeanDefinition<Object>> definitions) {
        List<String> names = []
        for (BeanDefinition<Object> definition : definitions) {
            names.add(definition.getBeanType().getName())
        }
        return names
    }

    @groovy.transform.CompileStatic
    private static List<String> references(ApplicationContext context) {
        List<String> names = []
        for (BeanDefinitionReference<Object> reference : context.getBeanDefinitionReferences()) {
            if (reference.getBeanDefinitionName().startsWith('demo.')) {
                names.add(reference.getBeanDefinitionName())
            }
        }
        return names.sort()
    }

    @groovy.transform.CompileStatic
    private static Map<String, Set<List<String>>> disabled(ApplicationContext context, List<String> types) {
        Map<String, Set<List<String>>> reasons = [:]
        for (def bean : context.disabledBeans) {
            String type = bean.type().type.name
            if (types.contains(type)) {
                reasons.computeIfAbsent(type) { [] as Set }.add(bean.reasons())
            }
        }
        return reasons
    }

    private static String outcome(Closure<?> lookup) {
        try {
            return lookup.call().getClass().name
        } catch (Throwable t) {
            return t.getClass().name
        }
    }

    private static Throwable failure(Closure<?> lookup) {
        try {
            lookup.call()
            return null
        } catch (Throwable t) {
            return t
        }
    }

    private static <T extends Throwable> T cause(Throwable thrown, Class<T> type) {
        for (Throwable t = thrown; t != null; t = t.cause) {
            if (type.isInstance(t)) {
                return type.cast(t)
            }
        }
        return null
    }

    private static boolean eventually(int seconds, Closure<Boolean> condition) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (System.nanoTime() < end) {
            if (condition.call()) {
                return true
            }
            Thread.sleep(20)
        }
        return condition.call()
    }

    private static void edit(Path entries, String beanType, Closure<byte[]> change) {
        Path entry = entries.resolve(definition(beanType))
        Files.write(entry, change.call(Files.readAllBytes(entry)))
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length)
        System.arraycopy(second, 0, both, first.length, second.length)
        return both
    }

    /**
     * @return The content with the length of its header set to what follows the header
     */
    private static byte[] lengthFixed(byte[] content) {
        def buffer = java.nio.ByteBuffer.wrap(content.clone())
        buffer.putInt(6, content.length - DescriptorBytes.HEADER_LENGTH)
        return buffer.array()
    }

    private static Path copy(Path from, Path to) {
        Files.walk(from).withCloseable { paths ->
            paths.filter(Files::isRegularFile).forEach { Path file ->
                Path target = to.resolve(from.relativize(file).toString())
                Files.createDirectories(target.parent)
                Files.copy(file, target)
            }
        }
        return to
    }

    private static Path jar(Path from, Path to) {
        new JarOutputStream(Files.newOutputStream(to)).withCloseable { out ->
            Files.walk(from).withCloseable { paths ->
                paths.filter { it != from }.sorted().forEach { Path file ->
                    String name = from.relativize(file).toString().replace(File.separatorChar, '/' as char)
                    if (Files.isDirectory(file)) {
                        out.putNextEntry(new JarEntry(name + '/'))
                    } else {
                        out.putNextEntry(new JarEntry(name))
                        out.write(Files.readAllBytes(file))
                    }
                    out.closeEntry()
                }
            }
        }
        return to
    }

    /**
     * A loader of the application that tells which of its classes it has loaded.
     */
    static class FixtureLoader extends URLClassLoader {
        FixtureLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent)
        }

        boolean loaded(String name) {
            findLoadedClass(name) != null
        }
    }
}
