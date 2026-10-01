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
package io.micronaut.dev;

import io.micronaut.dev.test.TestRunSummary;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * The keys of test mode on a terminal: space runs the last tests again, {@code a} every test, {@code f} the failures,
 * {@code w} turns watching on or off, and {@code q} exits with the last run's status. Where the terminal can be put in
 * character mode, through {@code stty} on Unix, a key acts at once; otherwise it acts on Enter.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
final class TestConsole {

    private static final Logger LOG = LoggerFactory.getLogger(TestConsole.class);
    private static final String BANNER = "Continuous testing: SPACE runs the last tests again, 'a' every test, 'f' the failures, 'w' turns watching on or off, 'q' exits.";

    private final DevRuntime runtime;
    private final InputStream input;

    TestConsole(DevRuntime runtime, InputStream input) {
        this.runtime = runtime;
        this.input = input;
    }

    /**
     * Starts reading keys when the process has a terminal.
     *
     * @param runtime The runtime in test mode
     */
    static void startIfInteractive(DevRuntime runtime) {
        if (System.console() == null) {
            return;
        }
        boolean characterMode = characterMode(true);
        if (characterMode) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> characterMode(false), "micronaut-dev-terminal"));
        }
        System.out.println(BANNER + (characterMode ? "" : " Press Enter after the key."));
        Thread thread = new Thread(() -> new TestConsole(runtime, System.in).read(), "micronaut-dev-test-console");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Reads keys until the input ends or {@code q}.
     */
    void read() {
        try {
            int key;
            while ((key = input.read()) >= 0) {
                if (!act((char) key)) {
                    return;
                }
            }
        } catch (IOException e) {
            LOG.debug("The terminal input ended: {}", e.getMessage(), e);
        }
    }

    /**
     * Acts on a key.
     *
     * @param key The key
     * @return Whether to go on reading
     */
    boolean act(char key) {
        switch (Character.toLowerCase(key)) {
            case ' ' -> runtime.requestTests(TestRequest.RERUN);
            case 'a' -> runtime.requestTests(TestRequest.ALL);
            case 'f' -> {
                if (runtime.failedTestClasses().isEmpty()) {
                    System.out.println("No test failed in its last run.");
                } else {
                    runtime.requestTests(TestRequest.FAILED);
                }
            }
            case 'w' -> {
                boolean watching = !runtime.isWatchingTests();
                runtime.watchTests(watching);
                System.out.println(watching ? "Watching: a change runs the tests it affects." : "Not watching: changes compile, and no test runs until asked.");
            }
            case 'q' -> {
                TestRunSummary last = runtime.lastTestRun().orElse(null);
                runtime.close();
                characterMode(false);
                System.exit(last != null && last.isSuccess() ? 0 : 1);
                return false;
            }
            default -> {
                // Enter, after a key on a terminal in line mode, and any other key
            }
        }
        return true;
    }

    /**
     * Puts the terminal in character mode without echo, or back, through {@code stty} on the controlling terminal.
     *
     * @return Whether it worked
     */
    private static boolean characterMode(boolean on) {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return false;
        }
        String settings = on ? "-icanon min 1 -echo" : "icanon echo";
        try {
            Process process = new ProcessBuilder("/bin/sh", "-c", "stty " + settings + " < /dev/tty").redirectErrorStream(true).start();
            return process.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
