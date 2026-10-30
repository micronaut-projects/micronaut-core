package io.micronaut.context.env

import io.micronaut.context.env.RefreshTestSupport.Gate
import io.micronaut.context.env.RefreshTestSupport.Parking
import io.micronaut.context.env.RefreshTestSupport.ParkingEnvironment
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import static io.micronaut.context.env.RefreshTestSupport.WAIT_SECONDS
import static io.micronaut.context.env.RefreshTestSupport.daemon
import static io.micronaut.context.env.RefreshTestSupport.parkIn
import static io.micronaut.context.env.RefreshTestSupport.release

/**
 * The state an environment ends in when another thread calls stop(), start(), refresh() or
 * addPropertySource while it refreshes, for each order of those calls, and when code that the
 * refresh runs stops the environment or fails.
 *
 * <p>The expected state is what 4.10.x without the active refresh ends in: there, each call waits
 * for the refresh and then runs, in the order it was made. This spec runs unchanged on that
 * version as well, where it only checks the end state. With the active refresh, no call waits,
 * and the end state is the same, except where {@code differs} names the one intended difference,
 * {@code stop}: the last stop(), start() or refresh() made by the end of the read is a stop(), so
 * the refresh publishes nothing, and the stopped environment answers with what it had before:
 * {@code old} rather than {@code new}.</p>
 */
@Timeout(120)
class CallsDuringRefreshScenarioSpec extends Specification {

    /**
     * Whether this version has the active refresh, where calls from other threads do not wait.
     */
    @Shared
    boolean activeRefresh = DefaultEnvironment.declaredMethods.any { it.name == 'beginStartOrStop' }

    void "#calls from another thread #point, the refresh #code: running #running, app.value #value, p #p"() {
        given: "a value that changes from 'old' to 'new', read in the order data, code, gate"
        ParkingEnvironment env = new ParkingEnvironment()
        Map<String, Object> data = ['app.value': 'old', 'myApp.fooBar': 'old']
        env.addPropertySource(new MapPropertySource('data', data))
        CodeTheRefreshRuns codeSource = new CodeTheRefreshRuns(env, code)
        if (code != 'runs no code') {
            env.addPropertySource(codeSource)
        }
        Gate gate = new Gate()
        env.addPropertySource(gate)
        env.start()
        Added added = new Added(env)
        Throwable refreshFailure = null

        when: "another thread makes the calls while the refresh is parked"
        data.put('app.value', 'new')
        data.put('myApp.fooBar', 'new')
        codeSource.armed.set(true)
        env.failing.set(code == 'fails once it has read')
        Parking parking = point == 'before the refresh stops' ? env.stopping
                : point == 'while the refresh reads' ? gate
                : env.started
        Thread refresher = parkIn(parking) {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            }
        }
        Thread caller = daemon('caller') {
            calls.split(', ').findAll { it != 'no calls' }.each { make(env, it, added) }
        }
        boolean returnedWhileParked = true
        if (activeRefresh) {
            caller.join(WAIT_SECONDS * 1000L)
            returnedWhileParked = !caller.alive
        } else {
            waitUntilWaitingOrDone(caller)
        }
        release(parking, refresher)
        caller.join(3 * WAIT_SECONDS * 1000L)
        boolean asWithoutActiveRefresh = !activeRefresh && differs != 'nothing'

        then: "no call waited for the refresh, where calls do not wait"
        returnedWhileParked
        !caller.alive

        and: "the environment ends as it did when the calls waited, except for the intended difference"
        (refreshFailure != null) == (code in ['stops and then fails', 'fails once it has read'])
        env.running == running
        env.getProperty('app.value', String).orElse(null) == (asWithoutActiveRefresh ? 'new' : value)
        env.getProperty('my-app.foo-bar', String).orElse(null) == (asWithoutActiveRefresh && running ? 'new' : generated)
        p == 'not added' || env.propertySources.any { it.is(added) }
        env.getProperty('p.key', String).isPresent() == (p == 'processed')

        cleanup:
        parking.release.countDown()
        env.failing.set(false)
        env.stop()

