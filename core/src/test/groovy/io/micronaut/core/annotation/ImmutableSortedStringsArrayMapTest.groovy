package io.micronaut.core.annotation


import spock.lang.Specification
import spock.lang.Unroll
import spock.lang.Shared

class ImmutableSortedStringsArrayMapTest extends Specification {
    private static <V> ImmutableSortedStringsArrayMap<V> build(Map<String, V> source) {
        SortedMap<String, V> sorted = new TreeMap<>(source)
        new ImmutableSortedStringsArrayMap<V>(sorted.keySet() as String[], sorted.values() as Object[])
    }

    @Shared
    private Set<String> present

    @Shared
    private Set<String> absent

    def setupSpec() {
        Random seed = new Random(20211004)

        def keys = (0..65536).collect {
            int len = 1 + seed.nextInt(32)
            StringBuilder sb = new StringBuilder()
            len.times {
                sb.append((char) (48 + seed.nextInt(120)))
            }
            sb.toString()
        } as Set<String>

        present = keys.indexed().collect([] as Set) { i, v ->
            if (i < keys.size() / 10) {
                return v
            }
            null
        }.findAll() as Set<String>

        keys.removeAll(present)
        absent = keys
    }

    @Unroll("Verifies correct behavior of map of size #size")
    void verifyMapBehavior() {
        given:
        Map<String, Object> source = [:]
        size.times { idx ->
            source[present[idx]] = "value for $idx".toString()
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
        absent.each {
            assert !sorted.containsKey(it)
        }

        where:
        size << (1..384).findAll { it%3 == 0 }
    }
}
