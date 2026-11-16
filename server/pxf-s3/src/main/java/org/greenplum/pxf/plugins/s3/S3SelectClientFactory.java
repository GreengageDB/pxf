package org.greenplum.pxf.plugins.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.s3a.Constants;
import org.apache.hadoop.fs.s3a.auth.CredentialProviderListFactory;
import org.apache.hadoop.fs.s3a.impl.ConfigurationHelper;
import org.apache.hadoop.util.Preconditions;
import org.apache.hadoop.util.VersionInfo;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.apache.hadoop.fs.s3a.Constants.*;
import static org.apache.hadoop.fs.s3a.S3AUtils.lookupPassword;


/**
 * TODO:
 * It is a temporary factory to support AWS SDK v1. The copy of DefaultS3ClientFactory#createS3Client from Hadoop 3.3.6
 * It will be replaced by the factory to support AWS SDK v2
 */
@Slf4j
public class S3SelectClientFactory {

    public S3SelectClientFactory() {
    }

    public AmazonS3 create(URI name, Configuration conf) throws IOException {
        AwsCredentialsProvider credentials = CredentialProviderListFactory.createAWSCredentialProviderList(name, conf);
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard()
                .withCredentials(new SdkV1CredentialsProvider(credentials))
                .withClientConfiguration(createAwsConf(conf, name.getHost(), Constants.AWS_SERVICE_IDENTIFIER_S3))
                .withPathStyleAccessEnabled(conf.getBoolean(PATH_STYLE_ACCESS, false));
        String endpoint = conf.getTrimmed(ENDPOINT, DEFAULT_ENDPOINT);
        String region = conf.getTrimmed(AWS_REGION, "");
        if (endpoint.isEmpty()) {
            builder.withForceGlobalBucketAccessEnabled(true)
                    .withRegion(region.isEmpty() ? AWS_S3_CENTRAL_REGION : region);
        } else {
            builder.withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(endpoint, StringUtils.defaultIfEmpty(region, null)));
        }
        return builder.build();
    }

    private ClientConfiguration createAwsConf(Configuration conf,
                                              String bucket, String awsServiceIdentifier) throws IOException {
        final ClientConfiguration awsConf = new ClientConfiguration();
        initConnectionSettings(conf, awsConf);
        initProxySupport(conf, bucket, awsConf);
        initUserAgent(conf, awsConf);
        if (StringUtils.isNotEmpty(awsServiceIdentifier)) {
            String configKey = null;
            switch (awsServiceIdentifier) {
                case AWS_SERVICE_IDENTIFIER_S3:
                    configKey = SIGNING_ALGORITHM_S3;
                    break;
                case AWS_SERVICE_IDENTIFIER_STS:
                    configKey = SIGNING_ALGORITHM_STS;
                    break;
                default:
                    // Nothing to do. The original signer override is already setup
            }
            if (configKey != null) {
                String signerOverride = conf.getTrimmed(configKey, "");
                if (!signerOverride.isEmpty()) {
                    awsConf.setSignerOverride(signerOverride);
                }
            }
        }
        return awsConf;
    }

    private void initConnectionSettings(Configuration conf, ClientConfiguration awsConf) throws IOException {
        awsConf.setMaxConnections(intOption(conf, MAXIMUM_CONNECTIONS, DEFAULT_MAXIMUM_CONNECTIONS, 1));
        initProtocolSettings(conf, awsConf);
        awsConf.setMaxErrorRetry(intOption(conf, MAX_ERROR_RETRIES, DEFAULT_MAX_ERROR_RETRIES, 0));
        awsConf.setConnectionTimeout(toMillis(conf, ESTABLISH_TIMEOUT, DEFAULT_ESTABLISH_TIMEOUT_DURATION));
        awsConf.setSocketTimeout(toMillis(conf, SOCKET_TIMEOUT, DEFAULT_SOCKET_TIMEOUT_DURATION));
        int sockSendBuffer = intOption(conf, SOCKET_SEND_BUFFER, DEFAULT_SOCKET_SEND_BUFFER, 2048);
        int sockRecvBuffer = intOption(conf, SOCKET_RECV_BUFFER, DEFAULT_SOCKET_RECV_BUFFER, 2048);
        long requestTimeoutMillis = conf.getTimeDuration(REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT_DURATION.toMillis(), TimeUnit.MILLISECONDS, TimeUnit.MILLISECONDS);

        if (requestTimeoutMillis > Integer.MAX_VALUE) {
            log.debug("Request timeout is too high({} ms). Setting to {} ms instead",
                    requestTimeoutMillis, Integer.MAX_VALUE);
            requestTimeoutMillis = Integer.MAX_VALUE;
        }
        awsConf.setRequestTimeout((int) requestTimeoutMillis);
        awsConf.setSocketBufferSizeHints(sockSendBuffer, sockRecvBuffer);
        String signerOverride = conf.getTrimmed(SIGNING_ALGORITHM, "");
        if (!signerOverride.isEmpty()) {
            log.debug("Signer override = {}", signerOverride);
            awsConf.setSignerOverride(signerOverride);
        }
    }

    private void initProtocolSettings(Configuration conf, ClientConfiguration awsConf) {
        boolean secureConnections = conf.getBoolean(SECURE_CONNECTIONS,
                DEFAULT_SECURE_CONNECTIONS);
        awsConf.setProtocol(secureConnections ? Protocol.HTTPS : Protocol.HTTP);
    }

    private void initProxySupport(Configuration conf,
                                  String bucket,
                                  ClientConfiguration awsConf) throws IllegalArgumentException, IOException {
        String proxyHost = conf.getTrimmed(PROXY_HOST, "");
        int proxyPort = conf.getInt(PROXY_PORT, -1);
        if (!proxyHost.isEmpty()) {
            awsConf.setProxyHost(proxyHost);
            if (proxyPort >= 0) {
                awsConf.setProxyPort(proxyPort);
            } else {
                if (conf.getBoolean(SECURE_CONNECTIONS, DEFAULT_SECURE_CONNECTIONS)) {
                    log.warn("Proxy host set without port. Using HTTPS default 443");
                    awsConf.setProxyPort(443);
                } else {
                    log.warn("Proxy host set without port. Using HTTP default 80");
                    awsConf.setProxyPort(80);
                }
            }
            final String proxyUsername = lookupPassword(bucket, conf, PROXY_USERNAME, null, null);
            final String proxyPassword = lookupPassword(bucket, conf, PROXY_PASSWORD, null, null);
            if ((proxyUsername == null) != (proxyPassword == null)) {
                String msg = "Proxy error: " + PROXY_USERNAME + " or " + PROXY_PASSWORD + " set without the other.";
                log.error(msg);
                throw new IllegalArgumentException(msg);
            }
            boolean isProxySecured = conf.getBoolean(PROXY_SECURED, false);
            awsConf.setProxyUsername(proxyUsername);
            awsConf.setProxyPassword(proxyPassword);
            awsConf.setProxyDomain(conf.getTrimmed(PROXY_DOMAIN));
            awsConf.setProxyWorkstation(conf.getTrimmed(PROXY_WORKSTATION));
            awsConf.setProxyProtocol(isProxySecured ? Protocol.HTTPS : Protocol.HTTP);
        } else if (proxyPort >= 0) {
            String msg = "Proxy error: " + PROXY_PORT + " set without " + PROXY_HOST;
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }
    }

    private void initUserAgent(Configuration conf, ClientConfiguration awsConf) {
        String userAgent = "Hadoop " + VersionInfo.getVersion();
        String userAgentPrefix = conf.getTrimmed(USER_AGENT_PREFIX, "");
        if (!userAgentPrefix.isEmpty()) {
            userAgent = userAgentPrefix + ", " + userAgent;
        }
        log.debug("Using User-Agent: {}", userAgent);
        awsConf.setUserAgentPrefix(userAgent);
    }

    private int intOption(Configuration conf, String key, int defVal, int min) {
        int v = conf.getInt(key, defVal);
        Preconditions.checkArgument(
                v >= min,
                String.format("Value of %s: %d is below the minimum value %d", key, v, min));
        log.debug("Value of {} is {}", key, v);
        return v;
    }

    private int toMillis(Configuration conf, String key, Duration defaultValue) {
        long millis = ConfigurationHelper.getDuration(conf, key, defaultValue, TimeUnit.MILLISECONDS, Duration.ZERO)
                .toMillis();
        Preconditions.checkArgument(
                millis <= Integer.MAX_VALUE,
                "Timeout %s exceeds maximum supported value: %d ms", key, millis
        );
        return Math.toIntExact(millis);
    }


    private static class SdkV1CredentialsProvider implements AWSCredentialsProvider {
        private final AwsCredentialsProvider delegate;

        SdkV1CredentialsProvider(AwsCredentialsProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public AWSCredentials getCredentials() {
            AwsCredentials credentials = delegate.resolveCredentials();
            if (credentials.accessKeyId() == null) {
                return new AnonymousAWSCredentials();
            }
            if (credentials instanceof AwsSessionCredentials sessionCredentials) {
                return new BasicSessionCredentials(sessionCredentials.accessKeyId(), sessionCredentials.secretAccessKey(), sessionCredentials.sessionToken());
            }
            return new BasicAWSCredentials(credentials.accessKeyId(), credentials.secretAccessKey());
        }

        @Override
        public void refresh() {
            // NO-OP
        }
    }
}
