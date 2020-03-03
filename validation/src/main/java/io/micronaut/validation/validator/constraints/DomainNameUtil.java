/*
 * Copyright 2017-2020 original authors
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
/*
 * Hibernate Validator, declare and validate application constraints
 *
 * License: Apache License, Version 2.0
 * See the license.txt file in the root directory or <http://www.apache.org/licenses/LICENSE-2.0>.
 */
package io.micronaut.validation.validator.constraints;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import java.net.IDN;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Forked from Hibernate Validator.
 *
 * @author Marko Bekhta
 * @author Guillaume Smet
 */
public final class DomainNameUtil {

    /**
     * This is the maximum length of a domain name. But be aware that each label (parts separated by a dot) of the
     * domain name must be at most 63 characters long. This is verified by {@link IDN#toASCII(String)}.
     */
    private static final int MAX_DOMAIN_PART_LENGTH = 255;

    private static final String DOMAIN_CHARS_WITHOUT_DASH = "[a-z\u0080-\uFFFF0-9!#$%&'*+/=?^_`{|}~]";
    private static final String DOMAIN_LABEL = "(" + DOMAIN_CHARS_WITHOUT_DASH + "-*)*" + DOMAIN_CHARS_WITHOUT_DASH + "+";
    private static final String DOMAIN = DOMAIN_LABEL + "+(\\." + DOMAIN_LABEL + "+)*";

    private static final String IP_DOMAIN = "[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}";
    //IP v6 regex taken from http://stackoverflow.com/questions/53497/regular-expression-that-matches-valid-ipv6-addresses
    private static final String IP_V6_DOMAIN = "(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))";

    /**
     * Regular expression for the domain part of an URL
     * <p>
     * A host string must be a domain string, an IPv4 address string, or "[", followed by an IPv6 address string,
     * followed by "]".
     */
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            DOMAIN + "|\\[" + IP_V6_DOMAIN + "\\]", CASE_INSENSITIVE
    );

    /**
     * Regular expression for the domain part of an email address (everything after '@').
     */
    private static final Pattern EMAIL_DOMAIN_PATTERN = Pattern.compile(
            DOMAIN + "|\\[" + IP_DOMAIN + "\\]|" + "\\[IPv6:" + IP_V6_DOMAIN + "\\]", CASE_INSENSITIVE
    );

    private DomainNameUtil() {
    }

    /**
     * Checks the validity of the domain name used in an email. To be valid it should be either a valid host name, or an
     * IP address wrapped in [].
     *
     * @param domain domain to check for validity
     * @return {@code true} if the provided string is a valid domain, {@code false} otherwise
     */
    public static boolean isValidEmailDomainAddress(String domain) {
        return isValidDomainAddress(domain, EMAIL_DOMAIN_PATTERN);
    }

    /**
     * Checks validity of a domain name.
     *
     * @param domain the domain to check for validity
     * @return {@code true} if the provided string is a valid domain, {@code false} otherwise
     */
    public static boolean isValidDomainAddress(String domain) {
        return isValidDomainAddress(domain, DOMAIN_PATTERN);
    }

    private static boolean isValidDomainAddress(String domain, Pattern pattern) {
        // if we have a trailing dot the domain part we have an invalid email address.
        // the regular expression match would take care of this, but IDN.toASCII drops the trailing '.'
        if (domain.endsWith(".")) {
            return false;
        }

        Matcher matcher = pattern.matcher(domain);
        if (!matcher.matches()) {
            return false;
        }

        String asciiString;
        try {
            asciiString = IDN.toASCII(domain);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return asciiString.length() <= MAX_DOMAIN_PART_LENGTH;
    }

}

