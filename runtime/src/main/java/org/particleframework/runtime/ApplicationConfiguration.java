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
package org.particleframework.runtime;

import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.context.annotation.Primary;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * The default charset
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("particle")
@Primary
public class ApplicationConfiguration {

    public static final String DEFAULT_CHARSET = "particle.defaultCharset";

    protected Charset defaultCharset = StandardCharsets.UTF_8;

    /**
     * @return The default charset to use
     */
    public Charset getDefaultCharset() {
        return defaultCharset;
    }

    public void setDefaultCharset(Charset defaultCharset) {
        this.defaultCharset = defaultCharset;
    }
}
