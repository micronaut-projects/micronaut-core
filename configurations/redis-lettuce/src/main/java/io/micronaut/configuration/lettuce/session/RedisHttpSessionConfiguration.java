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

package io.micronaut.configuration.lettuce.session;

import io.micronaut.configuration.lettuce.RedisSetting;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.serialize.ObjectSerializer;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.session.http.HttpSessionConfiguration;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Configuration properties for Redis session.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(RedisSetting.PREFIX)
public class RedisHttpSessionConfiguration extends HttpSessionConfiguration implements Toggleable {

    private String namespace = "micronaut:session:";
    private String serverName;
    private String sessionCreatedTopic = namespace + "event:session-created";
    private String activeSessionsKey = namespace + "active-sessions";
    private Class<ObjectSerializer> valueSerializer;
    private Charset charset = StandardCharsets.UTF_8;
    private boolean enableKeyspaceEvents = true;
    private WriteMode writeMode = WriteMode.BATCH;
    private Duration expiredSessionCheck = Duration.ofMinutes(1);

    /**
     * @return The key prefix to use for reading and writing sessions
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * @return The name of the a configured Redis server to use.
     */
    public Optional<String> getServerName() {
        return Optional.ofNullable(serverName);
    }

    /**
     * @return The topic to use to publish the creation of new sessions.
     */
    public String getSessionCreatedTopic() {
        return sessionCreatedTopic;
    }

    /**
     * @return The key of the sorted set used to maintain a set of active sessions.
     */
    public String getActiveSessionsKey() {
        return activeSessionsKey;
    }

    /**
     * @return The {@link ObjectSerializer} type to use for serializing values. Defaults to {@link io.micronaut.core.serialize.JdkSerializer}
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
     * @return The {@link RedisHttpSessionConfiguration.WriteMode} to use. Defaults to {@link RedisHttpSessionConfiguration.WriteMode#BATCH}
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

    /**
     * Sets the namespace to use.
     *
     * @param namespace The namespace
     */
    public void setNamespace(String namespace) {
        if (StringUtils.isNotEmpty(namespace)) {
            this.namespace = namespace;
        }
    }

    /**
     * Sets the server name.
     *
     * @param serverName The server name
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    /**
     * Sets the topic to publish session created events to.
     *
     * @param sessionCreatedTopic The session created topic
     */
    public void setSessionCreatedTopic(String sessionCreatedTopic) {
        this.sessionCreatedTopic = sessionCreatedTopic;
    }

    /**
     * Sets the key to store active sessions in.
     * @param activeSessionsKey The active session key
     */
    public void setActiveSessionsKey(String activeSessionsKey) {
        this.activeSessionsKey = activeSessionsKey;
    }

    /**
     * Sets the default value serializer to use.
     *
     * @param valueSerializer The value serializer
     */
    public void setValueSerializer(Class<ObjectSerializer> valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

    /**
     * Sets the charset to use to serialize values.
     *
     * @param charset The default charset
     */
    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    /**
     * Sets whether keyspace events should be enabled at startup.
     * @param enableKeyspaceEvents True if keyspace event should be enabled
     */
    public void setEnableKeyspaceEvents(boolean enableKeyspaceEvents) {
        this.enableKeyspaceEvents = enableKeyspaceEvents;
    }

    /**
     * Sets the write mode to use.
     *
     * @param writeMode The write mode
     */
    public void setWriteMode(WriteMode writeMode) {
        this.writeMode = writeMode;
    }

    /**
     * Sets the duration to check for expired sessions.
     *
     * @param expiredSessionCheck The expired session check
     */
    public void setExpiredSessionCheck(Duration expiredSessionCheck) {
        this.expiredSessionCheck = expiredSessionCheck;
    }

    /**
     * The write mode for saving the session data.
     */
    enum WriteMode {
        /**
         * Batch up changes an synchronize once only when {@link io.micronaut.session.SessionStore#save(io.micronaut.session.Session)} is called.
         */
        BATCH,
        /**
         * <p>Perform asynchronous write-behind when session attributes are changed in addition to batching up changes when  {@link io.micronaut.session.SessionStore#save(io.micronaut.session.Session)} is called</p>.
         *
         * <p>Errors that occur during these asynchronous operations are silently ignored</p>
         *
         * <p>This strategy has the advantage of providing greater consistency at the expense of more network traffic</p>
         */
        BACKGROUND
    }
}
