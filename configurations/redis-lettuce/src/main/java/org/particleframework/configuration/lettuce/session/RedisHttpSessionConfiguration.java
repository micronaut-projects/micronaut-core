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
package org.particleframework.configuration.lettuce.session;

import io.lettuce.core.RedisURI;
import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.core.serialize.JdkSerializer;
import org.particleframework.core.serialize.ObjectSerializer;
import org.particleframework.core.util.Toggleable;
import org.particleframework.session.Session;
import org.particleframework.session.http.HttpSessionConfiguration;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Configuration properties for Redis session
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("redis")
public class RedisHttpSessionConfiguration extends HttpSessionConfiguration implements Toggleable{

    private WriteMode writeMode = WriteMode.BATCH;
    private boolean enableKeyspaceEvents = true;
    private String namespace = "particle:session:";
    private String activeSessionsKey = namespace + "active-sessions";
    private String sessionCreatedTopic = namespace + "event:session-created";
    private Class<ObjectSerializer> valueSerializer;
    private Charset charset = StandardCharsets.UTF_8;
    private Duration expiredSessionCheck = Duration.ofMinutes(1);
    private String serverName;

    /**
     * @return The name of the a configured Redis server to use
     */
    public Optional<String> getServerName() {
        return Optional.ofNullable(serverName);
    }

    /**
     * @return The topic to use to publish the creation of new sessions
     */
    public String getSessionCreatedTopic() {
        return sessionCreatedTopic;
    }

    /**
     *
     * @return The key of the sorted set used to maintain a set of active sessions
     */
    public String getActiveSessionsKey() {
        return activeSessionsKey;
    }

    /**
     * @return The key prefix to use for reading and writing sessions
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * @return The {@link ObjectSerializer} type to use for serializing values. Defaults to {@link JdkSerializer}
     */
    public Optional<Class<ObjectSerializer>> getValueSerializer() {
        return Optional.ofNullable(valueSerializer);
    }

    /**
     * @return The charset to use when encoding sessions
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * @return Whether keyspace events should be enabled programmatically
     */
    public boolean isEnableKeyspaceEvents() {
        return enableKeyspaceEvents;
    }

    /**
     * @return The {@link WriteMode} to use. Defaults to {@link WriteMode#BATCH}
     */
    public WriteMode getWriteMode() {
        return writeMode;
    }

    /**
     * @return The duration with which to check for expired sessions
     */
    public Duration getExpiredSessionCheck() {
        return expiredSessionCheck;
    }

    void setExpiredSessionCheck(Duration expiredSessionCheck) {
        this.expiredSessionCheck = expiredSessionCheck;
    }

    void setWriteMode(WriteMode writeMode) {
        this.writeMode = writeMode;
    }

    void setEnableKeyspaceEvents(boolean enableKeyspaceEvents) {
        this.enableKeyspaceEvents = enableKeyspaceEvents;
    }

    void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    void setServerName(String serverName) {
        this.serverName = serverName;
    }

    void setSessionCreatedTopic(String sessionCreatedTopic) {
        this.sessionCreatedTopic = sessionCreatedTopic;
    }

    void setActiveSessionsKey(String activeSessionsKey) {
        this.activeSessionsKey = activeSessionsKey;
    }

    void setValueSerializer(Class<ObjectSerializer> valueSerializer) {
        this.valueSerializer = valueSerializer;
    }
    /**
     * The write mode for saving the session data
     */
    enum WriteMode {
        /**
         * Batch up changes an synchronize once only when {@link org.particleframework.session.SessionStore#save(Session)} is called
         */
        BATCH,
        /**
         * <p>Perform asynchronous write-behind when session attributes are changed in addition to batching up changes when  {@link org.particleframework.session.SessionStore#save(Session)} is called</p>
         *
         * <p>Errors that occur during these asynchronous operations are silently ignored</p>
         *
         * <p>This strategy has the advantage of providing greater consistency at the expense of more network traffic</p>
         */
        BACKGROUND;
    }
}
