/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.cli;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.naming.Described;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.runtime.server.EmbeddedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultApplicationManager implements ApplicationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultApplicationManager.class);

    private final Lock lock = new ReentrantLock();
    private final AtomicReference<ApplicationContext> applicationContextRef = new AtomicReference<>();

    @Override
    public void startApplication(String[] args) {
        lock.lock();
        try {
            var current = applicationContextRef.get();
            if (current != null) {
                throw new IllegalStateException("Application context already started");
            }
            long start = System.nanoTime();
            ApplicationContext currentContext;
            try {
                currentContext = ApplicationContext.builder()
                    .args(args)
                    .classLoader(this.getClass().getClassLoader())
                    .start();
                currentContext.findBean(EmbeddedApplication.class)
                    .ifPresent(embeddedApplication -> {
                        embeddedApplication.start();
                        if (embeddedApplication instanceof Described described) {
                            if (LOGGER.isInfoEnabled()) {
                                long took = elapsedMillis(start);
                                String desc = described.getDescription();
                                LOGGER.info("Startup completed in {}ms. Server Running: {}", took,
                                    desc);
                            }
                        } else {
                            if (embeddedApplication instanceof EmbeddedServer embeddedServer) {
                                if (LOGGER.isInfoEnabled()) {
                                    long took = elapsedMillis(start);
                                    Object uri;
                                    try {
                                        uri = embeddedServer.getContextURI();
                                    } catch (UnsupportedOperationException e) {
                                        uri = "<URI display not available: " + e.getMessage() + ">";
                                    }
                                    LOGGER.info("Startup completed in {}ms. Server Running: {}",
                                        took, uri);
                                }
                            } else {
                                if (LOGGER.isInfoEnabled()) {
                                    long took = elapsedMillis(start);
                                    LOGGER.info("Startup completed in {}ms.", took);
                                }
                            }
                        }
                    });
                System.out.println("Application started");
                applicationContextRef.set(currentContext);
            } catch (Exception e) {
                System.err.println("Failed to start application: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stopApplication() {
        lock.lock();
        try {
            var app = applicationContextRef.get();
            if (app != null) {
                app.close();
                System.out.println("Application stopped");
            } else {
                throw new IllegalStateException("Cannot close application context which was not started");
            }
        } finally {
            applicationContextRef.set(null);
            lock.unlock();
        }
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }
}
