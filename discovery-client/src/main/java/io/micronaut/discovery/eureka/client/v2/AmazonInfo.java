/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.micronaut.discovery.eureka.client.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.discovery.eureka.EurekaConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An AWS specific {@link DataCenterInfo} implementation.
 * <p>
 * Gets AWS specific information for registration with eureka by making a HTTP call to an AWS service as recommended
 * by AWS.
 * </p>
 *
 * @author Karthik Ranganathan, Greg Kim
 */
public class AmazonInfo implements DataCenterInfo {

    private static final String AWS_API_VERSION = "latest";
    // CHECKSTYLE:OFF
    private static final String AWS_METADATA_URL = "http://169.254.169.254/" + AWS_API_VERSION + "/meta-data/";
    // CHECKSTYLE:ON

    /**
     * MetaData key.
     */
    public enum MetaDataKey {
        instanceId("instance-id"),  // always have this first as we use it as a fail fast mechanism
        amiId("ami-id"),
        instanceType("instance-type"),
        localIpv4("local-ipv4"),
        localHostname("local-hostname"),
        availabilityZone("availability-zone", "placement/"),
        publicHostname("public-hostname"),
        publicIpv4("public-ipv4"),
        mac("mac"),  // mac is declared above vpcId so will be found before vpcId (where it is needed)
        vpcId("vpc-id", "network/interfaces/macs/") {
            @Override
            public URL getURL(String prepend, String mac) throws MalformedURLException {
                return new URL(AWS_METADATA_URL + this.path + mac + "/" + this.name);
            }
        },
        accountId("accountId") {
            private Pattern pattern = Pattern.compile("\"accountId\"\\s?:\\s?\\\"([A-Za-z0-9]*)\\\"");

            @Override
            public URL getURL(String prepend, String append) throws MalformedURLException {
                // CHECKSTYLE:OFF
                return new URL("http://169.254.169.254/" + AWS_API_VERSION + "/dynamic/instance-identity/document");
                // CHECKSTYLE:ON
            }

            // no need to use a json deserializer, do a custom regex parse
            @Override
            public String read(InputStream inputStream) throws IOException {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
                    String toReturn = null;
                    String inputLine;
                    while ((inputLine = br.readLine()) != null) {
                        Matcher matcher = pattern.matcher(inputLine);
                        if (toReturn == null && matcher.find()) {
                            toReturn = matcher.group(1);
                            // don't break here as we want to read the full buffer for a clean connection close
                        }
                    }

                    return toReturn;
                }
            }
        };

        protected String name;
        protected String path;

        /**
         * @param name The name
         */
        MetaDataKey(String name) {
            this(name, "");
        }

        /**
         * @param name The name
         * @param path The path
         */
        MetaDataKey(String name, String path) {
            this.name = name;
            this.path = path;
        }

        /**
         * @return The name
         */
        public String getName() {
            return name;
        }

        /**
         * Override to apply prepend and append.
         *
         * @param prepend The prefix
         * @param append  The suffix
         * @return The new URL
         * @throws MalformedURLException if the URL is not valid
         */
        public URL getURL(String prepend, String append) throws MalformedURLException {
            return new URL(AWS_METADATA_URL + path + name);
        }

        /**
         * @param inputStream The input stream
         * @return The information read
         * @throws IOException if there is an error
         */
        public String read(InputStream inputStream) throws IOException {
            String toReturn;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
                String line = br.readLine();
                toReturn = line;

                while (line != null) {  // need to read all the buffer for a clean connection close
                    line = br.readLine();
                }

                return toReturn;
            }
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    private Map<String, String> metadata;

    /**
     * Default constructor.
     */
    public AmazonInfo() {
        this.metadata = new HashMap<>();
    }

