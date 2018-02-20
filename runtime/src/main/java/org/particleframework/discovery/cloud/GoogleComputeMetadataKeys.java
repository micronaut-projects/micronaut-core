package org.particleframework.discovery.cloud;

/**
 * @author rvanderwerf
 * @since 1.0
 */
public enum GoogleComputeMetadataKeys {

    DESCRIPTION("description"),
    HOSTNAME("hostname"),
    ID("id"),
    ATTRIBUTES("attributes"),
    CPU_PLATFORM("cpuPlatform"),
    DISKS("disks"),
    DNS_SERVERS("dnsServers"),
    FORWARDED_IPS("forewardedIps"),
    GATEWAY("gateway"),
    IP("ip"),
    IP_ALIASES("ipAliases"),
    MAC("mac"),
    NETWORK("network"),
    SCOPES("scopes"),
    MACHINE_TYPE("machineType"),
    MAINTENANCE_EVENT("maintenanceEvent"),
    NAME("name"),
    NETWORK_INTERFACES("networkInterfaces"),
    SERVICE_ACCOUNTS("serviceAccounts"),
    DEFAULTS("default"),
    PROJECT_ID("projectId"),
    NUMERIC_PROJECT_ID("numericProjectId"),
    ZONE("zone"),
    TAGS("tags"),
    VIRTUAL_CLOCK("virtualClock"),
    IMAGE("image"),
    LICENSES("licenses"),
    ACCESS_CONFIGS("accessConfigs"),
    NETMASK("subnetmask");



    protected String name;
    protected String path;


    GoogleComputeMetadataKeys(String name) {
        this(name, "");
    }

    GoogleComputeMetadataKeys(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public String getName() {
        return name;
    }



}
