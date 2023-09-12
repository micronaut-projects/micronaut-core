package io.micronaut.inject.beans.intro;

public interface AInterface<K, V> {

    void set(K k, V v);

    String get(K k, V v);
}
