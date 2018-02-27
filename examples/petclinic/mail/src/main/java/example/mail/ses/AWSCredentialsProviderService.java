package example.mail.ses;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import org.particleframework.context.annotation.Requires;
import org.particleframework.context.annotation.Value;
import javax.inject.Singleton;

@Singleton
@Requires(property = "aws.accessKeyId")
public class AWSCredentialsProviderService implements AWSCredentialsProvider {

    @Value("${aws.accessKeyId}")
    String accessKey;

    @Value("${aws.secretKey}")
    String secretKey;

    @Override
    public AWSCredentials getCredentials() {
        return new BasicAWSCredentials(accessKey, secretKey);
    }

    @Override
    public void refresh() {

    }
}