        where:
        point                       | calls                           | code                    || running | value | generated | p             | differs
        'once the refresh has read' | 'stop'                          | 'runs no code'          || false   | 'old' | null      | 'not added'   | 'stop'
        'once the refresh has read' | 'start'                         | 'runs no code'          || true    | 'new' | 'new'     | 'not added'   | 'nothing'
        'once the refresh has read' | 'add'                           | 'runs no code'          || true    | 'new' | 'new'     | 'processed'   | 'nothing'
        'once the refresh has read' | 'refresh'                       | 'runs no code'          || true    | 'new' | 'new'     | 'not added'   | 'nothing'
        'once the refresh has read' | 'stop, start'                   | 'runs no code'          || true    | 'new' | 'new'     | 'not added'   | 'nothing'
        'once the refresh has read' | 'start, stop'                   | 'runs no code'          || false   | 'old' | null      | 'not added'   | 'stop'
        'once the refresh has read' | 'add, stop'                     | 'runs no code'          || false   | 'old' | null      | 'processed'   | 'stop'
        'once the refresh has read' | 'stop, add'                     | 'runs no code'          || false   | 'old' | null      | 'registered'  | 'stop'
        'once the refresh has read' | 'stop, refresh'                 | 'runs no code'          || true    | 'new' | 'new'     | 'not added'   | 'nothing'
        'once the refresh has read' | 'refresh, stop'                 | 'runs no code'          || false   | 'old' | null      | 'not added'   | 'stop'
        'once the refresh has read' | 'add that stops, start'         | 'runs no code'          || true    | 'new' | 'new'     | 'processed'   | 'nothing'
        'once the refresh has read' | 'start, add that stops'         | 'runs no code'          || false   | 'new' | null      | 'processed'   | 'nothing'
        'once the refresh has read' | 'stop, start, add that stops'   | 'runs no code'          || false   | 'new' | null      | 'processed'   | 'nothing'
        'before the refresh stops'  | 'stop, start, add that stops'   | 'runs no code'          || false   | 'new' | null      | 'processed'   | 'nothing'
        'once the refresh has read' | 'add'                           | 'fails once it has read'|| true    | 'old' | 'old'     | 'processed'   | 'nothing'
        'once the refresh has read' | 'refresh'                       | 'fails once it has read'|| true    | 'new' | 'new'     | 'not added'   | 'nothing'
        'before the refresh stops'  | 'stop'                          | 'runs no code'          || false   | 'old' | null      | 'not added'   | 'stop'
        'before the refresh stops'  | 'stop, start'                   | 'runs no code'          || true    | 'new' | 'new'     | 'not added'   | 'nothing'
        'before the refresh stops'  | 'refresh'                       | 'runs no code'          || true    | 'new' | 'new'     | 'not added'   | 'nothing'
        'before the refresh stops'  | 'start'                         | 'stops'                 || true    | 'new' | 'new'     | 'not added'   | 'nothing'
        'before the refresh stops'  | 'stop'                          | 'stops and then fails'  || false   | 'old' | null      | 'not added'   | 'nothing'
        'before the refresh stops'  | 'start'                         | 'stops and then fails'  || true    | 'new' | 'new'     | 'not added'   | 'nothing'
        'while the refresh reads'   | 'no calls'                      | 'stops'                 || false   | 'new' | 'new'     | 'not added'   | 'nothing'
        'while the refresh reads'   | 'start'                         | 'stops'                 || true    | 'new' | 'new'     | 'not added'   | 'nothing'
        'while the refresh reads'   | 'stop, start'                   | 'runs no code'          || true    | 'new' | 'new'     | 'not added'   | 'nothing'
    }

    void "a stop, a start and then an add from another thread once the refresh has read: the added property source overrides one of a higher order, as it is processed after the start"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(PropertySource.of('higher', ['app.value': 'higher'], 100))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['app.value': 'lower'], 0)

        when:
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread caller = daemon('caller') {
            env.stop()
            env.start()
            env.addPropertySource(lower)
        }
        boolean returnedWhileParked = returnedWhileParked(caller)
        release(env.started, refresher)
        caller.join(3 * WAIT_SECONDS * 1000L)

        then:
        returnedWhileParked
        !caller.alive
        env.running
        env.getProperty('app.value', String).get() == 'lower'

        cleanup:
        env.started.release.countDown()
        env.stop()
    }

    void "two adds from another thread once the refresh has read, of which the first stops the environment when it is processed: the second only registers its property source"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(new MapPropertySource('data', ['app.value': 'old']))
        env.start()
        Added first = new Added(env)
        first.stops.set(true)
        PropertySource second = PropertySource.of('second', ['second.key': 'second'])

        when:
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread caller = daemon('caller') {
            env.addPropertySource(first)
            env.addPropertySource(second)
        }
        boolean returnedWhileParked = returnedWhileParked(caller)
        release(env.started, refresher)
        caller.join(3 * WAIT_SECONDS * 1000L)

        then:
        returnedWhileParked
        !caller.alive
        !env.running
        env.getProperty('p.key', String).isPresent()
        env.propertySources.any { it.is(second) }
        !env.getProperty('second.key', String).isPresent()

        cleanup:
        env.started.release.countDown()
        env.stop()
    }

    void "after #calls from another thread once the refresh has read, the refresh #code: the added property source overrides one of a higher order"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(PropertySource.of('higher', ['app.value': 'higher'], 100))
        CodeTheRefreshRuns codeSource = new CodeTheRefreshRuns(env, code)
        if (code != 'runs no code') {
            env.addPropertySource(codeSource)
        }
        env.start()
        PropertySource lower = PropertySource.of('lower', ['app.value': 'lower'], 0)
        RefreshesWhenProcessed refreshing = new RefreshesWhenProcessed(env)

        when:
        codeSource.armed.set(true)
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread caller = daemon('caller') {
            if (calls == 'start, add') {
                env.start()
            } else {
                env.addPropertySource(refreshing)
            }
            env.addPropertySource(lower)
        }
        boolean returnedWhileParked = returnedWhileParked(caller)
        release(env.started, refresher)
        caller.join(3 * WAIT_SECONDS * 1000L)

        then:
        returnedWhileParked
        !caller.alive
        env.running
        env.getProperty('app.value', String).get() == 'lower'

        cleanup:
        env.started.release.countDown()
        env.stop()

        where:
        calls                                         | code
        'start, add'                                  | 'stops'
        'an add that refreshes when processed, an add' | 'runs no code'
    }

    void "#calls from another thread once the refresh has read: running #running, registered #registered, named.value #namedValue, first.only #firstOnly"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(new MapPropertySource('data', ['app.value': 'old']))
        env.start()
        PropertySource first = PropertySource.of('named', ['named.value': 'first', 'first.only': 'first'])
        PropertySource second = PropertySource.of('named', ['named.value': 'second'])

        when:
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread caller = daemon('caller') {
            calls.split(', ').each { String call ->
                switch (call) {
                    case 'stop':
                        env.stop()
                        break
                    case 'add first':
                        env.addPropertySource(first)
                        break
                    case 'add second by the same name':
                        env.addPropertySource(second)
                        break
                    case 'remove first':
                        env.removePropertySource(first)
                        break
                    default:
                        throw new IllegalArgumentException(call)
                }
            }
        }
        boolean returnedWhileParked = returnedWhileParked(caller)
        release(env.started, refresher)
        caller.join(3 * WAIT_SECONDS * 1000L)
        PropertySource named = env.propertySources.find { it.name == 'named' }

        then:
        returnedWhileParked
        !caller.alive
        env.running == running
        (named == null ? 'none' : named.is(first) ? 'first' : 'second') == registered
        env.getProperty('named.value', String).orElse(null) == namedValue
        env.getProperty('first.only', String).orElse(null) == firstOnly

        cleanup:
        env.started.release.countDown()
        env.stop()

        where:
        calls                                    || running | registered | namedValue | firstOnly
        'add first, add second by the same name' || true    | 'second'   | 'second'   | 'first'
        'add first, remove first'                || true    | 'none'     | 'first'    | 'first'
        'stop, add first, remove first'          || false   | 'none'     | null       | null
    }

    void "a refresh and then #mutation from another thread once the refresh has read: the change comes after the refresh, as when the calls waited"() {
        given: "a property source whose value tells whether the change was made when it was read"
        ParkingEnvironment env = new ParkingEnvironment()
        def configuration = io.micronaut.inject.BeanConfiguration.disabled('review.fixture')
        Closure<String> observed = mutation == 'addPackage'
                ? { env.packages.contains('review.late') ? 'after' : 'before' }
                : { env.isActive(configuration) ? 'before' : 'after' }
        env.addPropertySource(new ObservesEnvironment(observed))
        env.start()

        when:
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread caller = daemon('caller') {
            env.refresh()
            if (mutation == 'addPackage') {
                env.addPackage('review.late')
            } else if (mutation == 'addConfigurationExcludes') {
                env.addConfigurationExcludes('review.fixture')
            } else {
                env.addConfigurationIncludes('review.other')
            }
        }
        boolean returnedWhileParked = returnedWhileParked(caller)
        release(env.started, refresher)
        caller.join(3 * WAIT_SECONDS * 1000L)

        then:
        returnedWhileParked
        !caller.alive
        env.running
        observed.call() == 'after'
        env.getProperty('observed.value', String).get() == 'before'

        cleanup:
        env.started.release.countDown()
        env.stop()

        where:
        mutation << ['addPackage', 'addConfigurationExcludes', 'addConfigurationIncludes']
    }

    void "a start and then an add from another thread once the refresh has read: an override of processPropertySource decides, as for any add"() {
        given:
        FilteringEnvironment env = new FilteringEnvironment()
        env.start()
        PropertySource filtered = PropertySource.of('filtered', ['filtered.key': 'must-not-be-processed'])

        when:
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread caller = daemon('caller') {
            env.start()
            env.addPropertySource(filtered)
        }
        boolean returnedWhileParked = returnedWhileParked(caller)
        release(env.started, refresher)
        caller.join(3 * WAIT_SECONDS * 1000L)

        then:
        returnedWhileParked
        !caller.alive
        env.running
        env.propertySources.any { it.is(filtered) }
        !env.getProperty('filtered.key', String).isPresent()

        cleanup:
        env.started.release.countDown()
        env.stop()
    }

    void "a start, an add that fails, and a stop from another thread once the refresh has read: the stop is still carried out"() {
        given:
        FailingRunningEnvironment env = new FailingRunningEnvironment()
        env.start()
        PropertySource source = PropertySource.of('added', ['added.key': 'added'])
        Throwable refreshFailure = null

        when:
        Thread refresher = parkIn(env.started) {
            try {
                env.refresh()
            } catch (Throwable failure) {
                refreshFailure = failure
            }
        }
        env.failRunningCheck.set(true)
        Thread caller = daemon('caller') {
            env.start()
            try {
                env.addPropertySource(source)
            } catch (IllegalStateException expected) {
                // thrown to the caller when the calls wait; logged when the refresh carries the add out
            }
            env.stop()
        }
        boolean returnedWhileParked = returnedWhileParked(caller)
        release(env.started, refresher)
        caller.join(3 * WAIT_SECONDS * 1000L)

        then:
        returnedWhileParked
        !caller.alive
        !env.running
        refreshFailure == null

        cleanup:
        env.started.release.countDown()
        env.failRunningCheck.set(false)
        env.stop()
    }

    void "a start and then a close from another thread, after code the refresh runs stopped the environment: the close comes after the start, as when the calls waited"() {
        given: "an expression resolver that tells whether it was closed when a property was read"
        ParkingEnvironment env = new ParkingEnvironment()
        ClosingResolver resolver = new ClosingResolver()
        // as PropertySourcePropertyResolverSpec sets expression resolvers
        env.placeholderResolver.@expressionResolvers = [resolver]
        env.addPropertySource(new ObservesEnvironment({
            env.placeholderResolver.resolveRequiredPlaceholders('${review.resource}')
        }))
        CodeTheRefreshRuns stopping = new CodeTheRefreshRuns(env, 'stops')
        env.addPropertySource(stopping)
        env.start()

        when:
        stopping.armed.set(true)
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread caller = daemon('caller') {
            env.start()
            env.close()
        }
        boolean returnedWhileParked = returnedWhileParked(caller)
        release(env.started, refresher)
        caller.join(3 * WAIT_SECONDS * 1000L)

        then:
        returnedWhileParked
        !caller.alive
        !env.running
        resolver.closed.get()
        env.getProperty('observed.value', String).get() == 'open'

        cleanup:
        env.started.release.countDown()
        env.stop()
    }

    void "a refresh and then an added converter from another thread once the refresh has read: the converter comes after the refresh, as when the calls waited"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(new ObservesEnvironment({
            env.canConvert(ConvertedFrom, ConvertedTo) ? 'after' : 'before'
        }))
        env.start()

        when:
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread caller = daemon('caller') {
            env.refresh()
            env.addConverter(ConvertedFrom, ConvertedTo, { new ConvertedTo() } as java.util.function.Function<ConvertedFrom, ConvertedTo>)
        }
        boolean returnedWhileParked = returnedWhileParked(caller)
        release(env.started, refresher)
        caller.join(3 * WAIT_SECONDS * 1000L)

        then:
        returnedWhileParked
        !caller.alive
        env.running
        env.canConvert(ConvertedFrom, ConvertedTo)
        env.getProperty('observed.value', String).get() == 'before'

        cleanup:
        env.started.release.countDown()
        env.stop()
    }

    static class ConvertedFrom {
    }

    static class ConvertedTo {
    }

    /**
     * An expression resolver for ${review.resource}, which resolves to whether it is closed.
     */
    static class ClosingResolver implements PropertyExpressionResolver, AutoCloseable {
        final AtomicBoolean closed = new AtomicBoolean()

        @Override
        <T> Optional<T> resolve(io.micronaut.core.value.PropertyResolver propertyResolver,
                                io.micronaut.core.convert.ConversionService conversionService,
                                String expression, Class<T> requiredType) {
            return expression == 'review.resource'
                    ? conversionService.convert(closed.get() ? 'closed' : 'open', requiredType)
                    : Optional.empty()
        }

        @Override
        void close() {
            closed.set(true)
        }
    }

    /**
     * A property source with one property, whose value the given closure computes when it is read.
     */
    static class ObservesEnvironment implements PropertySource {
        private final Closure<String> reader

        ObservesEnvironment(Closure<String> reader) {
            this.reader = reader
        }

        @Override
        String getName() { 'observes-environment' }

        @Override
        Object get(String key) { this.@reader.call() }

        @Override
        Iterator<String> iterator() { ['observed.value'].iterator() }
    }

    /**
     * An environment that does not process the property source named 'filtered'.
     */
    static class FilteringEnvironment extends ParkingEnvironment {
        @Override
        protected void processPropertySource(PropertySource source, PropertySource.PropertyConvention convention) {
            if (source.name != 'filtered') {
                super.processPropertySource(source, convention)
            }
        }
    }

    /**
     * An environment whose isRunning(), once armed, throws the next time it is called.
     */
    static class FailingRunningEnvironment extends ParkingEnvironment {
        final AtomicBoolean failRunningCheck = new AtomicBoolean()

        @Override
        boolean isRunning() {
            // the field is not set yet while the constructor runs
            if (failRunningCheck != null && failRunningCheck.compareAndSet(true, false)) {
                throw new IllegalStateException('running check failed')
            }
            return super.isRunning()
        }
    }

    /**
     * With the active refresh, whether the calls returned while the refresh is parked. Without it,
     * waits until the first call waits for the refresh, or the calls are done, and answers true.
     */
    private boolean returnedWhileParked(Thread caller) {
        if (activeRefresh) {
            caller.join(WAIT_SECONDS * 1000L)
            return !caller.alive
        }
        waitUntilWaitingOrDone(caller)
        return true
    }

    private static void make(Environment env, String call, Added added) {
        switch (call) {
            case 'stop':
                env.stop()
                break
            case 'start':
                env.start()
                break
            case 'refresh':
                env.refresh()
                break
            case 'add':
                env.addPropertySource(added)
                break
            case 'add that stops':
                added.stops.set(true)
                env.addPropertySource(added)
                break
            default:
                throw new IllegalArgumentException(call)
        }
    }

    /**
     * Without the active refresh, the first call waits for the refresh: waits until it does, or until
     * the calls are done, such as an add that only registers its property source.
     */
    private static void waitUntilWaitingOrDone(Thread caller) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (caller.alive && !(caller.state in [Thread.State.BLOCKED, Thread.State.WAITING, Thread.State.TIMED_WAITING])) {
            assert System.nanoTime() < deadline: 'the call neither waited nor returned'
            Thread.sleep(5)
        }
    }

    /**
     * A property source read before the others, whose code, once armed, does something the first
     * time the refreshing thread reads it.
     */
    static class CodeTheRefreshRuns implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        private final Environment env
        private final String what

        CodeTheRefreshRuns(Environment env, String what) {
            this.env = env
            this.what = what
        }

        @Override
        String getName() { 'code' }

        @Override
        int getOrder() { -100 }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (Thread.currentThread().name == 'refresher' && armed.compareAndSet(true, false)) {
                if (what in ['stops', 'stops and then fails']) {
                    env.stop()
                }
                if (what == 'stops and then fails') {
                    throw new IllegalStateException('failed once it stopped the environment')
                }
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * A property source another thread adds, which refreshes the environment the first time it is
     * processed.
     */
    static class RefreshesWhenProcessed implements PropertySource {
        private final AtomicBoolean refreshed = new AtomicBoolean()
        private final Environment env

        RefreshesWhenProcessed(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'refreshes' }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (refreshed.compareAndSet(false, true)) {
                env.refresh()
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * The property source another thread adds, which, once asked to, stops the environment the
     * first time it is processed.
     */
    static class Added implements PropertySource {
        final AtomicBoolean stops = new AtomicBoolean()
        private final Environment env

        Added(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'p' }

        @Override
        Object get(String key) { key == 'p.key' ? 'p' : null }

        @Override
        Iterator<String> iterator() {
            if (stops.compareAndSet(true, false)) {
                env.stop()
            }
            return ['p.key'].iterator()
        }
    }
}
