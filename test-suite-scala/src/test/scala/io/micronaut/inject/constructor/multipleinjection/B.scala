package io.micronaut.inject.constructor.multipleinjection

import javax.inject.Inject

@Inject
class B(val a:A, val c:C)