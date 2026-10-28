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
package io.micronaut.dev.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.http.netty.channel.DefaultEventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.NioEventLoopGroupFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The retained event loop groups a generation asks for by name.
 */
class DevEventLoopGroupsTest {

    @Test
    void aGroupAskedForAgainWithOtherSettingsWhileClaimedReplacesTheFirstWithAWarning() {
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        Logger logger = (Logger) LoggerFactory.getLogger(DevEventLoopGroups.class);
        logger.addAppender(logged);
        DevEventLoopGroups groups = new DevEventLoopGroups();
        try {
            NioEventLoopGroupFactory factory = new NioEventLoopGroupFactory();
            EventLoopGroup first = groups.eventLoopGroup("custom", configuration(1), factory, DevEventLoopGroupsTest::newGroup);
            assertSame(first, groups.eventLoopGroup("custom", configuration(1), factory, DevEventLoopGroupsTest::newGroup));
            assertEquals(List.of(), warnings(logged));

            EventLoopGroup second = groups.eventLoopGroup("custom", configuration(2), factory, DevEventLoopGroupsTest::newGroup);
            assertNotSame(first, second);
            assertTrue(first.isShuttingDown());
            assertEquals(1, warnings(logged).size(), warnings(logged).toString());
            assertTrue(warnings(logged).get(0).contains("[custom]"));

            // the next generation makes its own without a warning, as no registry of it holds the group
            groups.generationEnded();
            EventLoopGroup third = groups.eventLoopGroup("custom", configuration(3), factory, DevEventLoopGroupsTest::newGroup);
            assertNotSame(second, third);
            assertEquals(1, warnings(logged).size(), warnings(logged).toString());
        } finally {
            groups.close();
            logger.detachAppender(logged);
        }
    }

    private static List<String> warnings(ListAppender<ILoggingEvent> logged) {
        return logged.list.stream().filter(event -> event.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static EventLoopGroupConfiguration configuration(int threads) {
        return new DefaultEventLoopGroupConfiguration("custom", threads, null, false, null, Duration.ZERO, Duration.ofSeconds(1));
    }

    private static EventLoopGroup newGroup() {
        return new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    }
}
