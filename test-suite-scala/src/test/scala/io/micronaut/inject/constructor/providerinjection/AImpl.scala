package io.micronaut.inject.constructor.providerinjection

import javax.inject.Singleton

@Singleton
class AImpl(val c:C, val c2:C) extends A