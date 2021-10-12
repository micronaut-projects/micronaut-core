/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.websocket;

import java.util.Objects;

/**
 * Enumeration of close events. See https://tools.ietf.org/html/rfc6455#section-11.7.
 *
 *
 * @author graemerocher
 * @since 1.0
 */
public class CloseReason {

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason NORMAL = new CloseReason(1000, "Normal Closure");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason GOING_AWAY = new CloseReason(1001, "Going Away");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason PROTOCOL_ERROR = new CloseReason(1002, "Protocol Error");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason UNSUPPORTED_DATA = new CloseReason(1003, "Unsupported Data");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason NO_STATUS_RECEIVED = new CloseReason(1005, "No Status Recvd");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason ABNORMAL_CLOSURE = new CloseReason(1006, "Abnormal Closure");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason INVALID_FRAME_PAYLOAD_DATA = new CloseReason(1007, "Invalid frame payload data");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason POLICY_VIOLATION = new CloseReason(1008, "Policy Violation");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason MESSAGE_TO_BIG = new CloseReason(1009, "Message Too Big");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason MISSING_EXTENSION = new CloseReason(1010, "Missing Extension");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason INTERNAL_ERROR = new CloseReason(1011, "Internal Error");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason SERVICE_RESTART = new CloseReason(1012, "Service Restart");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason TRY_AGAIN_LATER = new CloseReason(1013, "Try Again Later");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason BAD_GATEWAY = new CloseReason(1014, "Bad Gateway");

    /**
     * See https://tools.ietf.org/html/rfc6455#section-11.7.
     */
    public static final CloseReason TLS_HANDSHAKE = new CloseReason(1015, "TLS Handshake");

    private final int code;
    private String reason;

    /**
     * Default constructor.
     *
     * @param code The code.
     * @param reason The reason.
     */
    public CloseReason(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }

    /**
     * @return The code
     */
    public int getCode() {
        return code;
    }

    /**
     * @return The reason
     */
    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CloseReason that = (CloseReason) o;
        return code == that.code &&
                Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, reason);
    }

    @Override
    public String toString() {
        return "CloseReason{" +
                "code=" + code +
                ", reason='" + reason + '\'' +
                '}';
    }
}
