package io.micronaut.validation.validator

import org.junit.Assert.assertEquals
import org.junit.Test
import spock.lang.Specification
import javax.validation.constraints.Size


//class ValidatorSpecKotlin() : Specification() {
//    @Test
//    fun testKotlin() {
//        assertEquals(1, 1);
//    }
//
//}

class BookKotlin(val authors: List<@Size(min=2, max=10) String>) {

}

