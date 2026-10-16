package io.micronaut.core.util

import spock.lang.Specification

import java.util.function.Function
import java.util.function.Supplier

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
            ["scan", ["a", "a"] as String[], "a"],
            ["hash", (names(t + 2).toList() + ["name0"]) as String[], "name0"]
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

    def "duplicate keys are rejected in the #layout layout, naming the key"() {
        when:
        ImmutableStringIntMap.of(keys, ID)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Duplicate key [$duplicate]".toString()

        where:
        [layout, keys, duplicate] << duplicateKeyCases()
    }

    def "a duplicate key message names what is indexed in the #layout layout"() {
        when:
        ImmutableStringIntMap.of(keys, ID, false, { "the test items" } as Supplier<String>)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Duplicate key [$duplicate] in the test items".toString()

        where:
        [layout, keys, duplicate] << duplicateKeyCases()
    }

    def "skipDuplicates keeps only the first mapping of a key in the #layout layout"() {
        when:
        def map = ImmutableStringIntMap.of(keys, ID, true, null)

        then:
        map.isLinearScan() == (layout == "scan")
        (0..<keys.length).every { map.get(keys[it], -1) == keys.findIndexOf { k -> k == keys[it] } }
        map.get("missing", -1) == -1

        where:
        layout | keys
        "scan" | ["a", "b", "a", "c"] as String[]
        "hash" | (names(ImmutableStringIntMap.LINEAR_SCAN_THRESHOLD + 2).toList() + ["name0", "name3"]) as String[]
    }

    def "the description of what is indexed is only built for a duplicate"() {
        when:
        def map = ImmutableStringIntMap.of(names(size), ID, false, { throw new AssertionError("described") } as Supplier<String>)

        then:
        map.get("name0", -1) == 0

        where:
        size << [1, ImmutableStringIntMap.LINEAR_SCAN_THRESHOLD + 1]
    }

    def "numbered names, whose hash codes differ only in their low bits, are all found"() {
        given: 'without spreading the hash, field1..field24 pile into a few slots of a 64-slot table'
        String[] keys = (1..24).collect { "field$it".toString() } as String[]

        when:
        def map = ImmutableStringIntMap.of(keys, ID)

        then:
        !map.isLinearScan()
        (0..<keys.length).every { map.get(new String(keys[it].toCharArray()), -1) == it }
        map.get("field25", -1) == -1
        map.get("field0", -1) == -1
    }

    def "a null key is rejected by a map of #size keys"() {
        when:
        ImmutableStringIntMap.of(names(size), ID).get(null, -1)

        then:
        thrown(NullPointerException)

        where:
        size << [0, 1, ImmutableStringIntMap.LINEAR_SCAN_THRESHOLD, ImmutableStringIntMap.LINEAR_SCAN_THRESHOLD + 1]
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
