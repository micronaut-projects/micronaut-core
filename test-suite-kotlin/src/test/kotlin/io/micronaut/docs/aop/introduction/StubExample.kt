package io.micronaut.docs.aop.introduction

import java.time.LocalDateTime

// tag::class[]
@Stub
interface StubExample {

    @get:Stub("10")
    val number: Int

    val date: LocalDateTime?
}
// end::class[]
