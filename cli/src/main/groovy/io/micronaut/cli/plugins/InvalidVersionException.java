/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.plugins;

/**
 * Throw when a specified version number is invalid.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
public class InvalidVersionException extends RuntimeException {

    private static final long serialVersionUID = 7913782067211066121L;

    public InvalidVersionException() {
        // default
    }

    public InvalidVersionException(String message) {
        super(message);
    }

    public InvalidVersionException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public InvalidVersionException(Throwable throwable) {
        super(throwable);
    }
}
