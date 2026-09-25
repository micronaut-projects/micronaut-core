package io.micronaut.core.util

import spock.lang.Specification

import java.util.function.Function

class ImmutableStringIntMapSpec extends Specification {

    private static final Function<String, String> ID = Function.identity()

    private static String[] names(int count) {
        (0..<count).collect { "name$it".toString() } as String[]
    }

    /**
     * One size in the scan layout and one comfortably in the hash layout.
     */
    private static List<Integer> equalNotIdenticalSizes() {
        int t = ImmutableStringIntMap.LINEAR_SCAN_THRESHOLD
        [t, t + 4]
    }

    /**
     * A duplicate-key case for each layout: a short list with a duplicate for the scan layout,
     * and a list longer than the threshold with a duplicate for the hash layout.
     */
    private static List<List> duplicateKeyCases() {
        int t = ImmutableStringIntMap.LINEAR_SCAN_THRESHOLD
        [
            ["scan", ["a", "a"] as String[]],
            ["hash", (names(t + 2).toList() + ["name0"]) as String[]]
        ]
    }

    def "empty input returns the shared empty instance"() {
        when:
        def map = ImmutableStringIntMap.of(new String[0], ID)

        then:
        map.is(ImmutableStringIntMap.of(new String[0], ID))
        map.get("anything", -1) == -1
        map.isLinearScan()
    }

    def "finds every key and misses unknown keys for size #size"() {
        given:
        String[] keys = names(size)
        def map = ImmutableStringIntMap.of(keys, ID)

        expect:
        (0..<size).every { map.get(keys[it], -1) == it }
        map.get("missing", -1) == -1
        map.get("", -1) == -1
        map.isLinearScan() == (size <= ImmutableStringIntMap.LINEAR_SCAN_THRESHOLD)

        where:
        size << (1..20)
    }

    def "layout switches exactly at the threshold"() {
        given:
        int t = ImmutableStringIntMap.LINEAR_SCAN_THRESHOLD

        expect:
        ImmutableStringIntMap.of(names(t - 1), ID).isLinearScan()
        ImmutableStringIntMap.of(names(t), ID).isLinearScan()
        !ImmutableStringIntMap.of(names(t + 1), ID).isLinearScan()
    }

    def "lookup keys only need to be equal, not identical, for size #size"() {
        given:
        String[] keys = names(size)
        def map = ImmutableStringIntMap.of(keys, ID)

        expect:
        (0..<size).every { map.get(new String(keys[it].toCharArray()), -1) == it }

        where:
        // one size in the scan layout and one in the hash layout
        size << equalNotIdenticalSizes()
    }

    def "duplicate keys are rejected in the #layout layout"() {
        when:
        ImmutableStringIntMap.of(keys, ID)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Duplicate key"

        where:
        [layout, keys] << duplicateKeyCases()
    }

    def "colliding hash codes are all found in the hash layout"() {
        given: '"Aa" and "BB" share a hash code, so every string built from them collides'
        List<String> colliding = ["Aa", "BB"].collectMany { a -> ["Aa", "BB"].collectMany { b -> ["Aa", "BB"].collect { c -> a + b + c } } }
        String[] keys = (colliding + names(ImmutableStringIntMap.LINEAR_SCAN_THRESHOLD + 4).toList()) as String[]

        when:
        def map = ImmutableStringIntMap.of(keys, ID)

        then:
        !map.isLinearScan()
        keys.findAll { it.length() == 6 }*.hashCode().unique().size() == 1
        (0..<keys.length).every { map.get(keys[it], -1) == it }
        map.get("AaAaBC", -1) == -1
    }

    def "uses the key function"() {
        when:
        def map = ImmutableStringIntMap.of([1, 22, 333] as Integer[], { Integer i -> "k" + i } as Function)

        then:
        map.get("k22", -1) == 1
        map.get("22", -1) == -1
    }

    def "null keys from the key function are rejected"() {
        when:
        ImmutableStringIntMap.of(["a"] as String[], { String s -> null } as Function)

        then:
        thrown(NullPointerException)
    }
}
