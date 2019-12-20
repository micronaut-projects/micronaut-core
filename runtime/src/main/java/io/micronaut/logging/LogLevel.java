/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.logging;

/**
 * Logging levels supported by a {@link LoggingSystem}
 *
 * Typically, a logging system may permit the log level to be null, representing
 * an unspecified log level. For {@link LoggingSystem} and the loggers endpoint,
 * we prefer to return the NOT_SPECIFIED pseudo-level instead of null.
 *
 * @author Matthew Moss
 * @since 1.0
 */
public enum LogLevel {
    ALL,
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    OFF,
    NOT_SPECIFIED
}
