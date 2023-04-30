package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.aop.Introduction
import io.micronaut.context.annotation.Type

@Introduction
@Type(StubIntroducer::class)
@MustBeDocumented
@Retention
annotation class Stub(val value: String = "")
