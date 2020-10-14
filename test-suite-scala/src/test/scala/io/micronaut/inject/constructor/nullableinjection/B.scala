package io.micronaut.inject.constructor.nullableinjection

import javax.annotation.Nullable
import javax.inject.Inject

@Inject
class B(@Nullable val a:A)