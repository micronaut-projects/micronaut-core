/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.server.netty.handler.accesslog.element;

import io.micronaut.core.order.Ordered;

public class NotImplementedElementBuilder implements LogElementBuilder {

    private static final String[] NOT_IMPLEMENTED = new String[] { "l", "u" };

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public LogElement build(String token, String param) {
        for (String element: NOT_IMPLEMENTED) {
            if (token.equals(element)) {
                return ConstantElement.UNKNOWN;
            }
        }
        return null;
    }
}
