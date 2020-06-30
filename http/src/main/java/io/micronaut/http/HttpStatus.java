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
package io.micronaut.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents HTTP status codes. See https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public enum HttpStatus implements CharSequence {
    CONTINUE(100, "Continue"),
    SWITCHING_PROTOCOLS(101, "Switching Protocols"),
    PROCESSING(102, "Processing"),
    OK(200, "Ok"),
    CREATED(201, "Created"),
    ACCEPTED(202, "Accepted"),
    NON_AUTHORITATIVE_INFORMATION(203, "Non-Authoritative Information"),
    NO_CONTENT(204, "No Content"),
    RESET_CONTENT(205, "Reset Content"),
    PARTIAL_CONTENT(206, "Partial Content"),
    MULTI_STATUS(207, "Multi Status"),
    ALREADY_IMPORTED(208, "Already imported"),
    IM_USED(226, "IM Used"),
    MULTIPLE_CHOICES(300, "Multiple Choices"),
    MOVED_PERMANENTLY(301, "Moved Permanently"),
    FOUND(302, "Found"),
    SEE_OTHER(303, "See Other"),
    NOT_MODIFIED(304, "Not Modified"),
    USE_PROXY(305, "Use Proxy"),
    SWITCH_PROXY(306, "Switch Proxy"),
    TEMPORARY_REDIRECT(307, "Temporary Redirect"),
    PERMANENT_REDIRECT(308, "Permanent Redirect"),
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    PAYMENT_REQUIRED(402, "Payment Required"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    PROXY_AUTHENTICATION_REQUIRED(407, "Proxy Authentication Required"),
    REQUEST_TIMEOUT(408, "Request Timeout"),
    CONFLICT(409, "Conflict"),
    GONE(410, "Gone"),
    LENGTH_REQUIRED(411, "Length Required"),
    PRECONDITION_FAILED(412, "Precondition Failed"),
    REQUEST_ENTITY_TOO_LARGE(413, "Request Entity Too Large"),
    REQUEST_URI_TOO_LONG(414, "Request-URI Too Long"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
    REQUESTED_RANGE_NOT_SATISFIABLE(416, "Requested Range Not Satisfiable"),
    EXPECTATION_FAILED(417, "Expectation Failed"),
    I_AM_A_TEAPOT(418, "I am a teapot"),
    ENHANCE_YOUR_CALM(420, "Enhance your calm"),
    UNPROCESSABLE_ENTITY(422, "Unprocessable Entity"),
    LOCKED(423, "Locked"),
    FAILED_DEPENDENCY(424, "Failed Dependency"),
    UNORDERED_COLLECTION(425, "Unordered Collection"),
    UPGRADE_REQUIRED(426, "Upgrade Required"),
    PRECONDITION_REQUIRED(428, "Precondition Required"),
    TOO_MANY_REQUESTS(429, "Too Many Requests"),
    REQUEST_HEADER_FIELDS_TOO_LARGE(431, "Request Header Fields Too Large"),
    NO_RESPONSE(444, "No Response"),
    BLOCKED_BY_WINDOWS_PARENTAL_CONTROLS(450, "Blocked by Windows Parental Controls"),
    UNAVAILABLE_FOR_LEGAL_REASONS(451, "Unavailable For Legal Reasons"),
    REQUEST_HEADER_TOO_LARGE(494, "Request Header Too Large"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    BAD_GATEWAY(502, "Bad Gateway"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    GATEWAY_TIMEOUT(504, "Gateway Timeout"),
    HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported"),
    VARIANT_ALSO_NEGOTIATES(506, "Variant Also Negotiates"),
    INSUFFICIENT_STORAGE(507, "Insufficient Storage"),
    LOOP_DETECTED(508, "Loop Detected"),
    BANDWIDTH_LIMIT_EXCEEDED(509, "Bandwidth Limit Exceeded"),
    NOT_EXTENDED(510, "Not Extended"),
    NETWORK_AUTHENTICATION_REQUIRED(511, "Network Authentication Required"),
    CONNECTION_TIMED_OUT(522, "Connection Timed Out");

    private static final Map<Integer, HttpStatus> BY_CODE;

    static {
        HttpStatus[] statuses = values();
        Map<Integer, HttpStatus> byCode = new LinkedHashMap<>();
        for (HttpStatus status : statuses) {
            byCode.put(status.code, status);
        }

        BY_CODE = Collections.unmodifiableMap(byCode);
    }

    private final int code;
    private final String reason;

    /**
     * @param code   The code
     * @param reason The reason
     */
    HttpStatus(int code, String reason) {
        Objects.requireNonNull(reason, "Argument 'reason' cannot be null");
        this.code = code;
        this.reason = reason;
    }

    /**
     * @return The reason text
     */
    public String getReason() {
        return reason;
    }

    /**
     * @return The code
     */
    public int getCode() {
        return code;
    }

    /**
     * The status for the given code.
     *
     * @param code The code
     * @return The value
     */
    public static HttpStatus valueOf(int code) {
        HttpStatus status = BY_CODE.get(code);
        if (status == null) {
            throw new IllegalArgumentException("Invalid HTTP status code: " + code);
        }
        return status;
    }

    @Override
    public int length() {
        return name().length();
    }

    @Override
    public char charAt(int index) {
        return name().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return name().subSequence(start, end);
    }
}
