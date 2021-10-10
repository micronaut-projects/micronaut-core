package io.micronaut.docs.basics

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

class Book {
    var title: String? = null

    @JsonCreator
    constructor(@JsonProperty("title") title: String) {
        this.title = title
    }

    internal constructor() {}
}
