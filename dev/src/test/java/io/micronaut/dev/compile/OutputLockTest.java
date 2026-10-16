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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputLockTest {

    @TempDir
    Path directory;

    @Test
    // Windows cannot delete a file open in another process and create it again at once
    @DisabledOnOs(OS.WINDOWS)
    void aLockFileReplacedWhileWaitingIsLockedAgain() throws Exception {
        Path lockFile = directory.resolve("classes.micronaut-dev.lock");
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path classes = Path.of(OutputLockTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process holder = new ProcessBuilder(javaHome.resolve("bin").resolve("java").toString(), "-cp", classes.toString(),
            Holder.class.getName(), lockFile.toString())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
        try (BufferedReader out = new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
             Writer in = holder.outputWriter(StandardCharsets.UTF_8)) {
            assertEquals("locked", out.readLine());

            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CompletableFuture<Void> waiting = CompletableFuture.runAsync(() -> {
                try (OutputLock.Hold hold = OutputLock.at(lockFile).lock()) {
                    locked.countDown();
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            // let it open the file and wait on its lock
            Thread.sleep(500);
            assertEquals(1, locked.getCount());

            // a clean: the file is deleted and created again while locked, then the old lock is released
            in.write("replace\n");
            in.flush();
            assertEquals("replaced", out.readLine());

            assertTrue(locked.await(30, TimeUnit.SECONDS));
            in.write("check\n");
            in.flush();
            assertEquals("held", out.readLine(), "the lock of the file the path names now is held");
            release.countDown();
            waiting.get(30, TimeUnit.SECONDS);
            in.write("check\n");
            in.flush();
            assertEquals("free", out.readLine());
        } finally {
            assertTrue(holder.waitFor(30, TimeUnit.SECONDS));
        }
    }

    /**
     * The other process: holds the lock of the file, replaces it on request and then reports whether the lock of the
     * new file is held.
     */
    static final class Holder {
        public static void main(String[] args) throws Exception {
            Path lockFile = Path.of(args[0]);
            PrintStream out = System.out;
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            channel.lock();
            out.println("locked");
            out.flush();
            in.readLine();
            Files.delete(lockFile);
            Files.createFile(lockFile);
            channel.close();
            out.println("replaced");
            out.flush();
            for (int i = 0; i < 2; i++) {
                in.readLine();
                try (FileChannel current = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
                    FileLock taken = current.tryLock();
                    out.println(taken == null ? "held" : "free");
                    out.flush();
                }
            }
        }
    }
}
