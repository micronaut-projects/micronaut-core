package io.micronaut.discovery.aws.route53;


import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync;
import io.micronaut.context.env.Environment;

public interface AWSServiceDiscoveryResolver {


    AWSServiceDiscoveryAsync resolve(Environment environment);


}
