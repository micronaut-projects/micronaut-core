/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.context.logging;

import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Abstract implementation of {@link LoggingConfigurer}.
 *
 * @param <Ctx> The context type
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public abstract class AbstractLoggingConfigurer<Ctx> implements LoggingConfigurer {

    /**
     * Configure appenders.
     *
     * @param environment The environment
     * @param ctx         The context
     */
    protected void configureAppenders(Environment environment, Ctx ctx) {
        Map<String, Object> appenders = environment.get("logger.appenders", Map.class).orElse(null);
        if (appenders != null) {
            for (Map.Entry<String, Object> e : appenders.entrySet()) {
                String name = e.getKey();
                Map<String, Object> conf = (Map<String, Object>) e.getValue();
                if (conf == null) {
                    conf = Collections.emptyMap();
                }
                String type = (String) conf.get("type");
                if (type == null) {
                    type = name;
                }
                if ("console".equals(type)) {
                    String pattern = (String) conf.get("pattern");
                    if (pattern == null) {
                        throw new IllegalStateException("Console appender is required to have the 'pattern' property specified!");
                    }
                    addConsoleAppender(ctx, name, pattern, conf);
                } else if ("file".equals(type)) {
                    String pattern = (String) conf.get("pattern");
                    if (pattern == null) {
                        throw new IllegalStateException("Console appender is required to have the 'pattern' property specified!");
                    }
                    String file = (String) conf.get("file");
                    if (file == null) {
                        throw new IllegalStateException("File appender is required to have the 'file' property specified!");
                    }
                    addFileAppender(ctx, name, pattern, file, conf);
                } else {
                    throw new IllegalStateException("Unrecognized type for appender: '" + name + "'. Use 'type' property or have the name one of: 'console', 'file'");
                }
            }
        }
    }

    /**
     * Add console appender.
     *
     * @param ctx     The context
     * @param name    The appender name
     * @param pattern The pattern
     * @param conf    The configuration
     */
    protected abstract void addConsoleAppender(Ctx ctx, String name, String pattern, Map<String, Object> conf);

    /**
     * Add file appender.
     *
     * @param ctx     The context
     * @param name    The appender name
     * @param pattern The pattern
     * @param file    The filename
     * @param conf    The configuration
     */
    protected abstract void addFileAppender(Ctx ctx, String name, String pattern, String file, Map<String, Object> conf);

    /**
     * Check if appender should be added to the root logger.
     *
     * @param conf The configuration
     * @return true if add
     */
    protected boolean shouldAppendToRoot(Map<String, Object> conf) {
        Object o = conf.get("append-to-root");
        if (o == null) {
            return true;
        }
        if (o == Boolean.FALSE) {
            return false;
        }
        if (o == Boolean.TRUE) {
            return true;
        }
        return !o.toString().toLowerCase(Locale.ROOT).equals("true");
    }

}
