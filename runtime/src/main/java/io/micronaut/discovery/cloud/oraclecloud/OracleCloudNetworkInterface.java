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
package io.micronaut.discovery.cloud.oraclecloud;

import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.cloud.NetworkInterface;

/**
 * A {@link NetworkInterface} implementation for Google.
 *
 * @author Todd Sharp
 * @since 1.2.0
 */
@Internal
class OracleCloudNetworkInterface extends NetworkInterface {

    @Override
    protected void setIpv4(String ipv4) {
        super.setIpv4(ipv4);
    }

    @Override
    protected void setIpv6(String ipv6) {
        super.setIpv6(ipv6);
    }

    @Override
    protected void setName(String name) {
        super.setName(name);
    }

    @Override
    protected void setMac(String mac) {
        super.setMac(mac);
    }

    @Override
    protected void setId(String id) {
        super.setId(id);
    }

    @Override
    protected void setGateway(String gateway) {
        super.setGateway(gateway);
    }

    @Override
    protected void setNetwork(String network) {
        super.setNetwork(network);
    }

    @Override
    protected void setNetmask(String netmask) {
        super.setNetmask(netmask);
    }
}
