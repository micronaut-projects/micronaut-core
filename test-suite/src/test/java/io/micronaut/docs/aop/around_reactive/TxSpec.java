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
package io.micronaut.docs.aop.around_reactive;

import io.micronaut.context.ApplicationContext;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

public class TxSpec {

    @Test
    public void testReactiveTx() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            TxManager txManager = applicationContext.getBean(TxManager.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(txManager.getTransactionsLog().isEmpty());

            String job = Mono.from(
                exampleBean.doWorkMono("job1")
            ).block();

            Assertions.assertEquals(List.of("OPEN TX1", "IN TX1", "COMMIT TX1"), txManager.getTransactionsLog());
            Assertions.assertEquals("Doing job: job1 in transaction: TX1", job);
        }
    }

    @Test
    public void testTwoFluxReactiveTx() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            TxManager txManager = applicationContext.getBean(TxManager.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(txManager.getTransactionsLog().isEmpty());
            List<String> results = new ArrayList<>();

            Flux.from(
                exampleBean.doWorkFlux("job1").doOnNext(results::add)
            ).thenMany(
                Mono.from(
                    exampleBean.doWorkFlux("job2").doOnNext(results::add)
                )
            ).collectList().block();

            Assertions.assertEquals(List.of("OPEN TX1", "IN TX1", "COMMIT TX1", "OPEN TX2", "IN TX2", "COMMIT TX2"), txManager.getTransactionsLog());
            Assertions.assertEquals(List.of("Doing job: job1 in transaction: TX1", "Doing job: job2 in transaction: TX2"), results);
        }
    }

    @Test
    public void testTwoMonoReactiveTx() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            TxManager txManager = applicationContext.getBean(TxManager.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(txManager.getTransactionsLog().isEmpty());
            List<String> results = new ArrayList<>();

            Mono.from(
                exampleBean.doWorkMono("job1").doOnNext(results::add)
            ).flatMap(result ->
                Mono.from(
                    exampleBean.doWorkMono("job2").doOnNext(results::add)
                )
            ).block();

            Assertions.assertEquals(List.of("OPEN TX1", "IN TX1", "COMMIT TX1", "OPEN TX2", "IN TX2", "COMMIT TX2"), txManager.getTransactionsLog());
            Assertions.assertEquals(List.of("Doing job: job1 in transaction: TX1", "Doing job: job2 in transaction: TX2"), results);
        }
    }

    @Test
    public void testTwoMonoReactiveTx2() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            TxManager txManager = applicationContext.getBean(TxManager.class);
            TxExample exampleBean = applicationContext.getBean(TxExample.class);

            Assertions.assertTrue(txManager.getTransactionsLog().isEmpty());
            List<String> results = new ArrayList<>();

            Flux.from(
                exampleBean.doWorkMono("job1").doOnNext(results::add)
            ).then(
                Mono.from(
                    exampleBean.doWorkMono("job2").doOnNext(results::add)
                )
            ).block();

            Assertions.assertEquals(List.of("OPEN TX1", "IN TX1", "COMMIT TX1", "OPEN TX2", "IN TX2", "COMMIT TX2"), txManager.getTransactionsLog());
            Assertions.assertEquals(List.of("Doing job: job1 in transaction: TX1", "Doing job: job2 in transaction: TX2"), results);
        }
    }


    // end::test[]
}
