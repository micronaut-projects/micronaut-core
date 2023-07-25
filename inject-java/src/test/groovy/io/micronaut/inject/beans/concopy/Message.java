/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.inject.beans.concopy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A class for mapping graphql-ws messages.
 *
 * @author Jeremy Grelle
 * @since 4.0
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = ConnectionInitMessage.class, name = Message.Types.CONNECTION_INIT),
    @Type(value = ConnectionAckMessage.class, name = Message.Types.CONNECTION_ACK),
    @Type(value = PingMessage.class, name = Message.Types.PING),
    @Type(value = PongMessage.class, name = Message.Types.PONG),
    @Type(value = SubscribeMessage.class, name = Message.Types.SUBSCRIBE),
    @Type(value = ErrorMessage.class, name = Message.Types.ERROR),
    @Type(value = CompleteMessage.class, name = Message.Types.COMPLETE)
})
@Introspected
public abstract sealed class Message {

    /**
     * Get the required value of the message's <code>type</code> field.
     *
     * @return The message's type
     */
    @JsonIgnore
    @NonNull
    abstract String getMessageType();

    /**
     * Validate a required message id.
     *
     * @param id The required message id
     */
    protected void checkRequiredId(String id) {
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("'id' is required for messages with type '" + getMessageType() + "'.");
        }
    }

    /**
     * The allowable graphql-ws message types.
     */
    static final class Types {

        static final String CONNECTION_INIT = "connection_init";
        static final String CONNECTION_ACK = "connection_ack";
        static final String PING = "ping";
        static final String PONG = "pong";
        static final String SUBSCRIBE = "subscribe";
        static final String NEXT = "next";
        static final String ERROR = "error";
        static final String COMPLETE = "complete";

        private Types() { }
    }
}

/**
 * A graphql-ws message that contains an optional payload.
 *
 * @param <T> The payload type
 */
abstract sealed class PayloadMessage<T> extends Message {

    @Nullable
    private final T payload;

    /**
     * Default constructor for a graphql-ws message with an optional payload.
     */
    protected PayloadMessage() {
        this(null);
    }

    /**
     * Constructor for a graphql-ws message with a payload.
     *
     * @param payload The message payload.
     */
    protected PayloadMessage(@Nullable T payload) {
        this.payload = payload;
    }

    /**
     * Get the message payload.
     *
     * @return The message payload.
     */
    @Nullable
    public T getPayload() {
        return payload;
    }
}

/**
 * A graphql-ws message that has a required non-null payload.
 *
 * @param <T> The payload type.
 */
abstract sealed class RequiredPayloadMessage<T> extends Message {

    @NonNull
    private final T payload;

    /**
     * Constructor for a graphql-ws message with a required payload.
     *
     * @param payload The message payload.
     */
    protected RequiredPayloadMessage(@NonNull T payload) {
        Objects.requireNonNull(payload, "A payload is required for message type '" + getMessageType() + ".");
        this.payload = payload;
    }

    /**
     * Get the message payload - will never be <code>null</code>.
     *
     * @return The message payload.
     */
    @NonNull
    public T getPayload() {
        return payload;
    }
}

/**
 * A graphql-ws message for connection initialisation.
 */
final class ConnectionInitMessage extends PayloadMessage<Map<String, Object>> {

    /**
     * Get the required value of the message's <code>type</code> field.
     *
     * @return The message's type
     */
    @Override
    @JsonIgnore
    @NonNull
    String getMessageType() {
        return Types.CONNECTION_INIT;
    }
}

/**
 * A graphql-ws message for connection acknowledgement.
 */
final class ConnectionAckMessage extends PayloadMessage<Map<String, Object>> {

    /**
     * Get the required value of the message's <code>type</code> field.
     *
     * @return The message's type
     */
    @Override
    @JsonIgnore
    @NonNull
    String getMessageType() {
        return Types.CONNECTION_ACK;
    }
}

/**
 * A graphql-ws message for a ping.
 */
final class PingMessage extends PayloadMessage<Map<String, Object>> {

    /**
     * Get the required value of the message's <code>type</code> field.
     *
     * @return The message's type
     */
    @Override
    @JsonIgnore
    @NonNull
    String getMessageType() {
        return Types.PING;
    }
}

/**
 * A graphql-ws message for a pong.
 */
final class PongMessage extends PayloadMessage<Map<String, Object>> {

    /**
     * Get the required value of the message's <code>type</code> field.
     *
     * @return The message's type
     */
    @Override
    @JsonIgnore
    @NonNull
    String getMessageType() {
        return Types.PONG;
    }
}

/**
 * A graphql-ws message for subscribing to the execution of a query.
 */
@Introspected
final class SubscribeMessage extends RequiredPayloadMessage<SubscribeMessage.SubscribePayload> {

    @NonNull
    private final String id;

    /**
     * Constructor for a graphql-ws 'subscribe' message.
     *
     * @param id
     * @param payload
     */
    @JsonCreator
    public SubscribeMessage(@NonNull @JsonProperty("id") String id, @NonNull @JsonProperty("payload") SubscribePayload payload) {
        super(payload);
        checkRequiredId(id);
        this.id = id;
    }

