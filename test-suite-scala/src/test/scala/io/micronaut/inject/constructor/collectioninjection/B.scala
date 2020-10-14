package io.micronaut.inject.constructor.collectioninjection

import javax.inject.Inject

@Inject
class B(val a:java.util.Collection[A])