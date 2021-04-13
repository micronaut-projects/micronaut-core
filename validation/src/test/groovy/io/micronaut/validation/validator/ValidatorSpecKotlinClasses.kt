package io.micronaut.validation.validator

import javax.validation.constraints.Size

class BookKotlin(val authors: List<@Size(min=2, max=10) String>) {

}

