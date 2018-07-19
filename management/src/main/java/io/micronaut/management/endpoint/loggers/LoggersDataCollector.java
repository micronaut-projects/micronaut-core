/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.management.endpoint.loggers;

import org.reactivestreams.Publisher;

import java.util.Collection;

/**
 * Used to respond with logger information for the {@link LoggersEndpoint}.
 *
 * @param <T> the type
 * @author Matthew Moss
 * @since 1.0
 */
public interface LoggersDataCollector<T> {

    Publisher<T> getData(Collection<LoggerConfiguration> configurations);

}
