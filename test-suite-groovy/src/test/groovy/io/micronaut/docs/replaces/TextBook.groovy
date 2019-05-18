package io.micronaut.docs.replaces

import groovy.transform.TupleConstructor
import io.micronaut.docs.requires.Book

@TupleConstructor
class TextBook extends Book {
    String title
}
