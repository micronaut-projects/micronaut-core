package io.micronaut.discovery.aws.route53.registration;

import com.amazonaws.services.servicediscovery.model.GetOperationRequest;
import com.amazonaws.services.servicediscovery.model.GetOperationResult;
import io.micronaut.context.annotation.Value;
import io.micronaut.discovery.aws.route53.Route53AutoRegistrationConfiguration;
import io.micronaut.runtime.server.EmbeddedServerInstance;
import io.micronaut.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

@Singleton
public class ServiceRegistrationStatusJob {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceRegistrationStatusJob.class);

    @Value("${server.serviceRegisterFile:/tmp/serviceOperationStatus.txt}")
    String operationStatusFile;

    @Inject
    Route53AutoNamingRegistrationClient route53AutoNamingRegistrationClient;

    @Inject
    Route53AutoRegistrationConfiguration route53AutoRegistrationConfiguration;

    @Inject
    EmbeddedServerInstance embeddedServerInstance;

    @Scheduled(fixedDelay = "10s")
    void executeEveryTen() throws IOException {
        // log for status file with an active operation id
        File operationFile = new File(operationStatusFile);
        if (operationFile.exists()) {
            FileInputStream fileInputStream = new FileInputStream(operationFile);
            BufferedReader buf = new BufferedReader(new InputStreamReader(fileInputStream));
            String operationId = buf.readLine();
            GetOperationRequest operationRequest = new GetOperationRequest().withOperationId(operationId);
            GetOperationResult result = route53AutoNamingRegistrationClient.getDiscoveryClient().getOperation(operationRequest);
            if (result.getOperation().getStatus().equalsIgnoreCase("failure") || result.getOperation().getStatus().equalsIgnoreCase("success")) {
                LOG.info("Service regisration for operation "+operationId+" resulted in "+result.getOperation().getStatus());
                fileInputStream.close();
                operationFile.delete();
                if (result.getOperation().getStatus().equalsIgnoreCase("failure")) {
                    if (route53AutoRegistrationConfiguration.isFailFast()) {
                        LOG.error("Error registering instance shutting down instance.");
                        ((EmbeddedServerInstance) embeddedServerInstance).getEmbeddedServer().stop();
                    }
                }
            }
        }
        LOG.info("Simple Job every 10 seconds :{}", new SimpleDateFormat("dd/M/yyyy hh:mm:ss").format(new Date()));
    }
}
