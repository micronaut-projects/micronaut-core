package io.micronaut.inject.constructor.streaminjection

import javax.inject.Inject

@Inject
class B(val all:java.util.stream.Stream[A])
