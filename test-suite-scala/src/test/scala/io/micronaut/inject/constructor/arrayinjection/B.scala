package io.micronaut.inject.constructor.arrayinjection

import javax.inject.Inject

@Inject
class B(val a:Array[A])