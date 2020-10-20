package io.micronaut.inject.generics

import javax.inject.Singleton

@Singleton
class ScalaClassA extends ScalaClassB[String]
