/*
 * Copyright 2017-2020 original authors
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

import io.kotest.common.runBlocking
import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TxSpec {

    @Test
    fun testTx() = runBlocking {
        ApplicationContext.run().use { applicationContext ->
            val txManager = applicationContext.getBean(
                TxManager::class.java
            )
            val exampleBean = applicationContext.getBean(
                TxExample::class.java
            )
            Assertions.assertTrue(txManager.getTransactionsLog().isEmpty())
            val results: MutableList<String> = ArrayList()
            results.add(
                exampleBean.doWork("job1")
            )
            results.add(
                exampleBean.doWork("job2")
            )
            Assertions.assertEquals(
                listOf("OPEN TX1", "IN TX1", "COMMIT TX1", "OPEN TX2", "IN TX2", "COMMIT TX2"),
                txManager.getTransactionsLog()
            )
            Assertions.assertEquals(
                listOf(
                    "Doing job: job1 in transaction: TX1",
                    "Doing job: job2 in transaction: TX2"
                ), results
            )
        }
    }
}
