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
package io.micronaut.discovery.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class contains some of the utility functions previously found in DiscoveryClient, but should be elsewhere.
 * It *does not yet* clean up the moved code.
 *
 * Forked from https://raw.githubusercontent.com/Netflix/eureka/master/eureka-client/src/main/java/com/netflix/discovery/endpoint/EndpointUtils.java.
 *
 * @author Tomasz Bak
 * @author graemerocher
 */
@Internal
public class EndpointUtil {
    private static final Logger LOG = LoggerFactory.getLogger(EndpointUtil.class);

    /**
     * The default region.
     */
    private static final String DEFAULT_REGION = "default";
    private static final String DEFAULT_ZONE = "default";

    /**
     * Get the list of all eureka service urls from DNS for the eureka client to
     * talk to. The client picks up the service url from its zone and then fails over to
     * other zones randomly. If there are multiple servers in the same zone, the client once
     * again picks one randomly. This way the traffic will be distributed in the case of failures.
     *
     * @param embeddedServer the embedded server
     * @param instanceConfiguration The instance configuration
     * @param discoveryClientConfiguration The discovery client configuration
     *
     * @return The list of all eureka service urls for the eureka client to talk to.
     */
    public static List<String> getServiceUrlsFromDNS(
            EmbeddedServer embeddedServer,
            ApplicationConfiguration.InstanceConfiguration instanceConfiguration,
            DiscoveryClientConfiguration discoveryClientConfiguration) {

        return getServiceUrlsFromDNS(
                instanceConfiguration,
                discoveryClientConfiguration,
                instanceConfiguration.getZone().orElse(DEFAULT_ZONE),
                true,
                new InstanceInfoBasedUrlRandomizer(embeddedServer)
        );
    }

