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

package io.micronaut.discovery.vault.config.client.v1.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.vault.config.client.AbstractVaultResponse;

import java.util.List;
import java.util.Map;

/**
 *  Vault Response Envelope.
 *
 *  @author thiagolocatelli
 *  @author graemerocher
 *  @since 1.1.1
 */
public class VaultResponseV1 extends AbstractVaultResponse<Map<String, Object>> {

    /**
     * Constructor for VaultResponseV1.
     *
     * @param data The data object
     * @param leaseDuration The token lease duration
     * @param leaseId The token lease id
     * @param requestId The vault request id
     * @param wrapInfo The wrap info object
     * @param renewable The flag indicating the vault token is renewable
     * @param warnings The list of warnings
     */
    @JsonCreator
    @Internal
    public VaultResponseV1(
            @JsonProperty("data") final Map<String, Object> data,
            @JsonProperty("lease_duration") final Long leaseDuration,
            @JsonProperty("lease_id") final String leaseId,
            @JsonProperty("request_id") final String requestId,
            @JsonProperty("wrap_info") final Map<String, String> wrapInfo,
            @JsonProperty("renewable") final boolean renewable,
            @JsonProperty("warnings") final List<String> warnings) {

        super(data, leaseDuration, leaseId, requestId, wrapInfo, renewable,
                warnings);
    }
}
