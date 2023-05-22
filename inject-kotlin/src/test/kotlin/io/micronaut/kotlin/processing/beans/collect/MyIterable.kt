package io.micronaut.kotlin.processing.beans.collect

import jakarta.inject.Singleton

@Singleton
class MyIterable : Iterable<String?> {
    override fun iterator(): MutableIterator<String?> {
        return object : MutableIterator<String?> {
            override fun hasNext(): Boolean {
                return false
            }

            override fun next(): String? {
                return null
            }

            override fun remove() {

            }
        }
    }
}
