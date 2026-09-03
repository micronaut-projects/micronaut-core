/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.aop.around_reactive

import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import jakarta.inject.Singleton

import java.util.concurrent.CompletionStage
import java.util.function.BiConsumer
import java.util.function.Function
import java.util.function.Supplier
import scala.collection.mutable.ListBuffer

@Requires(property = "spec.name", value = "TxSpec")
@Singleton
class TxManager:
  private var txCount = 0
  private val transactionsLog = ListBuffer.empty[String]

  def findTx(): String =
    val propagatedContext = PropagatedContext.find().orElseThrow()
    propagatedContext.find(classOf[TxPropagatedContext]).orElseThrow().tx

  def getTransactionsLog: List[String] =
    transactionsLog.toList

  def inTransaction[T](fn: Function[String, CompletionStage[T]]): CompletionStage[T] =
    val tx = newTransaction()
    transactionsLog += s"OPEN $tx"
    PropagatedContext.getOrEmpty().plus(TxPropagatedContext(tx)).propagate(new Supplier[CompletionStage[T]]:
      override def get(): CompletionStage[T] =
        transactionsLog += s"IN $tx"
        fn.apply(tx).whenComplete(new BiConsumer[T, Throwable]:
          override def accept(value: T, throwable: Throwable): Unit =
            if throwable != null then
              transactionsLog += s"ROLLBACK $tx"
            else
              transactionsLog += s"COMMIT $tx"
        )
    )

  private def newTransaction(): String =
    txCount = txCount + 1
    s"TX$txCount"

private case class TxPropagatedContext(tx: String) extends PropagatedContextElement
