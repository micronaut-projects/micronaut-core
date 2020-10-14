package io.micronaut.inject.constructor.optionalinjection

import javax.inject.Inject

@Inject
class BScala(val a:Option[A], val c:Option[C])
