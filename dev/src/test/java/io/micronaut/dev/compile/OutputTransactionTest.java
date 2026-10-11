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
package io.micronaut.dev.compile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputTransactionTest {

    @TempDir
    Path directory;

    @Test
    void aReplacedFileIsReplacedInOneRenameAndComesBackOnARollback() throws Exception {
        Path output = Files.createDirectories(directory.resolve("classes"));
        Path target = output.resolve("A.class");
        Files.writeString(target, "old");
        Path staged = Files.createDirectories(directory.resolve("staging")).resolve("A.class");
        Files.writeString(staged, "new");

        OutputTransaction transaction = new OutputTransaction(directory.resolve("backup"));
        transaction.put(staged, target);
        assertEquals("new", Files.readString(target));
        transaction.rollback();
        assertEquals("old", Files.readString(target));
        assertEquals("new", Files.readString(staged));

        transaction = new OutputTransaction(directory.resolve("backup"));
        transaction.put(staged, target);
        transaction.commit();
        assertEquals("new", Files.readString(target));
        assertFalse(Files.exists(directory.resolve("backup")));
    }

    @Test
    void theOutputLockIsReentrantAndExcludesTheOtherThreadsOfTheProcess() throws Exception {
        OutputLock lock = OutputLock.at(directory.resolve("classes.micronaut-dev.lock"));
        AtomicBoolean entered = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        Thread other;
        try (OutputLock.Hold outer = lock.lock(); OutputLock.Hold inner = OutputLock.at(directory.resolve("classes.micronaut-dev.lock")).lock()) {
            other = new Thread(() -> {
                started.countDown();
                try (OutputLock.Hold hold = lock.lock()) {
                    entered.set(true);
                }
            });
            other.start();
            assertTrue(started.await(10, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertFalse(entered.get());
        }
        other.join(10_000);
        assertTrue(entered.get());
        assertTrue(Files.exists(directory.resolve("classes.micronaut-dev.lock")));
    }
}
