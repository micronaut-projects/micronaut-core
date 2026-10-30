package io.micronaut.context.env

import io.micronaut.context.env.RefreshTestSupport.NeedsLock
import io.micronaut.context.env.RefreshTestSupport.Parking
import io.micronaut.context.env.RefreshTestSupport.ParkingEnvironment
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

import static io.micronaut.context.env.RefreshTestSupport.WAIT_SECONDS
import static io.micronaut.context.env.RefreshTestSupport.daemon
import static io.micronaut.context.env.RefreshTestSupport.parkIn
import static io.micronaut.context.env.RefreshTestSupport.release
import static io.micronaut.context.env.RefreshTestSupport.withoutDefaultPropertySources

/**
 * A property source that another thread adds while the environment refreshes does not wait for the
 * refresh. The refresh reads it in its order if it has not taken the registered property sources
 * yet, and otherwise processes it on top once it has published, as the add did once it got the
 * lock. A refresh waits for an add whose processing is under way. The threads are parked at fixed
 * points, and every wait is bounded.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/14158")
@Timeout(60)
class AddDuringRefreshSpec extends Specification {

    void "a thread that holds a lock the refresh needs, and adds a property source before the refresh reads, does not wait for it, and the refresh reads the property source in its order"() {
        given: "a property source that, once armed, needs a lock of the application, and one of a higher order"
        ReentrantLock appLock = new ReentrantLock()
        ParkingEnvironment env = new ParkingEnvironment()
        NeedsLock needsLock = new NeedsLock(appLock)
        env.addPropertySource(needsLock)
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['shared': 'lower', 'lower.key': 'lower'], -10)

        when: "another thread takes the lock and adds a property source of a lower order while the refresh is parked before it stops the environment"
        needsLock.arm()
        Thread refresher = parkIn(env.stopping) { env.refresh() }
        Thread adder = daemon('adder') {
            appLock.lock()
            try {
                env.addPropertySource(lower)
            } finally {
                appLock.unlock()
            }
        }
        adder.join(WAIT_SECONDS * 1000L)
        boolean addReturnedWhileParked = !adder.alive
        release(env.stopping, refresher)

        then: "the add returned while the refresh was parked, and the refresh got the lock"
        addReturnedWhileParked
        needsLock.gotLock.get()

        and: "the refresh read the added property source in its order"
        env.getProperty('shared', String).get() == 'higher'
        env.getProperty('lower.key', String).get() == 'lower'

        cleanup:
        env.stopping.release.countDown()
        env.stop()
    }

    void "a property source added once the refresh has read does not wait for it, and is processed on top once the refresh has published"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        Map<String, Object> data = ['shared': 'higher', 'app.value': 'old']
        env.addPropertySource(new MapPropertySource('higher', data))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['shared': 'lower'], -10)

        when:
        data.put('app.value', 'new')
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread adder = daemon('adder') { env.addPropertySource(lower) }
        adder.join(WAIT_SECONDS * 1000L)
        boolean addReturnedWhileParked = !adder.alive
        String sharedWhileParked = env.getProperty('shared', String).orElse(null)
        release(env.started, refresher)

        then: "the add returned at once, and shows once the refresh has published, on top"
        addReturnedWhileParked
        sharedWhileParked == 'higher'
        env.getProperty('app.value', String).get() == 'new'
        env.getProperty('shared', String).get() == 'lower'

        cleanup:
        env.started.release.countDown()
        env.stop()
    }

    void "a property source added before a refresh that then fails does not wait for it, and is processed on top once the refresh has failed"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['shared': 'lower', 'lower.key': 'lower'], -10)
        Throwable failure = null

        when:
        env.failing.set(true)
        Thread refresher = parkIn(env.stopping) {
            try {
                env.refresh()
            } catch (Throwable t) {
                failure = t
            }
        }
        Thread adder = daemon('adder') { env.addPropertySource(lower) }
        adder.join(WAIT_SECONDS * 1000L)
        boolean addReturnedWhileParked = !adder.alive
        release(env.stopping, refresher)

        then: "the refresh failed and published nothing, and the add was processed on top"
        addReturnedWhileParked
        failure instanceof IllegalStateException
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('shared', String).get() == 'lower'

        cleanup:
        env.stopping.release.countDown()
        env.stop()
    }

    void "of two property sources by one name added while the refresh is parked once it has read, the later one is processed on top, and the earlier one not at all"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(PropertySource.of('data', ['app.value': 'data']))
        env.start()

        when:
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread adder = daemon('adder') {
            env.addPropertySource(PropertySource.of('named', ['named.value': 'first', 'first.only': 'first']))
            env.addPropertySource(PropertySource.of('named', ['named.value': 'second']))
        }
        adder.join(WAIT_SECONDS * 1000L)
        boolean addsReturnedWhileParked = !adder.alive
        release(env.started, refresher)

        then:
        addsReturnedWhileParked
        env.getProperty('named.value', String).get() == 'second'
        !env.getProperty('first.only', String).isPresent()
        env.getProperty('app.value', String).get() == 'data'

        cleanup:
        env.started.release.countDown()
        env.stop()
    }

    void "a property source added while the refresh is parked, which another thread then stops, stays registered, and the next start reads it"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(PropertySource.of('data', ['app.value': 'data']))
        env.start()
        PropertySource added = PropertySource.of('added', ['added.key': 'added'])

        when:
        Thread refresher = parkIn(env.started) { env.refresh() }
        Thread other = daemon('other') {
            env.addPropertySource(added)
            env.stop()
        }
        other.join(WAIT_SECONDS * 1000L)
        boolean returnedWhileParked = !other.alive
        release(env.started, refresher)

        then:
        returnedWhileParked
        !env.running
        env.propertySources.any { it.is(added) }

        when:
        env.start()

        then:
        env.getProperty('added.key', String).get() == 'added'

        cleanup:
        env.started.release.countDown()
        env.stop()
    }

    void "a property source added during the refresh that fails once the refresh has published is left out, and those added after it are processed"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(PropertySource.of('data', ['app.value': 'data']))
        env.start()
        PropertySource broken = new PropertySource() {
            @Override
            String getName() { 'broken' }

            @Override
            Object get(String key) { null }

            @Override
            Iterator<String> iterator() {
                throw new IllegalStateException('cannot read broken')
            }
        }
        Throwable failure = null

        when:
        Thread refresher = parkIn(env.started) {
            try {
                env.refresh()
            } catch (Throwable t) {
                failure = t
            }
        }
        Thread adder = daemon('adder') {
            env.addPropertySource(broken)
            env.addPropertySource(PropertySource.of('after', ['after.key': 'after']))
        }
        adder.join(WAIT_SECONDS * 1000L)
        boolean addsReturnedWhileParked = !adder.alive
        release(env.started, refresher)

        then: "the refresh did not fail for it"
        addsReturnedWhileParked
        failure == null
        env.getProperty('after.key', String).get() == 'after'
        env.propertySources.any { it.is(broken) }

        cleanup:
        env.started.release.countDown()
        env.removePropertySource(broken)
        env.stop()
    }

    void "a refresh that starts while a property source is processed by the thread adding it waits for that, and both are published"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        Map<String, Object> data = ['app.value': 'old']
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()
        ParksWhenIterated parking = new ParksWhenIterated()

        when: "the add is parked while it processes its property source, with the lock of the catalogs held, and another thread refreshes"
        data.put('app.value', 'new')
        parking.arm()
        Thread adder = daemon('adder') { env.addPropertySource(parking) }
        assert parking.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread refresher = daemon('refresher') { env.refresh() }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (refresher.alive && !(refresher.state in [Thread.State.WAITING, Thread.State.BLOCKED])) {
            assert System.nanoTime() < deadline: 'the refresh never waited for the add'
            Thread.sleep(5)
        }
        parking.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)

        then:
        !adder.alive
        !refresher.alive
        env.getProperty('parked.key', String).get() == 'parked'
        env.getProperty('app.value', String).get() == 'new'

        cleanup:
        parking.release.countDown()
        env.stop()
    }

    void "a property source whose name refreshes the environment while it is added does not deadlock"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        Map<String, Object> data = ['app.value': 'old']
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()
        RefreshesFromName refreshing = new RefreshesFromName(env)

        when: "the add asks the property source for its name once more, as it processes it, and that refreshes"
        data.put('app.value', 'new')
        Thread adder = daemon('adder') { env.addPropertySource(refreshing) }
        adder.join(WAIT_SECONDS * 1000L)

        then:
        !adder.alive
        refreshing.refreshed.get()
        env.getProperty('app.value', String).get() == 'new'
        env.getProperty('named.key', String).get() == 'named'

        cleanup:
        env.stop()
    }

    void "a property source whose name starts the environment on another thread, and waits for that thread, while it is added does not deadlock: #how"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        env.addPropertySource(PropertySource.of('data', ['app.value': 'data']))
        env.start()
        StartsFromName starting = new StartsFromName(env)

        when: "the property source is asked for its name after it is registered, and that starts the environment on another thread"
        Thread refresher = how == 'during a refresh' ? parkIn(env.started) { env.refresh() } : null
        Thread adder = daemon('adder') { env.addPropertySource(starting) }
        adder.join(WAIT_SECONDS * 1000L)
        boolean addReturned = !adder.alive
        if (refresher != null) {
            release(env.started, refresher)
        }

        then: "every start on the other thread returned while the property source waited for it"
        addReturned
        starting.starts.get() > 0
        starting.allReturned.get()
        env.getProperty('starting.key', String).get() == 'starting'

        cleanup:
        env.started.release.countDown()
        env.stop()

        where:
        how << ['on a running environment', 'during a refresh']
    }

    void "a property source added while the next refresh is active is left to that refresh, not to the one that has just stopped being active"() {
        given:
        PublishParkingEnvironment env = new PublishParkingEnvironment()
        env.addPropertySource(PropertySource.of('data', ['app.value': 'data']))
        ParksWhenIterated reading = new ParksWhenIterated()
        env.addPropertySource(reading)
        env.start()
        PropertySource added = PropertySource.of('added', ['added.key': 'added'])
        Throwable secondFailure = null

        when: "the first refresh is stopped while it reads, and parked once it is no longer active, before it publishes"
        Thread first = parkIn(reading) { env.refresh() }
        Thread stopper = daemon('stopper') { env.stop() }
        stopper.join(WAIT_SECONDS * 1000L)
        env.publishing.arm()
        reading.release.countDown()
        assert env.publishing.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)

        and: "a second refresh, which then fails, becomes active, and a property source is added meanwhile"
        env.failing.set(true)
        Thread second = daemon('second-refresh') {
            try {
                env.refresh()
            } catch (Throwable t) {
                secondFailure = t
            }
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (second.alive && second.state != Thread.State.BLOCKED) {
            assert System.nanoTime() < deadline: 'the second refresh never waited for the lock'
            Thread.sleep(5)
        }
        Thread adder = daemon('adder') { env.addPropertySource(added) }
        adder.join(WAIT_SECONDS * 1000L)
        boolean addReturned = !adder.alive
        release(env.publishing, first)
        second.join(3 * WAIT_SECONDS * 1000L)

        then: "the second refresh failed, and processed the property source on top"
        !stopper.alive
        addReturned
        !second.alive
        secondFailure instanceof IllegalStateException
        env.getProperty('added.key', String).get() == 'added'

        cleanup:
        reading.release.countDown()
        env.publishing.release.countDown()
        env.stop()
    }

    void "a property source that refreshes the environment when the refresh processes it on top does not change what that refresh reports"() {
        given:
        ParkingEnvironment env = new ParkingEnvironment()
        Map<String, Object> data = ['app.value': 'old']
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()
        RefreshesWhenIterated refreshing = new RefreshesWhenIterated(env)
        Map<String, Object> diff = null

        when: "it is added once the refresh has read, and refreshes the environment when it is processed on top"
        data.put('app.value', 'new')
        Thread refresher = parkIn(env.started) { diff = env.refreshAndDiff() }
        Thread adder = daemon('adder') { env.addPropertySource(refreshing) }
        adder.join(WAIT_SECONDS * 1000L)
        boolean addReturnedWhileParked = !adder.alive
        release(env.started, refresher)

        then: "the refresh reports its own change only"
        addReturnedWhileParked
        refreshing.refreshed.get()
        diff == ['app.value': 'old']
        env.getProperty('queued.value', String).get() == 'queued'

        cleanup:
        env.started.release.countDown()
        env.stop()
    }

    /**
     * A property source that, once armed, parks the thread that iterates it.
     */
    static class ParksWhenIterated extends Parking implements PropertySource {
        @Override
        String getName() { 'parked' }

        @Override
        Object get(String key) { key == 'parked.key' ? 'parked' : null }

        @Override
        Iterator<String> iterator() {
            pass()
            return ['parked.key'].iterator()
        }
    }

    /**
     * A property source that refreshes the environment the second time it is asked for its name: the
     * first time is when it is registered, the second when it is processed.
     */
    static class RefreshesFromName implements PropertySource {
        final AtomicBoolean refreshed = new AtomicBoolean()
        private final Environment env
        private final AtomicInteger names = new AtomicInteger()

        RefreshesFromName(Environment env) {
            this.env = env
        }

        @Override
        String getName() {
            if (names.incrementAndGet() == 2) {
                env.refresh()
                refreshed.set(true)
            }
            return 'named'
        }

        @Override
        Object get(String key) { key == 'named.key' ? 'named' : null }

        @Override
        Iterator<String> iterator() { ['named.key'].iterator() }
    }

    /**
     * A property source that, every time it is asked for its name after it was registered, starts the
     * environment on another thread and waits a bounded time for that thread. Its origin does not
     * ask for its name, so that only the calls for the name itself do.
     */
    static class StartsFromName implements PropertySource {
        final AtomicInteger starts = new AtomicInteger()
        final AtomicBoolean allReturned = new AtomicBoolean(true)
        private final Environment env
        private final AtomicInteger names = new AtomicInteger()

        StartsFromName(Environment env) {
            this.env = env
        }

        @Override
        String getName() {
            if (names.incrementAndGet() > 1) {
                starts.incrementAndGet()
                Thread other = RefreshTestSupport.daemon('starter') { env.start() }
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                other.join(RefreshTestSupport.WAIT_SECONDS * 1000L)
                if (other.alive) {
                    allReturned.set(false)
                }
            }
            return 'starting'
        }

        @Override
        PropertySource.Origin getOrigin() { PropertySource.Origin.of('starts-from-name') }

        @Override
        Object get(String key) { key == 'starting.key' ? 'starting' : null }

        @Override
        Iterator<String> iterator() { ['starting.key'].iterator() }
    }

    /**
     * A property source that refreshes the environment the first time it is iterated.
     */
    static class RefreshesWhenIterated implements PropertySource {
        final AtomicBoolean refreshed = new AtomicBoolean()
        private final Environment env

        RefreshesWhenIterated(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'queued' }

        @Override
        Object get(String key) { key == 'queued.value' ? 'queued' : null }

        @Override
        Iterator<String> iterator() {
            if (refreshed.compareAndSet(false, true)) {
                env.refresh()
            }
            return ['queued.value'].iterator()
        }
    }

    /**
     * A parking environment whose refresh, once armed, also parks when it decides whether to publish
     * what it read, once it is no longer active, with the lock of the catalogs held.
     */
    static class PublishParkingEnvironment extends ParkingEnvironment {
        final Parking publishing = new Parking()

        @Override
        boolean publishesRefresh() {
            publishing.pass()
            return super.publishesRefresh()
        }
    }
}
