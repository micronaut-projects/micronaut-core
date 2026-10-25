package io.micronaut.context.env

import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A property source added by the name of one that a failed refresh left to be processed after it,
 * while that one is processed: by a thread that holds the lock of the catalogs, and by one that is
 * interrupted while it waits.
 */
@Timeout(90)
class AddPropertySourceDeferredSameNameSpec extends Specification {
    void "a catalog owner can add the name of deferred work waiting for its catalog"() {
        given:
        ReviewEnvironment env = new ReviewEnvironment()
        ReplaySource first = new ReplaySource(true)
        FailingSource failing = new FailingSource(env, first.armed)
        env.addPropertySource(failing)
        env.start()
        List<Throwable> errors = Collections.synchronizedList([])
        AtomicReference<Throwable> refreshFailure = new AtomicReference<>()
        Gate nested = new Gate()
        PropertySource holderSource = new PropertySource() {
            @Override
            String getName() { 'holder' }
            @Override
            Object get(String key) { null }
            @Override
            Iterator<String> iterator() {
                assert env.ownsCatalog()
                nested.pause()
                env.addPropertySource(PropertySource.of('target', ['target.value': 'newer']))
                return Collections.emptyIterator()
            }
        }
        Thread refresher = null
        Thread adder = null
        Thread holder = null

        when: 'a failed refresh leaves a source paused before it acquires the catalog'
        env.parkStop.set(true)
        failing.armed.set(true)
        refresher = worker('review-refresh', errors) {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure.set(t)
            }
        }
        assert env.stopped.entered.await(10, TimeUnit.SECONDS)
        adder = worker('review-add', errors) { env.addPropertySource(first) }
        awaitCondition { env.propertySources.any { it.is(first) } }
        awaitCondition { !adder.alive || adder.state in [Thread.State.BLOCKED, Thread.State.TIMED_WAITING] }
        env.stopped.release.countDown()
        assert first.replay.entered.await(10, TimeUnit.SECONDS)
        // On 4.10.34 the original adding thread can reach getName() just before the
        // failed refresh clears its reading flag. Finish that refresh before adding the holder.
        if (first.replaying.get() != refresher) {
            refresher.join(10_000)
            assert !refresher.alive
        }

        and: 'a different source owns the catalog, then adds a replacement by the deferred name'
        holder = worker('review-holder', errors) {
            env.addPropertySource(holderSource)
        }
        assert nested.entered.await(10, TimeUnit.SECONDS) : "errors=${errors}; replay=${first.replaying.get()?.stackTrace?.toList()}; holder=${holder.stackTrace.toList()}"
        first.replay.release.countDown()
        awaitCondition { first.replaying.get().state == Thread.State.BLOCKED }
        nested.release.countDown()
        holder.join(3_000)
        adder.join(3_000)
        refresher.join(3_000)

        then:
        assert !holder.alive : "holder=${holder.stackTrace.toList()}; replay=${first.replaying.get()?.stackTrace?.toList()}"
        !adder.alive
        !refresher.alive
        errors.empty
        refreshFailure.get() instanceof IllegalArgumentException
        env.getProperty('target.value', String).orElse(null) == 'newer'

