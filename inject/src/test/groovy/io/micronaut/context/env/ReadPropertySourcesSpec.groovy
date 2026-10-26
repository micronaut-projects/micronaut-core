package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.io.ResourceLoader
import io.micronaut.core.order.Ordered
import io.micronaut.core.util.StringUtils
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * A start or a refresh reading the property sources while other threads refresh, stop, start or
 * add property sources. A refresh reads them into a state of its own and holds no lock of the
 * environment while their code runs; one thread reads them at a time, and a refresh waits for
 * another one at most two seconds. A stop never waits: a read in progress then publishes nothing.
 * The threads are sequenced with latches, and every wait is bounded.
 */
@Timeout(90)
class ReadPropertySourcesSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/14141")
    void "stop() from another thread during a refresh does not wait for it, holding a lock the refresh needs, and the refresh publishes nothing"() {
        given:
        ReentrantLock appLock = new ReentrantLock()
        ParkingEnvironment env = new ParkingEnvironment()
        NeedsLock needsLock = new NeedsLock(appLock)
        env.addPropertySource(needsLock)
        env.addPropertySource(new MapPropertySource('data', ['myApp.fooBar': 'value']))
        env.start()
        Map<String, Object> diff = null

        when: "the refresh is parked before it reads, and another thread stops the environment holding the lock"
        needsLock.armed.set(true)
        Thread refresher = parkIn(env.stopping) { diff = env.refreshAndDiff() }
        long stopMillis = -1
        Thread stopper = daemon('stopper') {
            appLock.lock()
            try {
                long start = System.nanoTime()
                env.stop()
                stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            } finally {
                appLock.unlock()
            }
        }
        stopper.join(WAIT_SECONDS * 1000L)
        boolean stopperWaited = stopper.alive
        release(env.stopping, refresher)

        then: "the stop returns at once, the refresh gets the lock, and the environment ends stopped"
        !stopperWaited
        stopMillis >= 0 && stopMillis < 1000
        !needsLock.timedOut
        diff == [:]
        !env.running
        !env.getProperty('my-app.foo-bar', String).isPresent()

        when: "it is started again"
        env.start()

        then:
        env.running
        env.getProperty('my-app.foo-bar', String).get() == 'value'

        cleanup:
        env.stopping.release.countDown()
        env.stop()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/14141")
    void "start() from another thread during a refresh does not wait for it, holding a lock the refresh needs"() {
        given:
        ReentrantLock appLock = new ReentrantLock()
        ParkingEnvironment env = new ParkingEnvironment()
        NeedsLock needsLock = new NeedsLock(appLock)
        env.addPropertySource(needsLock)
        env.start()

        when:
        needsLock.armed.set(true)
        Thread refresher = parkIn(env.stopping) { env.refresh() }
        Thread starter = daemon('starter') {
            appLock.lock()
            try {
                env.start()
            } finally {
                appLock.unlock()
            }
        }
        starter.join(WAIT_SECONDS * 1000L)
        boolean starterWaited = starter.alive
        release(env.stopping, refresher)

        then:
        !starterWaited
        !needsLock.timedOut
        env.running

        cleanup:
        env.stopping.release.countDown()
        env.stop()
    }

    void "a start after stop() during a refresh does not wait for the refresh, which publishes nothing over what the start read"() {
        given:
        Map<String, Object> data = ['app.value': 'old']
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()

        when: "a refresh is parked once it has read the property sources, and the environment is stopped and started meanwhile"
        Thread refresher = parkIn(env.publishing) { env.refresh() }
        Thread restarter = daemon('restarter') {
            env.stop()
            data.put('app.value', 'new')
            env.start()
        }
        restarter.join(WAIT_SECONDS * 1000L)
        boolean restarterWaited = restarter.alive
        String afterRestart = env.getProperty('app.value', String).orElse(null)
        release(env.publishing, refresher)

        then:
        !restarterWaited
        afterRestart == 'new'
        env.running
        env.getProperty('app.value', String).get() == 'new'

        cleanup:
        env.publishing.release.countDown()
        env.stop()
    }

    void "a refresh that finds another refresh reading, which needs a lock it holds, returns within about two seconds, and the other reads once more for it"() {
        given:
        ReentrantLock appLock = new ReentrantLock()
        Map<String, Object> data = ['app.value': 'old']
        ParkingEnvironment env = new ParkingEnvironment()
        NeedsLock needsLock = new NeedsLock(appLock)
        env.addPropertySource(needsLock)
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()
        Map<String, Object> diff = null

        when: "a refresh is parked before it reads, and another thread holding the lock refreshes after a change"
        Thread first = parkIn(env.stopping) { env.refresh() }
        data.put('app.value', 'new')
        needsLock.armed.set(true)
        long secondMillis = -1
        Thread second = daemon('second') {
            appLock.lock()
            try {
                long start = System.nanoTime()
                diff = env.refreshAndDiff()
                secondMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            } finally {
                appLock.unlock()
            }
        }
        second.join(WAIT_SECONDS * 1000L)
        boolean secondWaited = second.alive
        release(env.stopping, first)

        then: "the second leaves its read to the first, which gets the lock, completes, and applies the change"
        !secondWaited
        secondMillis >= 1500 && secondMillis < 5000
        diff == [:]
        !needsLock.timedOut
        env.getProperty('app.value', String).get() == 'new'

        cleanup:
        env.stopping.release.countDown()
        env.stop()
    }

    void "a property source that refreshes the environment whenever it is read makes the read end after one more read: #how"() {
        given:
        AtomicInteger reads = new AtomicInteger()
        AtomicBoolean armed = new AtomicBoolean()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new PropertySource() {
            @Override
            String getName() { 'refreshing' }

            @Override
            Object get(String key) { key == 'refreshing.key' ? 'value' : null }

            @Override
            Iterator<String> iterator() {
                if (armed.get()) {
                    reads.incrementAndGet()
                    env.refresh()
                }
                return ['refreshing.key'].iterator()
            }
        })
        if (refresh) {
            env.start()
        }

        when:
        armed.set(true)
        refresh ? env.refresh() : env.start()

        then:
        reads.get() == 2
        env.getProperty('refreshing.key', String).get() == 'value'

        cleanup:
        env.stop()

        where:
        how       | refresh
        'start'   | false
        'refresh' | true
    }

    void "a refresh made while a start reads the property sources leaves every key in place once seen, and is read once the start is done"() {
        given: "a property source read after the first that parks the start once armed"
        Map<String, Object> late = ['late.key': 'old']
        Gate gate = new Gate(20)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('early', ['early.key': 'early'], 10))
        env.addPropertySource(gate)
        env.addPropertySource(new MapPropertySource('late', late) {
            @Override
            int getOrder() { 30 }
        })
        AtomicBoolean polling = new AtomicBoolean(true)
        AtomicBoolean lost = new AtomicBoolean()
        Thread poller = daemon('poller') {
            boolean seen = false
            while (polling.get()) {
                boolean present = env.getProperty('early.key', String).isPresent()
                if (seen && !present) {
                    lost.set(true)
                }
                seen |= present
                Thread.yield()
            }
        }

        when: "the start is parked after the first property source, and another thread refreshes after a change"
        gate.arm()
        Thread starter = daemon('starter') { env.start() }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        late.put('late.key', 'new')
        Thread refresher = daemon('refresher') { env.refresh() }
        refresher.join(WAIT_SECONDS * 1000L)
        boolean refresherWaited = refresher.alive
        gate.release.countDown()
        starter.join(WAIT_SECONDS * 1000L)
        polling.set(false)
        poller.join(WAIT_SECONDS * 1000L)

        then:
        !refresherWaited
        !starter.alive
        !lost.get()
        env.getProperty('early.key', String).get() == 'early'
        env.getProperty('late.key', String).get() == 'new'

        cleanup:
        polling.set(false)
        gate.release.countDown()
        env.stop()
    }

    void "stop() while a start reads the property sources does not wait for it, and nothing the start reads after is published"() {
        given: "a key of the first property source in the catalog maps that the later one writes into"
        Gate gate = new Gate(20)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('early', ['early.key': 'early', 'lead.key': 'early'], 10))
        env.addPropertySource(gate)
        env.addPropertySource(PropertySource.of('late', ['late.key': 'late', 'lateKey.value': 'late'], 30))

        when: "the start is parked after the first property source, and the environment is stopped"
        gate.arm()
        Thread starter = daemon('starter') { env.start() }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread stopper = daemon('stopper') { env.stop() }
        stopper.join(WAIT_SECONDS * 1000L)
        boolean stopperWaited = stopper.alive
        gate.release.countDown()
        starter.join(WAIT_SECONDS * 1000L)

        then: "the stopped environment answers what the raw catalog held when it was stopped, as stopping always did"
        !stopperWaited
        !starter.alive
        !env.running
        env.getProperty('lead.key', String).get() == 'early'
        !env.containsProperty('late.key')
        !env.getProperty('late-key.value', String).isPresent()
        !env.getProperty('lateKey.value', String).isPresent()

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13982")
    void "a property source added once a #how has taken the registered ones is visible at once, and wins over a later one at the end"() {
        given: "a property source read after the first that parks the read once armed"
        Gate gate = new Gate(20)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('early', ['shared.key': 'early'], 10))
        env.addPropertySource(gate)
        env.addPropertySource(PropertySource.of('late', ['shared.key': 'late'], 30))
        if (refresh) {
            env.start()
        }

        when:
        gate.arm()
        Thread reader = daemon('reader') { refresh ? env.refresh() : env.start() }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread adder = daemon('adder') {
            env.addPropertySource(PropertySource.of('added', ['shared.key': 'added', 'added.key': 'added']))
        }
        adder.join(WAIT_SECONDS * 1000L)
        boolean adderWaited = adder.alive
        String whileRead = env.getProperty('added.key', String).orElse(null)
        gate.release.countDown()
        reader.join(WAIT_SECONDS * 1000L)

        then:
        !adderWaited
        whileRead == 'added'
        !reader.alive
        env.getProperty('shared.key', String).get() == 'added'
        env.getProperty('added.key', String).get() == 'added'

        when: "the next refresh reads it in its order"
        env.refresh()

        then:
        env.getProperty('shared.key', String).get() == 'late'
        env.getProperty('added.key', String).get() == 'added'

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        how       | refresh
        'start'   | false
        'refresh' | true
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13914")
    void "indexed properties added while a refresh changes the list and the map they index give what processing them after the refresh gives"() {
        given:
        Map<String, Object> declared = ['app.list': ['a', 'b'], 'app.map': [k: 'a']]
        Gate gate = new Gate(20)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('declares', declared) {
            @Override
            int getOrder() { 10 }
        })
        env.addPropertySource(gate)
        env.start()

        when: "the refresh is parked once it has read the changed list and map, and another thread adds indexed properties"
        List<Object> newList = new ArrayList<>(['c', 'd'])
        Map<String, Object> newMap = new LinkedHashMap<>([k: 'c'])
        declared.put('app.list', newList)
        declared.put('app.map', newMap)
        gate.arm()
        Thread refresher = daemon('refresher') { env.refresh() }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        env.addPropertySource(PropertySource.of('indexes', ['app.list[0]': 'z', 'app.map[y]': 'z'], 30))
        List<?> listWhileRead = env.getProperty('app.list', List).orElse(null)
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then:
        listWhileRead == ['z', 'b']
        !refresher.alive
        env.getProperty('app.list', List).get() == ['z', 'd']
        env.getProperty('app.map', Map).get() == [k: 'c', y: 'z']
        newList == ['c', 'd']
        newMap == [k: 'c']

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    @RestoreSystemProperties
    void "the system properties and the environment variables stay through refreshes"() {
        given:
        System.setProperty('refresh.twice', '1')
        DefaultEnvironment env = new DefaultEnvironment({ ['test'] } as ApplicationContextConfiguration)
        env.start()

        when:
        env.refresh()
        String afterFirst = env.getProperty('refresh.twice', String).orElse(null)
        Set<String> namesAfterFirst = env.propertySources*.name as Set
        env.refresh()

        then:
        afterFirst == '1'
        env.getProperty('refresh.twice', String).get() == '1'
        namesAfterFirst.containsAll([SystemPropertiesPropertySource.NAME, EnvironmentPropertySource.NAME])
        (env.propertySources*.name as Set).containsAll([SystemPropertiesPropertySource.NAME, EnvironmentPropertySource.NAME])
        env.propertySources.count { it.name == SystemPropertiesPropertySource.NAME } == 1

        cleanup:
        env.stop()
    }

    void "a refresh runs the code of the property sources and of the loaders with no lock of the environment held"() {
        given:
        List<String> locked = Collections.synchronizedList([])
        LockCheckingEnvironment env = new LockCheckingEnvironment(locked)
        env.addPropertySource(new PropertySource() {
            @Override
            String getName() { 'checking' }

            @Override
            Object get(String key) {
                env.check('get')
                return 'value'
            }

            @Override
            Iterator<String> iterator() {
                env.check('iterator')
                return ['checking.key'].iterator()
            }
        })
        env.start()
        env.checking.set(true)

        when:
        env.refresh()

        then:
        env.checks.get() >= 3
        locked == []
        env.getProperty('checking.key', String).get() == 'value'

        cleanup:
        env.stop()
    }

    void "a refresh of a stopped environment starts it"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['myApp.fooBar': 'value']))
        env.start()
        env.stop()

        when:
        env.refresh()

        then:
        env.running
        env.getProperty('my-app.foo-bar', String).get() == 'value'

        cleanup:
        env.stop()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/14142")
    void "a property source added #moment a refresh reads stays processed when the refresh fails once it has read"() {
        given:
        StartFails env = new StartFails()
        Gate gate = new Gate(Ordered.LOWEST_PRECEDENCE)
        env.addPropertySource(gate)
        env.start()

        when:
        env.fail.set(true)
        Parking parking = beforeRead ? env.stopping : gate
        Thread refresher = parkIn(parking) {
            try {
                env.refresh()
            } catch (IllegalStateException ignored) {
                // expected
            }
        }
        Thread adder = daemon('adder') { env.addPropertySource(PropertySource.of('added', ['added.key': 'added'])) }
        adder.join(WAIT_SECONDS * 1000L)
        boolean adderWaited = adder.alive
        release(parking, refresher)

        then:
        !adderWaited
        env.getProperty('added.key', String).orElse(null) == 'added'

        when:
        env.refresh()

        then:
        env.getProperty('added.key', String).get() == 'added'

        cleanup:
        env.stopping.release.countDown()
        gate.release.countDown()
        env.stop()

        where:
        moment                                      | beforeRead
        'before'                                    | true
        'once it has taken the registered ones, as' | false
    }

    void "a refresh that stop() has dropped, while it still reads, does not change the property sources that a start after the stop owns"() {
        given: "an environment whose configuration file has another value each time it is read, and a property source read last"
        Gate gate = new Gate(Ordered.LOWEST_PRECEDENCE)
        FileReadingEnvironment env = new FileReadingEnvironment()
        env.addPropertySource(gate)
        env.start()

        when: "a refresh is parked before it reads, and the environment is stopped and started, the start parked once it has read its file"
        Thread refresher = parkIn(env.stopping) { env.refresh() }
        env.stop()
        gate.arm()
        Thread starter = daemon('starter') { env.start() }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)

        and: "the dropped refresh reads its file meanwhile, and then the start publishes"
        release(env.stopping, refresher)
        gate.release.countDown()
        starter.join(WAIT_SECONDS * 1000L)

        and: "the next refresh reads the file again"
        env.refresh()

        then: "it reads the file as it is now, not as the start read it"
        !starter.alive
        env.files.get() == 4
        env.getProperty('app.value', String).get() == 'v4'

        cleanup:
        env.stopping.release.countDown()
        gate.release.countDown()
        env.stop()
    }

    void "a start of a stopped environment during its refresh does not read the property sources again once the refresh has published"() {
        given:
        AtomicInteger reads = new AtomicInteger()
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(new PropertySource() {
            @Override
            String getName() { 'counted' }

            @Override
            Object get(String key) { key == 'counted.key' ? 'value' : null }

            @Override
            Iterator<String> iterator() {
                reads.incrementAndGet()
                return ['counted.key'].iterator()
            }
        })
        env.start()
        env.stop()
        reads.set(0)

        when: "the stopped environment is refreshed, and another thread starts it while the refresh is parked before it reads"
        Thread refresher = parkIn(env.stopping) { env.refresh() }
        Thread starter = daemon('starter') { env.start() }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (!(starter.state in [Thread.State.WAITING, Thread.State.TIMED_WAITING]) && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
        release(env.stopping, refresher)
        starter.join(WAIT_SECONDS * 1000L)

        then: "the start waits for the refresh, which has started the environment, and reads nothing itself"
        !starter.alive
        reads.get() == 1
        env.running
        env.getProperty('counted.key', String).get() == 'value'

        cleanup:
        env.stopping.release.countDown()
        env.stop()
    }

    @RestoreSystemProperties
    void "stop() from another thread while a refresh of a bootstrap enabled context reads the bootstrap configuration does not wait for it"() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, StringUtils.TRUE)
        Path dir = Files.createTempDirectory('refresh-bootstrap-')
        dir.resolve('bootstrap.yml').toFile().text = 'bootstrap.value: one\n'
        CountingGate gate = new CountingGate()
        ApplicationContext ctx = ApplicationContext.builder()
                .overrideConfigLocations('file:' + dir)
                .propertySources(gate)
                .start()
        Environment env = ctx.environment

        when: "the refresh is parked while the new bootstrap environment reads the property sources"
        gate.armAt(1)
        Thread refresher = daemon('refresher') { env.refresh() }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread stopper = daemon('stopper') { env.stop() }
        stopper.join(WAIT_SECONDS * 1000L)
        boolean stopperWaited = stopper.alive
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then:
        !stopperWaited
        !refresher.alive
        !env.running

        cleanup:
        gate.release.countDown()
        ctx?.close()
        dir?.toFile()?.deleteDir()
    }

    @RestoreSystemProperties
    void "a property source added while a refresh of a bootstrap enabled context processes the property sources does not wait for it, and is visible at once"() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, StringUtils.TRUE)
        Path dir = Files.createTempDirectory('refresh-bootstrap-')
        dir.resolve('bootstrap.yml').toFile().text = 'bootstrap.value: one\n'
        CountingGate gate = new CountingGate()
        ApplicationContext ctx = ApplicationContext.builder()
                .overrideConfigLocations('file:' + dir)
                .propertySources(gate)
                .start()
        Environment env = ctx.environment

        when: "the refresh is parked where it processes the property sources, after the bootstrap environment has read them"
        gate.armAt(2)
        Thread refresher = daemon('refresher') { env.refresh() }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread adder = daemon('adder') { env.addPropertySource(PropertySource.of('added', ['added.key': 'added'])) }
        adder.join(WAIT_SECONDS * 1000L)
        boolean adderWaited = adder.alive
        String whileRead = env.getProperty('added.key', String).orElse(null)
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then:
        !adderWaited
        whileRead == 'added'
        !refresher.alive
        env.getProperty('added.key', String).get() == 'added'
        env.getProperty('bootstrap.value', String).get() == 'one'

        cleanup:
        gate.release.countDown()
        ctx?.close()
        dir?.toFile()?.deleteDir()
    }

    static ApplicationContextConfiguration withoutDefaultPropertySources() {
        ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration
    }

    /**
     * Runs the read on another thread, once the parking is armed, and returns that thread once the
     * read is parked.
     */
    private static Thread parkIn(Parking parking, Closure<?> read) {
        parking.arm()
        Thread reader = daemon('reader', read)
        assert parking.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        return reader
    }

    private static void release(Parking parking, Thread reader) {
        parking.release.countDown()
        reader.join(WAIT_SECONDS * 1000L)
        assert !reader.alive
    }

    private static Thread daemon(String name, Closure<?> work) {
        Thread thread = new Thread(work, name)
        thread.daemon = true
        thread.start()
        return thread
    }

    /**
     * Parks, once armed, the thread that passes it.
     */
    static class Parking {
        final CountDownLatch reached = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicBoolean armed = new AtomicBoolean()

        void arm() {
            armed.set(true)
        }

        boolean pass() {
            if (armed.compareAndSet(true, false)) {
                reached.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of a property source
                if (!release.await(3 * ReadPropertySourcesSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the parked thread was never released')
                }
                return true
            }
            return false
        }
    }

    /**
     * A property source without properties that, once armed, parks the thread that reads it.
     */
    static class Gate extends Parking implements PropertySource {
        private final int order

        Gate(int order) {
            this.order = order
        }

        CountDownLatch getEntered() { reached }

        @Override
        String getName() { 'gate' }

        @Override
        int getOrder() { order }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            pass()
            return Collections.emptyIterator()
        }
    }

    /**
     * A property source without properties, read first, that parks the thread that reads it the
     * given number of times once armed: the bootstrap environment of a refresh reads it first, and
     * then the refresh itself.
     */
    static class CountingGate implements PropertySource {
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicInteger countdown = new AtomicInteger()

        void armAt(int read) {
            countdown.set(read)
        }

        @Override
        String getName() { 'counting-gate' }

        @Override
        int getOrder() { Ordered.HIGHEST_PRECEDENCE }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (countdown.get() > 0 && countdown.decrementAndGet() == 0) {
                entered.countDown()
                release.await(3 * ReadPropertySourcesSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * An environment without default property sources that, once armed, parks the thread that
     * stops it, which a refresh does before it reads the property sources, or the one that
     * publishes what a refresh has read.
     */
    static class ParkingEnvironment extends DefaultEnvironment {
        final Parking stopping = new Parking()
        final Parking publishing = new Parking()

        ParkingEnvironment() {
            super(ReadPropertySourcesSpec.withoutDefaultPropertySources())
        }

        @Override
        Environment stop() {
            stopping.pass()
            return super.stop()
        }

        @Override
        Environment start() {
            Environment environment = super.start()
            publishing.pass()
            return environment
        }
    }

    /**
     * An environment whose configuration file, named {@code application}, has another value each
     * time it is read, and that parks the thread that stops it once armed. It reads no system
     * properties and no environment variables.
     */
    static class FileReadingEnvironment extends DefaultEnvironment {
        final Parking stopping = new Parking()
        final AtomicInteger files = new AtomicInteger()

        FileReadingEnvironment() {
            super({ ['test'] } as ApplicationContextConfiguration)
        }

        @Override
        Environment stop() {
            stopping.pass()
            return super.stop()
        }

        @Override
        protected List<PropertySource> readPropertySourceList(String name) {
            return [PropertySource.of('application', ['app.value': 'v' + files.incrementAndGet()])]
        }

        @Override
        protected void addDefaultPropertySources(List<PropertySource> propertySources) {
            // only the file
        }
    }

    /**
     * An environment without default property sources whose start(), once armed, fails once it has
     * read the property sources.
     */
    static class StartFails extends ParkingEnvironment {
        final AtomicBoolean fail = new AtomicBoolean()

        @Override
        Environment start() {
            Environment environment = super.start()
            if (fail.compareAndSet(true, false)) {
                throw new IllegalStateException('failed once the property sources were read')
            }
            return environment
        }
    }

    /**
     * A property source without properties that, once armed, needs the given lock to be read, and
     * notes when it does not get it in time.
     */
    static class NeedsLock implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        volatile boolean timedOut
        private final ReentrantLock lock

        NeedsLock(ReentrantLock lock) {
            this.lock = lock
        }

        @Override
        String getName() { 'needs-lock' }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                // shorter than the waits for the threads, which a deadlock is reported by
                if (lock.tryLock(ReadPropertySourcesSpec.WAIT_SECONDS.intdiv(2), TimeUnit.SECONDS)) {
                    lock.unlock()
                } else {
                    timedOut = true
                }
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * An environment without default property sources whose loader, once checking, notes whether
     * the lock of the catalogs is held while it loads, as its property sources do.
     */
    static class LockCheckingEnvironment extends DefaultEnvironment {
        final AtomicBoolean checking = new AtomicBoolean()
        final AtomicInteger checks = new AtomicInteger()
        private final List<String> locked

        LockCheckingEnvironment(List<String> locked) {
            super({ ['test'] } as ApplicationContextConfiguration)
            this.locked = locked
        }

        void check(String what) {
            if (checking.get()) {
                checks.incrementAndGet()
                if (Thread.holdsLock(catalog)) {
                    locked << what
                }
            }
        }

        @Override
        protected void addDefaultPropertySources(List<PropertySource> propertySources) {
            // no system properties or environment variables: only the sources of the test
        }

        @Override
        Collection<PropertySourceLoader> getPropertySourceLoaders() {
            return [new PropertySourceLoader() {
                @Override
                Optional<PropertySource> load(String resourceName, ResourceLoader resourceLoader) {
                    check('loader')
                    return Optional.empty()
                }

                @Override
                Optional<PropertySource> loadEnv(String resourceName, ResourceLoader resourceLoader, ActiveEnvironment activeEnvironment) {
                    check('loader')
                    return Optional.empty()
                }

                @Override
                Map<String, Object> read(String name, InputStream input) throws IOException {
                    return [:]
                }
            }]
        }
    }
}
