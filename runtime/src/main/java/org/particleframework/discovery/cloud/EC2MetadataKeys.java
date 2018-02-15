package org.particleframework.discovery.cloud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum EC2MetadataKeys {

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
            return new URL("http://169.254.169.254/" + AWS_API_VERSION + "/dynamic/instance-identity/document");
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

    EC2MetadataKeys(String name) {
        this(name, "");
    }

    EC2MetadataKeys(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public String getName() {
        return name;
    }

    // override to apply prepend and append
    public URL getURL(String prepend, String append) throws MalformedURLException {
        return new URL(AWS_METADATA_URL + path + name);
    }

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

    public String toString() {
        return getName();
    }



    public static final String AWS_API_VERSION = "latest";
    public static final String AWS_METADATA_URL = "http://169.254.169.254/" + AWS_API_VERSION + "/meta-data/";


}
