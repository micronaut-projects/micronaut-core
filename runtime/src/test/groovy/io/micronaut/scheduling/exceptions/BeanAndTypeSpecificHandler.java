/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.scheduling.exceptions;

import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.scheduling.TaskExceptionHandler;

import javax.inject.Singleton;

@Singleton
public class BeanAndTypeSpecificHandler implements TaskExceptionHandler<ThrowsExceptionJob1, InstantiationException> {
    private ThrowsExceptionJob1 bean;
    private InstantiationException throwable;

    @Override
    public void handle(ThrowsExceptionJob1 bean, InstantiationException throwable) {
        this.bean = bean;
        this.throwable = throwable;
    }

    public ThrowsExceptionJob1 getBean() {
        return bean;
    }

    public InstantiationException getThrowable() {
        return throwable;
    }
}
