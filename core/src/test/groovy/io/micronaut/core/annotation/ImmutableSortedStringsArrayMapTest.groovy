package io.micronaut.core.annotation


import spock.lang.Specification
import spock.lang.Unroll

class ImmutableSortedStringsArrayMapTest extends Specification {
    private static <V> ImmutableSortedStringsArrayMap<V> build(Map<String, V> source) {
        SortedMap<String, V> sorted = new TreeMap<>(source)
        new ImmutableSortedStringsArrayMap<V>(sorted.keySet() as String[], sorted.values() as Object[])
    }

    private static final Random SEED = new Random(20211004)
    private static final Set<String> KEYS = (0..65536).collect {
        int len = 1+SEED.nextInt(32)
        StringBuilder sb = new StringBuilder()
        len.times {
            sb.append((char) (48 + SEED.nextInt(120)))
        }
        sb.toString()
    } as Set<String>

    private static final Set<String> PRESENT = KEYS.indexed().collect([] as Set) { i, v ->
        if (i < KEYS.size()/10) {
            return v
        }
        null
    }.findAll() as Set<String>

    private static final Set<String> ABSENT = KEYS - PRESENT

    @Unroll("Verifies correct behavior of map of size #size")
    void verifyMapBehavior() {
        given:
        Map<String, Object> source = [:]
        size.times { idx ->
            source[PRESENT[idx]] = "value for $idx".toString()
        }
        def sorted = build(source)

        expect:
        sorted.size() == source.size()
        sorted.keySet() == source.keySet()
        source.keySet().each {k ->
            assert sorted.containsKey(k)
            assert sorted[k] == source[k] : "Value returned for key $k is incorrect. Expected ${source[k]} but was: ${sorted[k]}"
        }

        and:
        ABSENT.each {
            assert !sorted.containsKey(it)
        }

        where:
        size << (1..384).findAll { it%3 == 0 }
    }
}