        cleanup:
        env.stopped.release.countDown()
        env.readFailed.countDown()
        first.replay.release.countDown()
        nested.release.countDown()
        // A regression leaves daemon threads in a lock cycle; stopping needs that same lock.
        if (![refresher, adder, holder].findAll().any { it.alive }) {
            env.stop()
        }
    }

    void "an interrupted thread adding the name of deferred work does not wait, keeps its interrupt, and its source is processed after that work"() {
        given:
        ReviewEnvironment env = new ReviewEnvironment()
        ReplaySource first = new ReplaySource(false)
        FailingSource failing = new FailingSource(env, first.armed)
        env.addPropertySource(failing)
        env.start()
        List<Throwable> errors = Collections.synchronizedList([])
        AtomicReference<Throwable> refreshFailure = new AtomicReference<>()
        AtomicReference<Throwable> addFailure = new AtomicReference<>()
        AtomicBoolean interruptPreserved = new AtomicBoolean()
        Thread refresher = null
        Thread adder = null
        Thread replacement = null

        when:
        env.parkStop.set(true)
        failing.armed.set(true)
        refresher = worker('review-refresh', errors) {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure.set(t)
            }
        }
        assert env.stopped.entered.await(10, TimeUnit.SECONDS)
        adder = worker('review-add', errors) { env.addPropertySource(first) }
        awaitCondition { env.propertySources.any { it.is(first) } }
        awaitCondition { !adder.alive || adder.state in [Thread.State.BLOCKED, Thread.State.TIMED_WAITING] }
        env.stopped.release.countDown()
        assert first.replay.entered.await(10, TimeUnit.SECONDS)

        and: 'an interrupted thread adds a replacement by that name, which fails to be processed'
        PropertySource failingReplacement = new PropertySource() {
            @Override
            String getName() { 'target' }
            @Override
            Object get(String key) { null }
            @Override
            Iterator<String> iterator() { throw new IllegalStateException('replacement failed') }
        }
        replacement = worker('review-replacement', errors) {
            Thread.currentThread().interrupt()
            try {
                env.addPropertySource(failingReplacement)
            } catch (Throwable t) {
                addFailure.set(t)
            } finally {
                interruptPreserved.set(Thread.currentThread().isInterrupted())
            }
        }
        replacement.join(10_000)
        boolean replacementWaited = replacement.alive
        first.replay.release.countDown()
        replacement.join(10_000)
        adder.join(10_000)
        refresher.join(10_000)

        then: 'the replacement is left to the thread processing that work, which processes it after it and logs its failure'
        !replacementWaited
        !replacement.alive
        !adder.alive
        !refresher.alive
        errors.empty
        addFailure.get() == null
        refreshFailure.get() instanceof IllegalArgumentException
        interruptPreserved.get()
        env.propertySources.find { it.name == 'target' }.is(failingReplacement)

        cleanup:
        env.stopped.release.countDown()
        env.readFailed.countDown()
        first.replay.release.countDown()
        if (![refresher, adder, replacement].findAll().any { it.alive }) {
            env.stop()
        }
    }

    void "a long run of sources added by the name of deferred work, each while the one before is processed, is processed in order without deepening the stack"() {
        given:
        int count = 300
        ReviewEnvironment env = new ReviewEnvironment()
        AtomicBoolean replay = new AtomicBoolean()
        Map<Integer, Integer> depths = new ConcurrentHashMap<>()
        Map<Integer, String> threads = new ConcurrentHashMap<>()
        List<ChainSource> chain = (0..count).collect { new ChainSource(it, replay, depths, threads) }
        FailingSource failing = new FailingSource(env, replay)
        env.addPropertySource(failing)
        env.start()
        List<Throwable> errors = Collections.synchronizedList([])
        AtomicReference<Throwable> refreshFailure = new AtomicReference<>()
        List<Thread> adders = Collections.synchronizedList([])
        Thread refresher = null

        when: 'a failed refresh leaves the first source to be processed after it'
        env.parkStop.set(true)
        failing.armed.set(true)
        refresher = worker('review-refresh', errors) {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure.set(t)
            }
        }
        assert env.stopped.entered.await(10, TimeUnit.SECONDS)
        Thread adder = worker('review-add', errors) { env.addPropertySource(chain[0]) }
        adders.add(adder)
        awaitCondition { env.propertySources.any { it.is(chain[0]) } }
        awaitCondition { !adder.alive || adder.state in [Thread.State.BLOCKED, Thread.State.TIMED_WAITING] }
        env.stopped.release.countDown()
        assert chain[0].processing.entered.await(10, TimeUnit.SECONDS)

        and: 'each next source by that name is added while the one before is processed'
        for (int i = 1; i <= count; i++) {
            ChainSource next = chain[i]
            Thread add = worker("review-add-$i", errors) { env.addPropertySource(next) }
            adders.add(add)
            // handed over, or, up to 4.10.34, waiting to process it itself
            awaitCondition {
                !add.alive || add.state == Thread.State.BLOCKED && add.stackTrace.any { it.methodName == 'processPropertySource' }
            }
            chain[i - 1].processing.release.countDown()
            assert next.processing.entered.await(10, TimeUnit.SECONDS) : "errors=${errors}"
        }
        chain[count].processing.release.countDown()
        adders.each { it.join(10_000) }
        refresher.join(10_000)

        then: 'the last one ends on top, processed about as deep in the stack as the first one handed over'
        !refresher.alive
        adders.every { !it.alive }
        errors.empty
        refreshFailure.get() instanceof IllegalArgumentException
        depths.size() == count + 1
        assert depths[count] <= depths[1] + 10 : "depths=${depths[1]}..${depths[count]}; threads=${threads[1]}..${threads[count]}"
        env.getProperty('chain.value', Integer).orElse(-1) == count

        cleanup:
        env.stopped.release.countDown()
        env.readFailed.countDown()
        chain.each { it.processing.release.countDown() }
        if (!(adders + refresher).findAll().any { it.alive }) {
            env.stop()
        }
    }

    private static Thread worker(String name, List<Throwable> errors, Closure<?> action) {
        Thread.startDaemon(name) {
            try {
                action.call()
            } catch (Throwable t) {
                errors.add(t)
            }
        }
    }

    private static void awaitCondition(Closure<Boolean> condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition.call() && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
        assert condition.call()
    }

    static class Gate {
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        void pause() {
            entered.countDown()
            assert release.await(30, TimeUnit.SECONDS)
        }
    }

    static class ReviewEnvironment extends DefaultEnvironment {
        final AtomicBoolean parkStop = new AtomicBoolean()
        final Gate stopped = new Gate()
        final CountDownLatch readFailed = new CountDownLatch(1)
        ReviewEnvironment() {
            super(ApplicationContext.builder().environments('test').deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration)
        }
        @Override
        Environment stop() {
            if (parkStop.compareAndSet(true, false)) {
                stopped.pause()
            }
            return super.stop()
        }
        @Override
        protected void resetCaches() {
            // On 4.10.34 the adding thread processes its source after the refresh. Park it
            // before getName() until the read fails, to reproduce the same interleaving there.
            if (Thread.currentThread().name == 'review-add') {
                assert readFailed.await(30, TimeUnit.SECONDS)
            }
            super.resetCaches()
        }
        boolean ownsCatalog() { Thread.holdsLock(catalog) }
    }

    static class ReplaySource implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        final AtomicReference<Thread> replaying = new AtomicReference<>()
        final Gate replay = new Gate()
        private final boolean beforeCatalog
        ReplaySource(boolean beforeCatalog) { this.beforeCatalog = beforeCatalog }
        private void parkOnce() {
            if (armed.compareAndSet(true, false)) {
                replaying.set(Thread.currentThread())
                replay.pause()
            }
        }
        @Override
        String getName() {
            if (beforeCatalog) { parkOnce() }
            return 'target'
        }
        @Override
        Object get(String key) { 'older' }
        @Override
        Iterator<String> iterator() {
            if (!beforeCatalog) { parkOnce() }
            return ['target.value'].iterator()
        }
    }

    @CompileStatic
    static class ChainSource implements PropertySource {
        final int sequence
        final AtomicBoolean replay
        final Map<Integer, Integer> depths
        final Map<Integer, String> threads
        final Gate processing = new Gate()
        ChainSource(int sequence, AtomicBoolean replay, Map<Integer, Integer> depths, Map<Integer, String> threads) {
            this.sequence = sequence
            this.replay = replay
            this.depths = depths
            this.threads = threads
        }
        @Override
        String getName() { 'chain' }
        @Override
        Object get(String key) { sequence }
        @Override
        Iterator<String> iterator() {
            if (replay.get()) {
                depths.put(sequence, new Throwable().stackTrace.length)
                threads.put(sequence, Thread.currentThread().name)
                processing.pause()
            }
            return ['chain.value'].iterator()
        }
    }

    static class FailingSource implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        private final ReviewEnvironment env
        private final AtomicBoolean toArm
        FailingSource(ReviewEnvironment env, AtomicBoolean toArm) {
            this.env = env
            this.toArm = toArm
        }
        @Override
        String getName() { 'failing' }
        @Override
        int getOrder() { 200 }
        @Override
        Object get(String key) { null }
        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                toArm.set(true)
                env.readFailed.countDown()
                throw new IllegalArgumentException('read failed')
            }
            return Collections.emptyIterator()
        }
    }
}
