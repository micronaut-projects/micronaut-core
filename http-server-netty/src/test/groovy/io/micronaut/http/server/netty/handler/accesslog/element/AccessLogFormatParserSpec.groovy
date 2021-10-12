/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.server.netty.handler.accesslog.element

import spock.lang.Specification

class AccessLogFormatParserSpec extends Specification {

    def "test access log format parser for predefined formats"() {
        given:
        AccessLogFormatParser parser = new AccessLogFormatParser(null);

        expect:
        parser.toString() == "%h - - %t \"%r\" %s %b"

        when:
        parser = new AccessLogFormatParser(AccessLogFormatParser.COMMON_LOG_FORMAT)

        then:
        parser.toString() == "%h - - %t \"%r\" %s %b"

        when:
        parser = new AccessLogFormatParser(AccessLogFormatParser.COMBINED_LOG_FORMAT)

        then:
        parser.toString() == "%h - - %t \"%r\" %s %b \"%{Referer}i\" \"%{User-Agent}i\""
    }

    def "test access log format parser for custom formats"() {
        given:
        AccessLogFormatParser parser = new AccessLogFormatParser("%h %l %u %t \"%r\" %s %b %% some string");

        expect:
        parser.toString() == "%h - - %t \"%r\" %s %b %% some string"

        when:
        parser = new AccessLogFormatParser("%h %l %u %{'['dd.MM.yyyy']'}t \"%r\" %s %b")

        then:
        parser.toString() == "%h - - %{'['dd.MM.yyyy']'}t \"%r\" %s %b"

        when:
        parser = new AccessLogFormatParser("%h %l %u %t \"%r\" %s %b \"%{cookie1}C\" \"%{cookie2}c\"")

        then:
        parser.toString() == "%h - - %t \"%r\" %s %b \"%{cookie1}C\" \"%{cookie2}c\""
    }

    def "test access log format parser for invalid formats"() {
        when:
        AccessLogFormatParser parser = new AccessLogFormatParser("%h %l %u %t \"%r\" % %b");

        then:
        parser.toString() == "%h - - %t \"%r\" -%b"

        when:
        parser = new AccessLogFormatParser("%h %l %u %t \"%r\" %z %b")

        then:
        parser.toString() == "%h - - %t \"%r\" - %b"

    }


}
