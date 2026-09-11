package io.micronaut.core.util

import spock.lang.Specification

class EnumConstantsMapSpec extends Specification {

    enum Colour {
        RED, GREEN, BLUE, YELLOW
    }

    enum Size {
        S, M, L, XL, XXL
    }

    enum Operation {
        PLUS {
            @Override
            int apply(int a, int b) {
                a + b
            }
        },
        MINUS {
            @Override
            int apply(int a, int b) {
                a - b
            }
        }

        abstract int apply(int a, int b)
    }

    void "put, get, remove, containsKey, containsValue, size and clear"() {
        given:
        Map<Colour, String> map = CollectionUtils.newEnumMap(Colour.values())

        expect:
        map.isEmpty()
        map.size() == 0
        map.get(Colour.RED) == null
        !map.containsKey(Colour.RED)
        !map.containsValue(null)

        when:
        def previous = map.put(Colour.BLUE, "blue")

        then:
        previous == null
        map.size() == 1
        !map.isEmpty()
        map.get(Colour.BLUE) == "blue"
        map.containsKey(Colour.BLUE)
        !map.containsKey(Colour.RED)
        map.containsValue("blue")
        !map.containsValue("red")

        when:
        previous = map.put(Colour.BLUE, "navy")

        then:
        previous == "blue"
        map.size() == 1
        map.get(Colour.BLUE) == "navy"

        when:
        map.put(Colour.RED, "red")
        previous = map.remove(Colour.BLUE)

        then:
        previous == "navy"
        map.size() == 1
        !map.containsKey(Colour.BLUE)
        map.remove(Colour.BLUE) == null
        map.size() == 1

        when:
        map.clear()

        then:
        map.isEmpty()
        map.get(Colour.RED) == null
    }

    void "null values are stored and distinguished from absent keys"() {
        given:
        Map<Colour, String> map = CollectionUtils.newEnumMap(Colour.values())

        when:
        def previous = map.put(Colour.GREEN, null)

        then:
        previous == null
        map.size() == 1
        map.containsKey(Colour.GREEN)
        map.get(Colour.GREEN) == null
        map.containsValue(null)
        !map.containsKey(Colour.RED)
        render(map) == "GREEN=null"

        when:
        previous = map.put(Colour.GREEN, "green")

        then:
        previous == null
        map.size() == 1
        !map.containsValue(null)

        when:
        map.put(Colour.GREEN, null)
        previous = map.remove(Colour.GREEN)

        then:
        previous == null
        map.isEmpty()
        !map.containsKey(Colour.GREEN)
    }

    void "a null key is rejected on put and absent on read"() {
        given:
        Map<Colour, String> map = CollectionUtils.newEnumMap(Colour.values())

        when:
        map.put(null, "x")

        then:
        thrown(NullPointerException)

        and:
        map.get(null) == null
        !map.containsKey(null)
        map.remove(null) == null
        map.isEmpty()
    }

    void "a key of another enum is absent on read and rejected on put"() {
        given:
        Map<Colour, String> map = CollectionUtils.newEnumMap(Colour.values())
        map.put(Colour.RED, "red")
        map.put(Colour.YELLOW, "yellow")

        expect: "the same ordinal, and an ordinal beyond the universe"
        map.get(Size.S) == null
        map.get(Size.XXL) == null
        !map.containsKey(Size.S)
        !map.containsKey(Size.XXL)
        map.remove(Size.S) == null
        map.get("RED") == null
        map.size() == 2

        when:
        ((Map) map).put(Size.S, "small")

        then:
        thrown(ClassCastException)

        when:
        ((Map) map).put(Size.XXL, "huge")

        then:
        thrown(ClassCastException)
        map.size() == 2
        map.get(Colour.RED) == "red"
    }

    void "iteration is in ordinal order regardless of insertion order"() {
        given:
        Map<Size, Integer> map = CollectionUtils.newEnumMap(Size.values())
        map.put(Size.XXL, 5)
        map.put(Size.S, 1)
        map.put(Size.L, 3)
        map.put(Size.M, 2)

        expect:
        map.entrySet().collect { it.key } == [Size.S, Size.M, Size.L, Size.XXL]
        new ArrayList<>(map.keySet()) == [Size.S, Size.M, Size.L, Size.XXL]
        new ArrayList<>(map.values()) == [1, 2, 3, 5]
        render(map) == "S=1, M=2, L=3, XXL=5"

        when:
        def visited = []
        map.forEach { k, v -> visited << k << v }

        then:
        visited == [Size.S, 1, Size.M, 2, Size.L, 3, Size.XXL, 5]
    }

