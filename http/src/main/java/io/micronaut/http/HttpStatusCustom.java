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

import io.micronaut.core.annotation.Internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Internal
public class HttpStatusCustom implements HttpStatus {

    private static final Map<Integer, String> GENERIC_REASONS;

    private final int code;
    private final String reason;

    public HttpStatusCustom(int code, String reason) {
        this.code = code;
        this.reason = Objects.isNull(reason) ? genericReason(code) : reason;
    }

    static {
        GENERIC_REASONS = new HashMap<>();
        GENERIC_REASONS.put(1, "Informational");
        GENERIC_REASONS.put(2, "Success");
        GENERIC_REASONS.put(3, "Redirection");
        GENERIC_REASONS.put(4, "Client Error");
        GENERIC_REASONS.put(5, "Server Error");
    }

    static String genericReason(int statusCode) {
        int firstDigit = statusCode / 100;
        String reason = "Unknown Status";

        if (GENERIC_REASONS.containsKey(firstDigit)) {
            reason = GENERIC_REASONS.get(firstDigit);
        }

        return String.format("%s (%d)", reason, statusCode);
    }

    public static HttpStatusStandard toStandard(HttpStatus httpStatus) {
        return HttpStatusStandard.valueOf(httpStatus.getCode());
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public int length() {
        return reason.length();
    }

    @Override
    public char charAt(int index) {
        return reason.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return reason.subSequence(start, end);
    }
}
