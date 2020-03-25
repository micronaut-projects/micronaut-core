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
package io.micronaut.cli.console.proxy

import groovy.transform.CompileStatic
import io.micronaut.cli.util.CliSettings

/**
 * An Authenticator that authenticates via System properties
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class SystemPropertiesAuthenticator extends Authenticator {

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        if (getRequestorType() == RequestorType.PROXY) {
            return new PasswordAuthentication(
                System.getProperty(CliSettings.PROXY_HTTP_USER, ""),
                System.getProperty(CliSettings.PROXY_HTTP_PASSWORD, "").toCharArray());
        }
        return null
    }
}