    void "Iterator.remove removes the last returned mapping"() {
        given:
        Map<Size, Integer> map = CollectionUtils.newEnumMap(Size.values())
        Size.values().each { map.put(it, it.ordinal()) }

        when:
        def iterator = map.entrySet().iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value % 2 == 0) {
                iterator.remove()
            }
        }

        then:
        render(map) == "M=1, XL=3"
        map.size() == 2

        when: "remove is called twice"
        iterator = map.keySet().iterator()
        iterator.next()
        iterator.remove()
        iterator.remove()

        then:
        thrown(IllegalStateException)
        render(map) == "XL=3"

        when:
        iterator = map.values().iterator()
        iterator.next()
        iterator.remove()

        then:
        map.isEmpty()
        !iterator.hasNext()

        when:
        iterator.next()

        then:
        thrown(NoSuchElementException)
    }

    void "Entry.setValue writes through to the map"() {
        given:
        Map<Colour, String> map = CollectionUtils.newEnumMap(Colour.values())
        map.put(Colour.RED, "red")
        map.put(Colour.BLUE, "blue")

        when:
        def entry = map.entrySet().iterator().next()
        def previous = entry.setValue("crimson")

        then:
        previous == "red"
        entry.key == Colour.RED
        entry.value == "crimson"
        map.get(Colour.RED) == "crimson"

        when:
        entry.setValue(null)

        then:
        map.containsKey(Colour.RED)
        map.get(Colour.RED) == null
        entry.equals(new AbstractMap.SimpleEntry(Colour.RED, null))
        entry.hashCode() == new AbstractMap.SimpleEntry(Colour.RED, null).hashCode()
    }

    void "the collection views remove from the map"() {
        given:
        Map<Colour, String> map = CollectionUtils.newEnumMap(Colour.values())
        Colour.values().each { map.put(it, it.name().toLowerCase()) }

        expect:
        map.keySet().remove(Colour.RED)
        !map.keySet().remove(Colour.RED)
        !map.keySet().remove(Size.M)
        map.values().remove("green")
        map.entrySet().remove(new AbstractMap.SimpleEntry(Colour.BLUE, "blue"))
        !map.entrySet().remove(new AbstractMap.SimpleEntry(Colour.YELLOW, "other"))
        map.entrySet().contains(new AbstractMap.SimpleEntry(Colour.YELLOW, "yellow"))
        map.keySet().contains(Colour.YELLOW)
        map.values().contains("yellow")
        render(map) == "YELLOW=yellow"

        when:
        map.entrySet().clear()

        then:
        map.isEmpty()
    }

    void "equals and hashCode agree with EnumMap and HashMap holding the same mappings"() {
        given:
        Map<Colour, String> map = CollectionUtils.newEnumMap(Colour.values())
        map.put(Colour.YELLOW, "yellow")
        map.put(Colour.RED, null)
        def enumMap = new EnumMap<Colour, String>(Colour)
        enumMap.putAll(map)
        def hashMap = new HashMap<Colour, String>(map)
        Map<Colour, String> other = CollectionUtils.newEnumMap(Colour.values())
        other.put(Colour.RED, null)
        other.put(Colour.YELLOW, "yellow")

        expect:
        map.equals(enumMap)
        enumMap.equals(map)
        map.equals(hashMap)
        hashMap.equals(map)
        map.equals(other)
        other.equals(map)
        map.hashCode() == enumMap.hashCode()
        map.hashCode() == hashMap.hashCode()
        map.hashCode() == other.hashCode()

        when: "an absent key and a null value differ"
        other.remove(Colour.RED)
        other.put(Colour.GREEN, null)

        then:
        !map.equals(other)
        !other.equals(map)

        when:
        hashMap.put(Colour.RED, "red")

        then:
        !map.equals(hashMap)
        !hashMap.equals(map)

        and: "empty maps of different enums are equal, as for any maps"
        CollectionUtils.newEnumMap(Colour.values()).equals(CollectionUtils.newEnumMap(Size.values()))
    }

    void "putAll copies from a map of the same enum and from other maps"() {
        given:
        Map<Colour, String> source = CollectionUtils.newEnumMap(Colour.values())
        source.put(Colour.GREEN, "green")
        source.put(Colour.YELLOW, null)
        Map<Colour, String> map = CollectionUtils.newEnumMap(Colour.values())
        map.put(Colour.RED, "red")
        map.put(Colour.GREEN, "lime")

        when:
        map.putAll(source)

        then:
        map.size() == 3
        render(map) == "RED=red, GREEN=green, YELLOW=null"

        when:
        map.putAll([(Colour.BLUE): "blue"])

        then:
        map.size() == 4
        map.get(Colour.BLUE) == "blue"

        when:
        Map<Size, String> sizes = CollectionUtils.newEnumMap(Size.values())
        sizes.put(Size.S, "small")
        ((Map) map).putAll(sizes)

        then:
        thrown(ClassCastException)
    }

    void "a map of an enum with constant-specific bodies"() {
        given:
        Map<Operation, String> map = CollectionUtils.newEnumMap(Operation.values())

        when:
        map.put(Operation.MINUS, "-")
        map.put(Operation.PLUS, "+")

        then:
        render(map) == "PLUS=+, MINUS=-"
        map.equals(new EnumMap<>(map))
    }

    void "the factory rejects an array that is not shaped as values() returns it"() {
        when:
        CollectionUtils.newEnumMap(constants as Enum[])

        then:
        thrown(expected)

        where:
        constants                              | expected
        [Colour.GREEN, Colour.RED]             | IllegalArgumentException
        [Colour.RED, Colour.BLUE]              | IllegalArgumentException
        [Colour.RED, null]                     | IllegalArgumentException
        [Colour.RED, Size.M]                   | IllegalArgumentException
        [Colour.RED, Colour.GREEN, Colour.RED] | IllegalArgumentException
    }

    void "the factory rejects a null array and accepts an empty one"() {
        when:
        CollectionUtils.newEnumMap(null)

        then:
        thrown(NullPointerException)

        expect:
        CollectionUtils.newEnumMap(new Colour[0]).isEmpty()
    }

    void "behaves as EnumMap over a randomised sequence of operations"() {
        given:
        def random = new Random(seed)
        Map<Size, String> map = CollectionUtils.newEnumMap(Size.values())
        def expected = new EnumMap<Size, String>(Size)
        def sizes = Size.values()
        def values = [null, "a", "b", "c"]
        def keys = (sizes as List<Object>) + [Colour.RED, Colour.YELLOW, null, "S"]

        when:
        10_000.times { step ->
            def key = sizes[random.nextInt(sizes.length)]
            def anyKey = keys[random.nextInt(keys.size())]
            def value = values[random.nextInt(values.size())]
            switch (random.nextInt(12)) {
                case 0:
                case 1:
                    assert map.put(key, value) == expected.put(key, value)
                    break
                case 2:
                    assert map.remove(anyKey) == expected.remove(anyKey)
                    break
                case 3:
                    assert map.get(anyKey) == expected.get(anyKey)
                    assert map.containsKey(anyKey) == expected.containsKey(anyKey)
                    assert map.getOrDefault(anyKey, "default") == expected.getOrDefault(anyKey, "default")
                    break
                case 4:
                    assert map.containsValue(value) == expected.containsValue(value)
                    break
                case 5:
                    def source = [:]
                    source.put(key, value)
                    source.put(sizes[random.nextInt(sizes.length)], values[random.nextInt(values.size())])
                    map.putAll(source)
                    expected.putAll(source)
                    break
                case 6:
                    Map<Size, String> source = CollectionUtils.newEnumMap(Size.values())
                    source.put(key, value)
                    map.putAll(source)
                    expected.putAll(source)
                    break
                case 7:
                    def target = random.nextInt(sizes.length)
                    removeWhere(map.entrySet().iterator()) { it.key.ordinal() == target }
                    removeWhere(expected.entrySet().iterator()) { it.key.ordinal() == target }
                    break
                case 8:
                    setWhere(map.entrySet().iterator(), key, value)
                    setWhere(expected.entrySet().iterator(), key, value)
                    break
                case 9:
                    assert map.keySet().remove(anyKey) == expected.keySet().remove(anyKey)
                    assert map.values().remove(value) == expected.values().remove(value)
                    break
                case 10:
                    assert map.entrySet().remove(new AbstractMap.SimpleEntry(key, value)) ==
                            expected.entrySet().remove(new AbstractMap.SimpleEntry(key, value))
                    break
                case 11:
                    if (random.nextInt(20) == 0) {
                        map.clear()
                        expected.clear()
                    }
                    break
            }
            assertSame(map, expected, step)
        }

        then:
        noExceptionThrown()

        where:
        seed << [1L, 42L, 20260911L]
    }

    private static String render(Map<?, ?> map) {
        map.entrySet().collect { entry -> "${entry.key}=${entry.value}" }.join(", ")
    }

    private static void removeWhere(Iterator<Map.Entry<Size, String>> iterator, Closure<Boolean> predicate) {
        while (iterator.hasNext()) {
            if (predicate.call(iterator.next())) {
                iterator.remove()
            }
        }
    }

    private static void setWhere(Iterator<Map.Entry<Size, String>> iterator, Size key, String value) {
        while (iterator.hasNext()) {
            def entry = iterator.next()
            if (entry.key == key) {
                entry.setValue(value)
            }
        }
    }

    private static void assertSame(Map<Size, String> map, EnumMap<Size, String> expected, int step) {
        assert map.size() == expected.size(), "step $step"
        assert map.isEmpty() == expected.isEmpty()
        assert render(map) == render(expected)
        assert new ArrayList<>(map.keySet()) == new ArrayList<>(expected.keySet())
        assert new ArrayList<>(map.values()) == new ArrayList<>(expected.values())
        assert map.equals(expected)
        assert expected.equals(map)
        assert map.hashCode() == expected.hashCode()
    }
}
