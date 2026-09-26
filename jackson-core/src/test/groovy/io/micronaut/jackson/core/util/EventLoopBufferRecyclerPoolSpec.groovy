package io.micronaut.jackson.core.util

import io.netty.util.concurrent.FastThreadLocalThread
import spock.lang.Specification
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.ObjectWriteContext
import tools.jackson.core.json.JsonFactory
import tools.jackson.core.util.BufferRecycler

import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class EventLoopBufferRecyclerPoolSpec extends Specification {

    EventLoopBufferRecyclerPool pool = new EventLoopBufferRecyclerPool()
    JsonFactory factory = JsonFactory.builder().recyclerPool(pool).build()

    void "netty is supported"() {
        expect:
        EventLoopBufferRecyclerPool.isSupported()
    }

    void "an event loop thread reuses its own recycler without the shared pool"() {
        when:
        def result = onFastThread {
            BufferRecycler first = pool.acquireAndLinkPooled()
            boolean linked = first.isLinkedWithPool()
            first.releaseToPool()
            BufferRecycler second = pool.acquireAndLinkPooled()
            second.releaseToPool()
            pool.releasePooled(second)
            [first, second, linked, pool.acquirePooled()]
        }

        then:
        result[0].is(result[1])
        result[0].is(result[3])
        !result[2]
        pool.pooledCount() == 0
    }

    void "parsers and generators of an event loop thread use its recycler"() {
        when:
        def result = onFastThread {
            BufferRecycler recycler = pool.acquirePooled()
            JsonParser parser = factory.createParser(ObjectReadContext.empty(), '{"a":1}'.getBytes(StandardCharsets.UTF_8))
            BufferRecycler parserRecycler = parser._ioContext.bufferRecycler()
            parser.close()
            JsonGenerator generator = factory.createGenerator(ObjectWriteContext.empty(), new StringWriter())
            BufferRecycler generatorRecycler = generator._ioContext.bufferRecycler()
            generator.close()
            char[] buffer = recycler.allocCharBuffer(BufferRecycler.CHAR_CONCAT_BUFFER)
            recycler.releaseCharBuffer(BufferRecycler.CHAR_CONCAT_BUFFER, buffer)
            // the generator takes the released buffer and gives it back when closed
            generator = factory.createGenerator(ObjectWriteContext.empty(), new StringWriter())
            generator.writeString("x")
            generator.close()
            [recycler, parserRecycler, generatorRecycler, buffer, recycler.allocCharBuffer(BufferRecycler.CHAR_CONCAT_BUFFER)]
        }

        then:
        result[1].is(result[0])
        result[2].is(result[0])
        result[4].is(result[3])
        pool.pooledCount() == 0
    }

    void "several parsers can be open at once on an event loop thread"() {
        when:
        def result = onFastThread {
            JsonParser outer = factory.createParser(ObjectReadContext.empty(), '{"a":[1,2,3]}'.getBytes(StandardCharsets.UTF_8))
            JsonParser inner = factory.createParser(ObjectReadContext.empty(), '[4,5]'.getBytes(StandardCharsets.UTF_8))
            List<Integer> values = []
            outer.nextToken(); outer.nextToken(); outer.nextToken()
            inner.nextToken()
            while (outer.nextToken() == JsonToken.VALUE_NUMBER_INT) {
                values << outer.getIntValue()
                if (inner.nextToken() == JsonToken.VALUE_NUMBER_INT) {
                    values << inner.getIntValue()
                }
            }
            inner.close()
            outer.close()
            values
        }

        then:
        result == [1, 4, 2, 5, 3]
    }

    void "other platform threads use the shared pool"() {
        when:
        BufferRecycler first = pool.acquireAndLinkPooled()
        boolean linked = first.isLinkedWithPool()
        first.releaseToPool()
        int pooled = pool.pooledCount()
        BufferRecycler second = pool.acquireAndLinkPooled()
        second.releaseToPool()

        then:
        linked
        pooled == 1
        first.is(second)
        pool.pooledCount() == 1
    }

    void "virtual threads use the shared pool"() {
        when:
        def result = new AtomicReference<List>()
        Thread.ofVirtual().start {
            BufferRecycler first = pool.acquireAndLinkPooled()
            boolean linked = first.isLinkedWithPool()
            first.releaseToPool()
            // takes the same recycler from the shared pool and releases it again
            assert parse(factory, generate(factory, 3)) == 3
            result.set([first, linked])
        }.join()

        then:
        result.get()[1]
        pool.pooledCount() == 1

        when:
        def second = new AtomicReference<BufferRecycler>()
        Thread.ofVirtual().start {
            BufferRecycler recycler = pool.acquireAndLinkPooled()
            second.set(recycler)
            recycler.releaseToPool()
        }.join()

        then:
        second.get().is(result.get()[0])
        pool.pooledCount() == 1
    }

    void "concurrent use from event loop, platform and virtual threads"() {
        given:
        int threads = 4
        int iterations = 500
        def errors = new CopyOnWriteArrayList<Throwable>()
        def eventLoopRecyclers = ConcurrentHashMap.newKeySet()
        def start = new CountDownLatch(1)
        Closure<Void> work = { int id ->
            start.await()
            for (int i = 0; i < iterations; i++) {
                int value = id * iterations + i
                assert parse(factory, generate(factory, value)) == value
            }
            return null
        }
        List<Thread> all = []
        threads.times { int t ->
            all << new FastThreadLocalThread({
                try {
                    eventLoopRecyclers << System.identityHashCode(pool.acquirePooled())
                    work(t)
                } catch (Throwable e) {
                    errors << e
                }
            } as Runnable)
            all << new Thread({
                try {
                    work(threads + t)
                } catch (Throwable e) {
                    errors << e
                }
            } as Runnable)
            all << Thread.ofVirtual().unstarted({
                try {
                    work(2 * threads + t)
                } catch (Throwable e) {
                    errors << e
                }
            } as Runnable)
        }

        when:
        all*.start()
        start.countDown()
        all*.join()

        then:
        errors.empty
        eventLoopRecyclers.size() == threads
        pool.pooledCount() >= 1
        pool.pooledCount() <= 2 * threads
    }

    private static String generate(JsonFactory factory, int value) {
        def writer = new StringWriter()
        JsonGenerator generator = factory.createGenerator(ObjectWriteContext.empty(), writer)
        generator.writeStartObject()
        generator.writeNumberProperty("value", value)
        generator.writeStringProperty("text", "x" * 100)
        generator.writeEndObject()
        generator.close()
        return writer.toString()
    }

    private static int parse(JsonFactory factory, String json) {
        JsonParser parser = factory.createParser(ObjectReadContext.empty(), json.getBytes(StandardCharsets.UTF_8))
        try {
            assert parser.nextToken() == JsonToken.START_OBJECT
            assert parser.nextName() == "value"
            parser.nextToken()
            return parser.getIntValue()
        } finally {
            parser.close()
        }
    }

    private static <T> T onFastThread(Callable<T> callable) {
        def result = new AtomicReference<T>()
        def error = new AtomicReference<Throwable>()
        def thread = new FastThreadLocalThread({
            try {
                result.set(callable.call())
            } catch (Throwable e) {
                error.set(e)
            }
        } as Runnable)
        thread.start()
        thread.join()
        if (error.get() != null) {
            throw error.get()
        }
        return result.get()
    }
}