    /**
     * Get the list of all eureka service urls from DNS for the eureka client to
     * talk to. The client picks up the service url from its zone and then fails over to
     * other zones randomly. If there are multiple servers in the same zone, the client once
     * again picks one randomly. This way the traffic will be distributed in the case of failures.
     *
     * @param serviceInstance the clientConfig to use
     * @param discoveryClientConfiguration The discovery client configuration
     * @param instanceZone The zone in which the client resides.
     * @param preferSameZone true if we have to prefer the same zone as the client, false otherwise.
     * @param randomizer a randomizer to randomized returned urls
     *
     * @return The list of all eureka service urls for the eureka client to talk to.
     */
    private static List<String> getServiceUrlsFromDNS(
            ApplicationConfiguration.InstanceConfiguration serviceInstance,
            DiscoveryClientConfiguration discoveryClientConfiguration,
            String instanceZone,
            boolean preferSameZone,
            ServiceUrlRandomizer randomizer) {
        final ConvertibleValues<String> values = ConvertibleValues.of(serviceInstance.getMetadata());
        String region = values.get(ServiceInstance.REGION, String.class).orElse(DEFAULT_REGION);
        // Get zone-specific DNS names for the given region so that we can get a
        // list of available zones
        Map<String, List<String>> zoneDnsNamesMap = getZoneBasedDiscoveryUrlsFromRegion(discoveryClientConfiguration, region);
        Set<String> availableZones = zoneDnsNamesMap.keySet();
        List<String> zones = new ArrayList<>(availableZones);
        if (zones.isEmpty()) {
            throw new RuntimeException("No available zones configured for the instanceZone " + instanceZone);
        }
        int zoneIndex = 0;
        boolean zoneFound = false;
        for (String zone : zones) {
            LOG.debug("Checking if the instance zone {} is the same as the zone from DNS {}", instanceZone, zone);
            if (preferSameZone) {
                if (instanceZone.equalsIgnoreCase(zone)) {
                    zoneFound = true;
                }
            } else {
                if (!instanceZone.equalsIgnoreCase(zone)) {
                    zoneFound = true;
                }
            }
            if (zoneFound) {
                LOG.debug("The zone index from the list {} that matches the instance zone {} is {}",
                        zones, instanceZone, zoneIndex);
                break;
            }
            zoneIndex++;
        }
        if (zoneIndex >= zones.size()) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("No match for the zone {} in the list of available zones {}",
                        instanceZone, zones.toArray());
            }
        } else {
            // Rearrange the zones with the instance zone first
            for (int i = 0; i < zoneIndex; i++) {
                String zone = zones.remove(0);
                zones.add(zone);
            }
        }

        // Now get the eureka urls for all the zones in the order and return it
        List<String> serviceUrls = new ArrayList<>();
        for (String zone : zones) {
            for (String zoneCname : zoneDnsNamesMap.get(zone)) {
                List<String> ec2Urls = new ArrayList<>(getEC2DiscoveryUrlsFromZone(zoneCname, DiscoveryUrlType.CNAME));
                // Rearrange the list to distribute the load in case of multiple servers
                if (ec2Urls.size() > 1) {
                    randomizer.randomize(ec2Urls);
                }
                for (String ec2Url : ec2Urls) {
                    StringBuilder sb = new StringBuilder()
                            .append("http://")
                            .append(ec2Url)
                            .append(":")
                            .append(discoveryClientConfiguration.getPort());
                    final Optional<String> contextPath = discoveryClientConfiguration.getContextPath();
                    if (contextPath.isPresent()) {
                        final String path = contextPath.get();
                        if (!path.startsWith("/")) {
                            sb.append("/");
                        }
                        sb.append(path);
                        if (!path.endsWith("/")) {
                            sb.append("/");
                        }
                    } else {
                        sb.append("/");
                    }
                    String serviceUrl = sb.toString();
                    LOG.debug("The EC2 url is {}", serviceUrl);
                    serviceUrls.add(serviceUrl);
                }
            }
        }
        // Rearrange the fail over server list to distribute the load
        String primaryServiceUrl = serviceUrls.remove(0);
        randomizer.randomize(serviceUrls);
        serviceUrls.add(0, primaryServiceUrl);

        if (LOG.isDebugEnabled()) {
            LOG.debug("This client will talk to the following serviceUrls in order : {} ",
                    (Object) serviceUrls.toArray());
        }
        return serviceUrls;
    }



    /**
     * Get the list of EC2 URLs given the zone name.
     *
     * @param dnsName The dns name of the zone-specific CNAME
     * @param type CNAME or EIP that needs to be retrieved
     * @return The list of EC2 URLs associated with the dns name
     */
    private static Set<String> getEC2DiscoveryUrlsFromZone(String dnsName, DiscoveryUrlType type) {
        Set<String> eipsForZone;
        try {
            dnsName = "txt." + dnsName;
            LOG.debug("The zone url to be looked up is {} :", dnsName);
            Set<String> ec2UrlsForZone = DnsResolver.getCNamesFromTxtRecord(dnsName);
            for (String ec2Url : ec2UrlsForZone) {
                LOG.debug("The eureka url for the dns name {} is {}", dnsName, ec2Url);
                ec2UrlsForZone.add(ec2Url);
            }
            if (DiscoveryUrlType.CNAME.equals(type)) {
                return ec2UrlsForZone;
            }
            eipsForZone = new TreeSet<>();
            for (String cname : ec2UrlsForZone) {
                String[] tokens = cname.split("\\.");
                String ec2HostName = tokens[0];
                String[] ips = ec2HostName.split("-");
                StringBuilder eipBuffer = new StringBuilder();
                for (int ipCtr = 1; ipCtr < 5; ipCtr++) {
                    eipBuffer.append(ips[ipCtr]);
                    if (ipCtr < 4) {
                        eipBuffer.append(".");
                    }
                }
                eipsForZone.add(eipBuffer.toString());
            }
            LOG.debug("The EIPS for {} is {} :", dnsName, eipsForZone);
        } catch (Throwable e) {
            throw new RuntimeException("Cannot get cnames bound to the region:" + dnsName, e);
        }
        return eipsForZone;
    }

    /**
     * Get the zone based CNAMES that are bound to a region.
     *
     *
     * @param discoveryClientConfiguration The discovery client configuration
     * @param region The region in use
     *            - The region for which the zone names need to be retrieved
     * @return - The list of CNAMES from which the zone-related information can
     *         be retrieved
     */
    private static Map<String, List<String>> getZoneBasedDiscoveryUrlsFromRegion(
            DiscoveryClientConfiguration discoveryClientConfiguration,
            String region) {
        String discoveryDnsName = null;
        try {
            discoveryDnsName = "txt." + region + "." + discoveryClientConfiguration.getHost();

            LOG.debug("The region url to be looked up is {} :", discoveryDnsName);
            Set<String> zoneCnamesForRegion = new TreeSet<>(DnsResolver.getCNamesFromTxtRecord(discoveryDnsName));
            Map<String, List<String>> zoneCnameMapForRegion = new TreeMap<>();
            for (String zoneCname : zoneCnamesForRegion) {
                String zone;
                if (isEC2Url(zoneCname)) {
                    throw new RuntimeException(
                            "Cannot find the right DNS entry for "
                                    + discoveryDnsName
                                    + ". "
                                    + "Expected mapping of the format <aws_zone>.<domain_name>");
                } else {
                    String[] cnameTokens = zoneCname.split("\\.");
                    zone = cnameTokens[0];
                    LOG.debug("The zoneName mapped to region {} is {}", region, zone);
                }
                List<String> zoneCnamesSet = zoneCnameMapForRegion.computeIfAbsent(zone, k -> new ArrayList<>());
                zoneCnamesSet.add(zoneCname);
            }
            return zoneCnameMapForRegion;
        } catch (Throwable e) {
            throw new RuntimeException("Cannot get cnames bound to the region:" + discoveryDnsName, e);
        }
    }

    // FIXME this is no valid for vpc
    private static boolean isEC2Url(String zoneCname) {
        return zoneCname.startsWith("ec2");
    }

    /**
     * Record types.
     */
    private enum DiscoveryUrlType {
        CNAME, A
    }

    /**
     * A randomizer interface.
     */
    private interface ServiceUrlRandomizer {
        void randomize(List<String> urlList);
    }

    /**
     * Default randomizer.
     */
    private static class InstanceInfoBasedUrlRandomizer implements ServiceUrlRandomizer {
        private final EmbeddedServer instanceInfo;

        InstanceInfoBasedUrlRandomizer(EmbeddedServer instanceInfo) {
            this.instanceInfo = instanceInfo;
        }

        @Override
        public void randomize(List<String> urlList) {
            int listSize = 0;
            if (urlList != null) {
                listSize = urlList.size();
            }
            if ((instanceInfo == null) || (listSize == 0)) {
                return;
            }
            // Find the hashcode of the instance hostname and use it to find an entry
            // and then arrange the rest of the entries after this entry.
            int instanceHashcode = instanceInfo.getHost().hashCode();
            if (instanceHashcode < 0) {
                instanceHashcode = instanceHashcode * -1;
            }
            int backupInstance = instanceHashcode % listSize;
            for (int i = 0; i < backupInstance; i++) {
                String zone = urlList.remove(0);
                urlList.add(zone);
            }
        }
    }
}
