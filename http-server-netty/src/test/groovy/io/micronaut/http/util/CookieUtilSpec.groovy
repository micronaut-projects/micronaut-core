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
package io.micronaut.http.util

import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.SameSite
import spock.lang.Specification

import java.time.Duration
import java.time.LocalDateTime
import java.time.Month
import java.time.temporal.ChronoUnit

class CookieUtilSpec extends Specification {

    static long durationInSeconds(Integer year, Month month, Integer day, Integer hour, Integer minute, Integer second){
        return Duration.between(LocalDateTime.of(year, month, day, hour, minute, second), LocalDateTime.now())
                .get(ChronoUnit.SECONDS)
    }

    void "test parsing cookie date from header #setCookieHeader"() {
        given:
        Cookie cookie = CookieUtil.getCookieFromString(setCookieHeader)

        expect:
        cookie.getMaxAge() == maxAge

        where:
        setCookieHeader                                                     | maxAge
        "Set-Cookie: name=value"                                            | 0
        "Set-Cookie: name=value; max-age=10"                                | 10
        "Set-Cookie: name=value; max-age=-10"                               | -10
        "Set-Cookie: name=value; max-age;"                                  | 0
        "Set-Cookie: name=value; expires=Thu, 04 Feb 2021 03:47:49 GMT;"    | durationInSeconds(2021, Month.FEBRUARY, 4, 3, 47, 49)
        "Set-Cookie: name=value; expires=Thu, 04-Feb-2021 03:47:49 GMT;"    | durationInSeconds(2021, Month.FEBRUARY, 4, 3, 47, 49)
        "Set-Cookie: name=value; expires=Sun, 06 Nov 1994 08:49:37 GMT;"    | durationInSeconds(1994, Month.NOVEMBER, 6, 8, 49, 37)
        "Set-Cookie: name=value; expires=Sunday, 06-Nov-94 08:49:37 GMT;"   | durationInSeconds(1994, Month.NOVEMBER, 6, 8, 49, 37)
        "Set-Cookie: name=value; expires=Sun Nov  6 08:49:37 1994;"         | durationInSeconds(1994, Month.NOVEMBER, 6, 8, 49, 37)
    }

    void "test parsing cookie from header #setCookieHeader"() {

        given:
        Cookie cookie = CookieUtil.getCookieFromString("$setCookieHeader")

        expect:
        cookie.getName() == name
        cookie.getValue() == value
        cookie.getDomain() == domain
        cookie.getPath() == path
        cookie.isHttpOnly() == httpOnly
        cookie.isSecure() == secure
        cookie.getMaxAge() == maxAge
        cookie.getSameSite().orElse(null) == sameSite

        where:
        setCookieHeader
                | name                              | value
                | domain | path | httpOnly | secure | sameSite     | maxAge

        "Set-Cookie: mid=0123456789A; Secure; SameSite=Lax"
                | "mid"                             | "0123456789A"
                | null   | null | false    | true   | SameSite.Lax | 0
        "Set-Cookie:        mid=0123456789A"
                | "mid"                             | "0123456789A"
                | null   | null | false    | false  | null         | 0
        "Set-Cookie: mid=0123456789A;"
                | "mid"                             | "0123456789A"
                | null   | null | false    | false  | null         | 0
        "Set-Cookie:mid=0123456789A;;;; Secure; SameSite=None"
                | "mid"                             | "0123456789A"
                | null   | null | false    | true   | SameSite.None| 0

        "set-cookie: _simpleauth_sess=\"OX1\\x23\"; Max-Age=15552000; Path=/; expires=Thu, 04-Feb-2021 03:47:49 GMT; secure; HttpOnly"
                | "_simpleauth_sess"                | "OX1\\x23"
                | null | "/" | true | true | null | 15552000

        "Set-Cookie:   user_session=a1BC-d; path=/; expires=Sat, 22 Aug 2020 03:54:17 GMT; secure; HttpOnly; SameSite=Lax"
                | "user_session"                    | "a1BC-d"
                | null | "/" | true | true | SameSite.Lax | durationInSeconds(2020, Month.AUGUST, 22, 3, 54, 17)

        "Set-Cookie:   __Host-user_session_same_site=a1BC-d; path=/; expires=Sat, 22 Aug 2020 03:54:17 GMT; SameSite=Strict; secure; HttpOnly"
                | "__Host-user_session_same_site"   | "a1BC-d"
                | null | "/" | true | true | SameSite.Strict | durationInSeconds(2020, Month.AUGUST, 22, 3, 54, 17)

        "Set-Cookie:   has_recent_activity=1; path=/; expires=Sat, 08 Aug 2020 04:54:17 GMT; secure; HttpOnly; SameSite=Lax"
                | "has_recent_activity"             | "1"
                | null | "/" | true | true | SameSite.Lax | durationInSeconds(2020, Month.AUGUST, 8, 4, 54, 17)

        "Set-Cookie:   _gh_sess=ABCdeF1%2FG%2FH234%2BiJk--lmno%2F--pqrs%3D%3D; path=/; secure; HttpOnly; SameSite=Lax"
                | "_gh_sess"                        | "ABCdeF1%2FG%2FH234%2BiJk--lmno%2F--pqrs%3D%3D"
                | null | "/" | true | true | SameSite.Lax | 0

    }
}
