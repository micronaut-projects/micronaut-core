package io.micronaut.inject.constructor.collectioninjection

import javax.inject.Inject

import scala.collection.mutable.ListBuffer

@Inject
class BScala(val a:ListBuffer[A])