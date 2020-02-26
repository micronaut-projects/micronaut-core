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
package io.micronaut.scheduling;

import io.micronaut.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Singleton;

/**
 * The default exception handler used if non other is found. Simply logs the exception.
 *
 * @author graemerocher
 * @since 1.0
 *
 */
@Singleton
@Primary
public class DefaultTaskExceptionHandler implements TaskExceptionHandler<Object, Throwable> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTaskExceptionHandler.class);

    @Override
    public void handle(@Nullable Object bean, @NonNull Throwable throwable) {
        if (LOG.isErrorEnabled()) {
            StringBuilder message = new StringBuilder("Error invoking scheduled task ");
            if (bean != null) {
                message.append("for bean [").append(bean.toString()).append("] ");
            }
            message.append(throwable.getMessage());
            LOG.error(message.toString(), throwable);
        }
    }
}
