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
package io.micronaut.context.env.yaml;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapted from {@link org.yaml.snakeyaml.constructor.SafeConstructor.ConstructYamlTimestamp} but
 * with java 8 time.
 */
@Internal
final class ConstructIsoTimestampString extends AbstractConstruct {
    private static final Pattern TIMESTAMP_REGEXP = Pattern.compile(
        "^([0-9][0-9][0-9][0-9])-([0-9][0-9]?)-([0-9][0-9]?)(?:(?:[Tt]|[ \t]+)([0-9][0-9]?):([0-9][0-9]):([0-9][0-9])(?:\\.([0-9]*))?(?:[ \t]*(?:(Z)|([-+][0-9][0-9]?)(?::([0-9][0-9])?)?))?)?$");
    private static final Pattern YMD_REGEXP = Pattern
        .compile("^([0-9][0-9][0-9][0-9])-([0-9][0-9]?)-([0-9][0-9]?)$");

    @Override
    public Object construct(Node node) {
        ScalarNode scalar = (ScalarNode) node;
        String nodeValue = scalar.getValue();
        return parse(nodeValue);
    }

    @NonNull
    static Temporal parse(String nodeValue) {
        Matcher match = YMD_REGEXP.matcher(nodeValue);
        if (match.matches()) {
            String yearS = match.group(1);
            String monthS = match.group(2);
            String dayS = match.group(3);
            return LocalDate.of(
                Integer.parseInt(yearS),
                Integer.parseInt(monthS),
                Integer.parseInt(dayS)
            );
        } else {
            match = TIMESTAMP_REGEXP.matcher(nodeValue);
            if (!match.matches()) {
                throw new YAMLException("Unexpected timestamp: " + nodeValue);
            }
            String yearS = match.group(1);
            String monthS = match.group(2);
            String dayS = match.group(3);
            String hourS = match.group(4);
            String minS = match.group(5);
            // seconds and milliseconds
            String seconds = match.group(6);
            String millis = match.group(7);
            if (millis != null) {
                seconds = seconds + "." + millis;
            }
            double fractions = Double.parseDouble(seconds);
            int secS = (int) Math.round(Math.floor(fractions));
            int nsec = (int) Math.round((fractions - secS) * 1_000_000_000);

            LocalDateTime ldt = LocalDateTime.of(
                Integer.parseInt(yearS),
                Integer.parseInt(monthS),
                Integer.parseInt(dayS),
                Integer.parseInt(hourS),
                Integer.parseInt(minS),
                secS,
                nsec
            );

            // timezone
            String timezonehS = match.group(9);
            String timezonemS = match.group(10);
            if (timezonehS != null) {
                ZoneOffset offset;
                if (timezonemS == null) {
                    offset = ZoneOffset.ofHours(Integer.parseInt(timezonehS));
                } else {
                    offset = ZoneOffset.ofHoursMinutes(Integer.parseInt(timezonehS), Integer.parseInt(timezonemS));
                }
                return ldt.atOffset(offset);
            } else {
                if (match.group(8) != null) {
                    // Z
                    return ldt.atOffset(ZoneOffset.UTC);
                } else {
                    // no time zone provided
                    return ldt;
                }
            }
        }
    }
}
