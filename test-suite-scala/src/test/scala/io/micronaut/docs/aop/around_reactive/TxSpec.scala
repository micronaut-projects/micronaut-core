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

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class TxSpec:

  @Test
  def testTx(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "TxSpec").asJava
    )
    try
      val txManager = context.getBean(classOf[TxManager])
      val exampleBean = context.getBean(classOf[TxExample])
      assertTrue(txManager.getTransactionsLog.isEmpty)

      val results = List(
        exampleBean.doWork("job1").toCompletableFuture.get(),
        exampleBean.doWork("job2").toCompletableFuture.get()
      )

      assertEquals(
        List("OPEN TX1", "IN TX1", "COMMIT TX1", "OPEN TX2", "IN TX2", "COMMIT TX2"),
        txManager.getTransactionsLog
      )
      assertEquals(
        List(
          "Doing job: job1 in transaction: TX1",
          "Doing job: job2 in transaction: TX2"
        ),
        results
      )
    finally
      context.close()