    /**
     * Get the required non-empty message id.
     *
     * @return The message id.
     */
    @NonNull
    public String getId() {
        return id;
    }

    /**
     * Get the required value of the message's <code>type</code> field.
     *
     * @return The message's type
     */
    @Override
    @JsonIgnore
    @NonNull
    String getMessageType() {
        return Types.SUBSCRIBE;
    }

    /**
     * Get the message payload as a {@link SubscribePayload}.
     *
     * @return The message payload.
     */
    @JsonIgnore
    @NonNull
    SubscribePayload getSubscribePayload() {
        //return SubscribePayload.fromMap(getPayload());
        return getPayload();
    }

    @Introspected
    public static class SubscribePayload {

        @NonNull
        private final String query;

        @Nullable
        private String operationName;

        @Nullable
        private Map<String, Object> variables;

        @Nullable
        private Map<String, Object> extensions;

        /**
         * Constructor for a graphql-ws 'subscribe' message's payload.
         *
         * @param query The required non-empty query being executed and subscribed.
         */
        @JsonCreator
        public SubscribePayload(@NonNull @JsonProperty("query") String query) {
            if (StringUtils.isEmpty(query)) {
                throw new IllegalArgumentException("The 'query' field is required in the payload of message type '" + Types.SUBSCRIBE + "'");
            }
            this.query = query;
        }

        private SubscribePayload(@NonNull String query, @Nullable String operationName, @Nullable Map<String, Object> variables, @Nullable Map<String, Object> extensions) {
            this.query = query;
            this.operationName = operationName;
            this.variables = variables;
            this.extensions = extensions;
        }

        /**
         * Gets the required non-empty query field of the message payload.
         *
         * @return The query.
         */
        @NonNull
        public String getQuery() {
            return query;
        }

        /**
         * Gets the operation name of the payload.
         *
         * @return The operation name.
         */
        @Nullable
        public String getOperationName() {
            return operationName;
        }

        /**
         * Sets the operation name of the payload.
         *
         * @param operationName The operation name.
         */
        public void setOperationName(@Nullable String operationName) {
            this.operationName = operationName;
        }

        /**
         * Gets the variables of the payload.
         *
         * @return The payload variables.
         */
        @Nullable
        public Map<String, Object> getVariables() {
            return variables;
        }

        /**
         * Sets the variables of the payload.
         *
         * @param variables The operation name.
         */
        public void setVariables(@Nullable Map<String, Object> variables) {
            this.variables = variables;
        }

        /**
         * Gets the extensions of the payload.
         *
         * @return The extensions.
         */
        @Nullable
        public Map<String, Object> getExtensions() {
            return extensions;
        }

        /**
         * Sets the extensions of the payload.
         *
         * @param extensions The operation name.
         */
        public void setExtensions(@Nullable Map<String, Object> extensions) {
            this.extensions = extensions;
        }

        public Map<String, Object> toMap() {
            return Map.of(
                "query", this.query,
                "operationName", Optional.ofNullable(this.operationName).orElse(""),
                "variables", Optional.ofNullable(this.variables).orElse(new HashMap<>()),
                "extensions", Optional.ofNullable(this.extensions).orElse(new HashMap<>())
            );
        }

        @SuppressWarnings("unchecked")
        public static SubscribePayload fromMap(Map<String, Object> payload) {
            return new SubscribePayload(
                (String) payload.get("query"),
                (String) payload.get("operationName"),
                (Map<String, Object>) payload.get("variables"),
                (Map<String, Object>) payload.get("extensions"));
        }
    }
}

/**
 * A graphql-ws message for reporting errors pertaining to a specific subscription.
 */
final class ErrorMessage extends RequiredPayloadMessage<List<Map<String, Object>>> {

    @NonNull
    private final String id;

    /**
     * Constructor for a graphql-ws 'error' message.
     *
     * @param id     The required non-empty id of the message.
     * @param errors The errors resulting from the specific subscription with the corresponding id.
     */
    @JsonCreator
    public ErrorMessage(@NonNull @JsonProperty("id") String id, @NonNull @JsonProperty("payload") List<Map<String, Object>> errors) {
        super(errors);
        checkRequiredId(id);
        this.id = id;
    }

    /**
     * Get the required non-empty message id.
     *
     * @return The message id.
     */
    @NonNull
    public String getId() {
        return id;
    }

    /**
     * Get the required value of the message's <code>type</code> field.
     *
     * @return The message's type
     */
    @Override
    @JsonIgnore
    @NonNull
    String getMessageType() {
        return Types.ERROR;
    }
}

/**
 * A graphql-ws message for the completion of a subscription.
 */
final class CompleteMessage extends Message {

    @NonNull
    private final String id;

    /**
     * Constructor for a graphql-ws 'complete' message.
     *
     * @param id The required non-empty id of the message.
     */
    public CompleteMessage(@NonNull String id) {
        checkRequiredId(id);
        this.id = id;
    }

    /**
     * Get the required non-empty message id.
     *
     * @return The message id.
     */
    @NonNull
    public String getId() {
        return id;
    }

    /**
     * Get the required value of the message's <code>type</code> field.
     *
     * @return The message's type
     */
    @Override
    @JsonIgnore
    @NonNull
    String getMessageType() {
        return Types.COMPLETE;
    }
}
