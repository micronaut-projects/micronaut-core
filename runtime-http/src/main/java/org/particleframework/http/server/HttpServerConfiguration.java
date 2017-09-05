/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server;

import groovy.transform.CompileStatic;
import org.particleframework.config.ConfigurationProperties;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * <p>A base {@link ConfigurationProperties} for servers</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(value = "particle.server", cliPrefix = "")
@CompileStatic
public class HttpServerConfiguration {

    protected int port = 8080;
    protected int securePort = 8443;
    protected String host = "localhost";
    protected Charset defaultCharset = StandardCharsets.UTF_8;

    /**
     * The default server port
     */
    public int getPort() {
        return port;
    }

    /**
     * The default secure (normally HTTPS) port
     */
    public int getSecurePort() {
        return securePort;
    }

    /**
     * The default host
     */
    public String getHost() {
        return host;
    }

    /**
     * The default charset to use when encoding and decoding responses if {@link org.particleframework.http.HttpHeaders#ACCEPT_CHARSET} or {@link org.particleframework.http.HttpHeaders#ACCEPT} is not sent by the client
     */
    public Charset getDefaultCharset() {
        return defaultCharset;
    }
}
