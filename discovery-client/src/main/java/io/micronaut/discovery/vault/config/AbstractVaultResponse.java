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
package io.micronaut.discovery.vault.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

import javax.annotation.concurrent.Immutable;
import java.util.List;
import java.util.Map;

/**
 *  Vault Response Envelope.
 *
 *  @param <T> type of the data
 *
 *  @author thiagolocatelli
 *  @since 1.2.0
 */
@Immutable
@Introspected
public abstract class AbstractVaultResponse<T> {

    protected T data;

    @JsonProperty("lease_duration")
    protected Long leaseDuration;

    @JsonProperty("lease_id")
    protected String leaseId;

    @JsonProperty("request_id")
    protected String requestId;

    @JsonProperty("wrap_info")
    protected Map<String, String> wrapInfo;

    protected boolean renewable;

    protected List<String> warnings;

    /**
     * Constructor for AbstractVaultResponse.
     *
     * @param data The data object
     * @param leaseDuration The token lease duration
     * @param leaseId The token lease id
     * @param requestId The vault request id
     * @param wrapInfo The wrap info object
     * @param renewable The flag indicating the vault token is renewable
     * @param warnings The list of warnings
     */
    public AbstractVaultResponse(final T data,
                                 final Long leaseDuration,
                                 final String leaseId,
                                 final String requestId,
                                 final Map<String, String> wrapInfo,
                                 final boolean renewable,
                                 final List<String> warnings) {
        this.data = data;
        this.leaseDuration = leaseDuration;
        this.leaseId = leaseId;
        this.requestId = requestId;
        this.wrapInfo = wrapInfo;
        this.renewable = renewable;
        this.warnings = warnings;
    }

    /**
     * @return The data object
     */
    @JsonIgnore
    public abstract Map<String, Object> getSecrets();

    /**
     * @return The data
     */
    public T getData() {
        return data;
    }

    /**
     * Set the data object.
     *
     * @param data the data object
     */
    public void setData(final T data) {
        this.data = data;
    }

    /**
     * @return The token lease duration
     */
    public Long getLeaseDuration() {
        return leaseDuration;
    }

    /**
     * Set the token lease duration.
     *
     * @param leaseDuration token lease duration
     */
    public void setLeaseDuration(final Long leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    /**
     * @return The token lease id
     */
    public String getLeaseId() {
        return leaseId;
    }

    /**
     * Set the token release id.
     *
     * @param leaseId token release id
     */
    public void setLeaseId(final String leaseId) {
        this.leaseId = leaseId;
    }

    /**
     * @return The vault request id
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Set the vault request id.
     *
     * @param requestId vault request id
     */
    public void setRequestId(final String requestId) {
        this.requestId = requestId;
    }

    /**
     * @return The wrap info object
     */
    public Map<String, String> getWrapInfo() {
        return wrapInfo;
    }

    /**
     * Set the wrap info object.
     *
     * @param wrapInfo wrap info object
     */
    public void setWrapInfo(final Map<String, String> wrapInfo) {
        this.wrapInfo = wrapInfo;
    }

    /**
     * @return The flag indicating the vault token is renewable
     */
    public boolean isRenewable() {
        return renewable;
    }

    /**
     * Set the flag indicating the vault token is renewable.
     *
     * @param renewable flag indicating the vault token is renewable
     */
    public void setRenewable(final boolean renewable) {
        this.renewable = renewable;
    }

    /**
     * @return List of warning
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * Set the list of warnings.
     *
     * @param warnings list of warning
     */
    public void setWarnings(final List<String> warnings) {
        this.warnings = warnings;
    }
}
