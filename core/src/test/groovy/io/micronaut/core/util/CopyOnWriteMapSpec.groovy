package io.micronaut.core.util

import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.stream.IntStream

@SuppressWarnings('GrEqualsBetweenInconvertibleTypes')
class CopyOnWriteMapSpec extends Specification {
    def "simple ops"() {
        given:
        def map = CopyOnWriteMap.create(Integer.MAX_VALUE)

        when:
        map.put("foo", "bar")
        then:
        map == ["foo": "bar"]
        map.containsKey("foo")
        !map.containsKey("bar")
        map.containsValue("bar")
        !map.containsValue("foo")
        map.size() == 1
        map.getOrDefault("foo", "baz") == "bar"
        map.getOrDefault("fiz", "baz") == "baz"
        map.hashCode() == ["foo": "bar"].hashCode()
        map.toString() == "{foo=bar}"

        when:
        map.remove("foo", "bar")
        then:
        map == [:]

        when:
        map.putAll(["foo": "bar"])
        then:
        map == ["foo": "bar"]

        when:
        map.remove("foo")
        then:
        map == [:]

        when:
        map.computeIfAbsent("foo", x -> "bar" + x)
        map.computeIfAbsent("foo", x -> "baz" + x)
        then:
        map == ["foo": "barfoo"]

        when:
        map.clear()
        then:
        map == [:]
    }

    def setUnset() {
        given:
        BitSet bs = new BitSet()

        when:
        CopyOnWriteMap.setUnset(bs, 0)
        then:
        bs.stream().toArray() == [0]

        when:
        CopyOnWriteMap.setUnset(bs, 0)
        then:
        bs.stream().toArray() == [0, 1]

        when:
        CopyOnWriteMap.setUnset(bs, 5)
        then:
        bs.stream().toArray() == [0, 1, 7]

        when:
        CopyOnWriteMap.setUnset(bs, 1)
        then:
        bs.stream().toArray() == [0, 1, 3, 7]
    }

    def eviction(def maxSize) {
        given:
        def map = CopyOnWriteMap.create(maxSize)

        when:
        for (int i = 0; i < maxSize + CopyOnWriteMap.EVICTION_BATCH - 1; i++) {
            map.put("foo" + i, "bar" + i)
        }
        then:
        map.size() == maxSize + CopyOnWriteMap.EVICTION_BATCH - 1

        when:
        map.put("foox", "barx")
        then:
        map.size() == maxSize

        where:
        maxSize << [0, 5]
    }

    def "concurrent evict"() {
        // COWMap is not thread-safe, but the static evict method should work for CHM

        given:
        def map = new ConcurrentHashMap<>()
        ExecutorService service = Executors.newFixedThreadPool(2)

        List<Callable<?>> callables = IntStream.range(0, 1000).mapToObj {i -> (Callable<Void>) () -> {
            map.put("foo", "bar")
            CopyOnWriteMap.evict(map, 1)
            return null
        }}

        when:
        for (Future<?> f : service.invokeAll(callables)) {
            f.get()
        }
        then:
        noExceptionThrown()

        cleanup:
        service.shutdown()
    }
}
