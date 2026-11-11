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
package io.micronaut.dev.agent;

import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DevAgentTest {

    @Test
    void anAgentLoadedAtLaunchOrAttachedLaterRecordsTheInstrumentation() {
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(DevAgentTest.class.getClassLoader(),
            new Class<?>[] {Instrumentation.class}, (proxy, method, args) -> null);
        // the first one recorded stays, whichever way it came: a test of the fast path may have attached already
        DevAgent.premain(null, instrumentation);
        DevAgent.agentmain("", instrumentation);
        assertNotNull(DevAgent.instrumentation());
    }
}
