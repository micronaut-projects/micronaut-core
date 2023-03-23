package io.micronaut.core.util

import spock.lang.Specification

class CollectionUtilsSpec extends Specification {

    void "test isEmpty for collection"(List<String> list) {
        expect:
        CollectionUtils.isEmpty(list as List<String>) == expected

        where:
        list || expected
        [''] || false
        []   || true
        null || true
    }

    void "test isNotEmpty for collection"(List<String> list) {
        expect:
        CollectionUtils.isNotEmpty(list as List<String>) == expected

        where:
        list || expected
        [''] || true
        []   || false
        null || false
    }

    void "test isNotEmpty for map"(Map<String, String> map) {
        expect:
        CollectionUtils.isNotEmpty(map as Map<String, String> ) == expected

        where:
        map          || expected
        [foo: 'bar'] || true
        [:]          || false
        null         || false
    }

    void "test isEmpty for map"(Map<String, String> map) {
        expect:
        CollectionUtils.isEmpty(map  as Map<String, String>) == expected

        where:
        map          || expected
        [foo: 'bar'] || false
        [:]          || true
        null         || true
    }
}
