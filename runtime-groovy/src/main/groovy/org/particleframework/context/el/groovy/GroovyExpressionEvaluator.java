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
package org.particleframework.context.el.groovy;

import org.particleframework.context.annotation.Primary;
import org.particleframework.context.el.ExpressionEvaluator;

import javax.inject.Singleton;
import java.util.Map;

/**
 * Implementation of the {@link ExpressionEvaluator} interface that uses Groovy to evaluate expressions
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Primary
public class GroovyExpressionEvaluator implements ExpressionEvaluator {
    @Override
    public <T> T evaluate(Class<?> bindingBean, String expression, Class<T> expectedType) {
        return null;
    }

    @Override
    public <T> T evaluate(Map<String, Object> variables, String expression, Class<T> expectedType) {
        return null;
    }
}
