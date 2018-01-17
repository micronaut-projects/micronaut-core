/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.runtime.context.el;

import org.particleframework.context.el.ExpressionEvaluator;

import java.util.Map;

/**
 * TODO: Javadoc description
 *
 * @author graemerocher
 * @since 1.0
 */
public class JavaExpressionEvaluator implements ExpressionEvaluator {
    @Override
    public <T> T evaluate(Class<?> bindingBean, String expression, Class<T> expectedType) {
        return null;
    }

    @Override
    public <T> T evaluate(Map<String, Object> variables, String expression, Class<T> expectedType) {
        return null;
    }
}
