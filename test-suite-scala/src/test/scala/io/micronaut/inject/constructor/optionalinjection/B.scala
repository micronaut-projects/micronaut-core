package io.micronaut.inject.constructor.optionalinjection

import java.util.Optional

import javax.inject.Inject

@Inject
class B(val a:Optional[A], val c:Optional[C])