    /**
     * Constructor provided for deserialization framework. It is expected that {@link AmazonInfo} will be built
     * programmatically using {@link AmazonInfo.Builder}.
     *
     * @param name     this value is ignored, as it is always set to "Amazon"
     * @param metadata The metadata
     */
    @JsonCreator
    AmazonInfo(
        @JsonProperty("name") String name,
        @JsonProperty("metadata") HashMap<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     * Constructor provided for deserialization framework. It is expected that {@link AmazonInfo} will be built
     * programmatically using {@link AmazonInfo.Builder}.
     *
     * @param name     this value is ignored, as it is always set to "Amazon"
     * @param metadata The metadata
     */
    AmazonInfo(
        @JsonProperty("name") String name,
        @JsonProperty("metadata") Map<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     * @return The name. It always returns "Amazon"
     */
    @Override
    public Name getName() {
        return Name.Amazon;
    }

    /**
     * Get the metadata information specific to AWS.
     *
     * @return the map of AWS metadata as specified by {@link AmazonInfo.MetaDataKey}.
     */
    @JsonProperty("metadata")
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Set AWS metadata.
     *
     * @param metadataMap the map containing AWS metadata.
     */
    public void setMetadata(Map<String, String> metadataMap) {
        this.metadata = metadataMap;
    }

    /**
     * Gets the AWS metadata specified in {@link AmazonInfo.MetaDataKey}.
     *
     * @param key the metadata key.
     * @return String returning the value.
     */
    public String get(AmazonInfo.MetaDataKey key) {
        return metadata.get(key.getName());
    }

    /**
     * @return The instance id
     */
    @JsonIgnore
    public String getId() {
        return get(AmazonInfo.MetaDataKey.instanceId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AmazonInfo)) {
            return false;
        }

        AmazonInfo that = (AmazonInfo) o;

        if (metadata != null ? !metadata.equals(that.metadata) : that.metadata != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return metadata != null ? metadata.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "AmazonInfo{" +
            "metadata=" + metadata +
            '}';
    }

    /**
     * @param metaDataKey         The metadata key
     * @param url                 The URL
     * @param connectionTimeoutMs The connection timeout
     * @param readTimeoutMs       The read timeout in milliseconds
     * @return The metadata
     * @throws IOException if there is an error
     */
    @SuppressWarnings("EmptyBlock")
    static String readEc2MetadataUrl(AmazonInfo.MetaDataKey metaDataKey, URL url, int connectionTimeoutMs, int readTimeoutMs) throws IOException {
        HttpURLConnection uc = (HttpURLConnection) url.openConnection();
        uc.setConnectTimeout(connectionTimeoutMs);
        uc.setReadTimeout(readTimeoutMs);

        if (uc.getResponseCode() != HttpURLConnection.HTTP_OK) {  // need to read the error for clean connection close
            try (BufferedReader br = new BufferedReader(new InputStreamReader(uc.getErrorStream()))) {
                while (br.readLine() != null) {
                    // do nothing but keep reading the line
                }
            }
        } else {
            return metaDataKey.read(uc.getInputStream());
        }

        return null;
    }

    /**
     * Builder class.
     */
    public static final class Builder {
        @SuppressWarnings("ConstantName")
        private static final Logger logger = LoggerFactory.getLogger(AmazonInfo.Builder.class);
        private static final int SLEEP_TIME_MS = 100;

        private AmazonInfo result;

        private Builder() {
            result = new AmazonInfo();
        }

        /**
         * @return The new builder
         */
        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * @param key   The key
         * @param value The value
         * @return The builder instance
         */
        public Builder addMetadata(MetaDataKey key, String value) {
            result.metadata.put(key.getName(), value);
            return this;
        }

        /**
         * Build the {@link InstanceInfo} information.
         *
         * @return AWS specific instance information.
         */
        public AmazonInfo build() {
            return result;
        }

        /**
         * Build the {@link AmazonInfo} automatically via HTTP calls to instance
         * metadata API.
         *
         * @param config The Eureka configuration
         * @return the instance information specific to AWS.
         */
        @SuppressWarnings("MagicNumber")
        public AmazonInfo autoBuild(EurekaConfiguration config) {

            for (AmazonInfo.MetaDataKey key : AmazonInfo.MetaDataKey.values()) {
                int numOfRetries = config.getRegistration().getRetryCount();
                while (numOfRetries-- > 0) {
                    try {
                        String mac = null;
                        if (key == AmazonInfo.MetaDataKey.vpcId) {
                            mac = result.metadata.get(AmazonInfo.MetaDataKey.mac.getName());  // mac should be read before vpcId due to declaration order
                        }
                        URL url = key.getURL(null, mac);
                        Duration readTimeout = config.getReadTimeout().orElse(Duration.ofSeconds(10));
                        String value = readEc2MetadataUrl(key, url, (int) readTimeout.toMillis(), (int) readTimeout.toMillis());
                        if (value != null) {
                            result.metadata.put(key.getName(), value);
                        }

                        break;
                    } catch (Throwable e) {
                        if (config.shouldLogAmazonMetadataErrors()) {
                            logger.warn("Cannot get the value for the Amazon metadata key: {} Reason :", key, e);
                        }
                        if (numOfRetries >= 0) {
                            try {
                                Thread.sleep(SLEEP_TIME_MS);
                            } catch (InterruptedException e1) {

                            }
                            continue;
                        }
                    }
                }

                if (key == AmazonInfo.MetaDataKey.instanceId
                    && config.getRegistration().isFailFast()
                    && !result.metadata.containsKey(AmazonInfo.MetaDataKey.instanceId.getName())) {

                    logger.warn("Skipping the rest of AmazonInfo init as we were not able to load instanceId after " +
                            "the configured number of retries: {}, per fail fast configuration: {}",
                        config.getRegistration().getRetryCount(), config.getRegistration().isFailFast());
                    break;  // break out of loop and return whatever we have thus far
                }
            }
            return result;
        }
    }
}
