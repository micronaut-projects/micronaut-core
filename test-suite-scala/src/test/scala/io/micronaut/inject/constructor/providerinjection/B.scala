package io.micronaut.inject.constructor.providerinjection

import javax.inject.{Inject, Provider}

@Inject
class B(val a:Provider[A])
