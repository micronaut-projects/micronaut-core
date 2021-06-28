/*
 * Copyright 2017-2021 original authors
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

import java.util.Objects;

public interface HttpStatus extends CharSequence {
    HttpStatus CONTINUE = HttpStatusStandard.valueOf(100);
    HttpStatus SWITCHING_PROTOCOLS = HttpStatusStandard.valueOf(101);
    HttpStatus PROCESSING = HttpStatusStandard.valueOf(102);
    HttpStatus OK = HttpStatusStandard.valueOf(200);
    HttpStatus CREATED = HttpStatusStandard.valueOf(201);
    HttpStatus ACCEPTED = HttpStatusStandard.valueOf(202);
    HttpStatus NON_AUTHORITATIVE_INFORMATION = HttpStatusStandard.valueOf(203);
    HttpStatus NO_CONTENT = HttpStatusStandard.valueOf(204);
    HttpStatus RESET_CONTENT = HttpStatusStandard.valueOf(205);
    HttpStatus PARTIAL_CONTENT = HttpStatusStandard.valueOf(206);
    HttpStatus MULTI_STATUS = HttpStatusStandard.valueOf(207);
    HttpStatus ALREADY_IMPORTED = HttpStatusStandard.valueOf(208);
    HttpStatus IM_USED = HttpStatusStandard.valueOf(226);
    HttpStatus MULTIPLE_CHOICES = HttpStatusStandard.valueOf(300);
    HttpStatus MOVED_PERMANENTLY = HttpStatusStandard.valueOf(301);
    HttpStatus FOUND = HttpStatusStandard.valueOf(302);
    HttpStatus SEE_OTHER = HttpStatusStandard.valueOf(303);
    HttpStatus NOT_MODIFIED = HttpStatusStandard.valueOf(304);
    HttpStatus USE_PROXY = HttpStatusStandard.valueOf(305);
    HttpStatus SWITCH_PROXY = HttpStatusStandard.valueOf(306);
    HttpStatus TEMPORARY_REDIRECT = HttpStatusStandard.valueOf(307);
    HttpStatus PERMANENT_REDIRECT = HttpStatusStandard.valueOf(308);
    HttpStatus BAD_REQUEST = HttpStatusStandard.valueOf(400);
    HttpStatus UNAUTHORIZED = HttpStatusStandard.valueOf(401);
    HttpStatus PAYMENT_REQUIRED = HttpStatusStandard.valueOf(402);
    HttpStatus FORBIDDEN = HttpStatusStandard.valueOf(403);
    HttpStatus NOT_FOUND = HttpStatusStandard.valueOf(404);
    HttpStatus METHOD_NOT_ALLOWED = HttpStatusStandard.valueOf(405);
    HttpStatus NOT_ACCEPTABLE = HttpStatusStandard.valueOf(406);
    HttpStatus PROXY_AUTHENTICATION_REQUIRED = HttpStatusStandard.valueOf(407);
    HttpStatus REQUEST_TIMEOUT = HttpStatusStandard.valueOf(408);
    HttpStatus CONFLICT = HttpStatusStandard.valueOf(409);
    HttpStatus GONE = HttpStatusStandard.valueOf(410);
    HttpStatus LENGTH_REQUIRED = HttpStatusStandard.valueOf(411);
    HttpStatus PRECONDITION_FAILED = HttpStatusStandard.valueOf(412);
    HttpStatus REQUEST_ENTITY_TOO_LARGE = HttpStatusStandard.valueOf(413);
    HttpStatus REQUEST_URI_TOO_LONG = HttpStatusStandard.valueOf(414);
    HttpStatus UNSUPPORTED_MEDIA_TYPE = HttpStatusStandard.valueOf(415);
    HttpStatus REQUESTED_RANGE_NOT_SATISFIABLE = HttpStatusStandard.valueOf(416);
    HttpStatus EXPECTATION_FAILED = HttpStatusStandard.valueOf(417);
    HttpStatus I_AM_A_TEAPOT = HttpStatusStandard.valueOf(418);
    HttpStatus ENHANCE_YOUR_CALM = HttpStatusStandard.valueOf(420);
    HttpStatus UNPROCESSABLE_ENTITY = HttpStatusStandard.valueOf(422);
    HttpStatus LOCKED = HttpStatusStandard.valueOf(423);
    HttpStatus FAILED_DEPENDENCY = HttpStatusStandard.valueOf(424);
    HttpStatus UNORDERED_COLLECTION = HttpStatusStandard.valueOf(425);
    HttpStatus UPGRADE_REQUIRED = HttpStatusStandard.valueOf(426);
    HttpStatus PRECONDITION_REQUIRED = HttpStatusStandard.valueOf(428);
    HttpStatus TOO_MANY_REQUESTS = HttpStatusStandard.valueOf(429);
    HttpStatus REQUEST_HEADER_FIELDS_TOO_LARGE = HttpStatusStandard.valueOf(431);
    HttpStatus NO_RESPONSE = HttpStatusStandard.valueOf(444);
    HttpStatus BLOCKED_BY_WINDOWS_PARENTAL_CONTROLS = HttpStatusStandard.valueOf(450);
    HttpStatus UNAVAILABLE_FOR_LEGAL_REASONS = HttpStatusStandard.valueOf(451);
    HttpStatus REQUEST_HEADER_TOO_LARGE = HttpStatusStandard.valueOf(494);
    HttpStatus INTERNAL_SERVER_ERROR = HttpStatusStandard.valueOf(500);
    HttpStatus NOT_IMPLEMENTED = HttpStatusStandard.valueOf(501);
    HttpStatus BAD_GATEWAY = HttpStatusStandard.valueOf(502);
    HttpStatus SERVICE_UNAVAILABLE = HttpStatusStandard.valueOf(503);
    HttpStatus GATEWAY_TIMEOUT = HttpStatusStandard.valueOf(504);
    HttpStatus HTTP_VERSION_NOT_SUPPORTED = HttpStatusStandard.valueOf(505);
    HttpStatus VARIANT_ALSO_NEGOTIATES = HttpStatusStandard.valueOf(506);
    HttpStatus INSUFFICIENT_STORAGE = HttpStatusStandard.valueOf(507);
    HttpStatus LOOP_DETECTED = HttpStatusStandard.valueOf(508);
    HttpStatus BANDWIDTH_LIMIT_EXCEEDED = HttpStatusStandard.valueOf(509);
    HttpStatus NOT_EXTENDED = HttpStatusStandard.valueOf(510);
    HttpStatus NETWORK_AUTHENTICATION_REQUIRED = HttpStatusStandard.valueOf(511);
    HttpStatus CONNECTION_TIMED_OUT = HttpStatusStandard.valueOf(522);

    int getCode();

    String getReason();

    /**
     * The status for the given code. If the code is non-standard a
     * custom status is created with a generic reason phrase. The
     * generic reason phrase is the class of the response.
     *
     * See: <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6.1.1">RFC2616 Section 6.1.1</a>
     *
     * @param code The code
     * @return The value
     */
    static HttpStatus valueOf(int code) {
        try {
            return HttpStatusStandard.valueOf(code);
        } catch (IllegalArgumentException e) {
            return new HttpStatusCustom(code, null);
        }
    }

    /**
     * The status for the given code and reason.
     *
     * @param code The status code
     * @param reason The reason. If null, a generic reason is provided
     * @return The {@link HttpStatus}
     */
    static HttpStatus custom(int code, String reason) {
        try {
            if (Objects.isNull(reason)) {
                return HttpStatusStandard.valueOf(code);
            }
        } catch (IllegalArgumentException e) {
            return new HttpStatusCustom(code, null);
        }
        return new HttpStatusCustom(code, reason);
    }

    @Override
    int length();

    @Override
    char charAt(int index);

    @Override
    CharSequence subSequence(int start, int end);
}
