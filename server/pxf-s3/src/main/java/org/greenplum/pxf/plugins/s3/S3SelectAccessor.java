package org.greenplum.pxf.plugins.s3;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.s3a.AWSCredentialProviderList;
import org.apache.hadoop.fs.s3a.DefaultS3ClientFactory;
import org.apache.hadoop.fs.s3a.S3ClientFactory;
import org.greenplum.pxf.api.OneRow;
import org.greenplum.pxf.api.model.Accessor;
import org.greenplum.pxf.api.model.BasePlugin;
import org.greenplum.pxf.api.model.GreenplumCSV;
import org.greenplum.pxf.api.model.RequestContext;
import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.hadoop.fs.s3a.Constants.*;
import static org.apache.hadoop.fs.s3a.S3AUtils.propagateBucketOptions;
import static org.apache.hadoop.fs.s3a.auth.CredentialProviderListFactory.createAWSCredentialProviderList;

/**
 * Accessor to read data from S3, using the S3 Select Framework.
 * S3 Select works on a single key (or object), pushing down as
 * much computation as possible to S3. This reduces the amount of
 * data we transfer over the wire, with the purpose of speeding up
 * query times from S3.
 */
public class S3SelectAccessor extends BasePlugin implements Accessor {

    // We call this option compression_codec to make it compatible to
    // the COMPRESSION_CODECs from the s3:text, s3:parquet profiles
    public static final String COMPRESSION_TYPE = "COMPRESSION_CODEC";
    public static final String FILE_HEADER_INFO = "FILE_HEADER";
    public static final String FILE_HEADER_INFO_IGNORE = "IGNORE";
    public static final String FILE_HEADER_INFO_USE = "USE";
    public static final String JSON_TYPE = "JSON-TYPE";
    private static final String UNSUPPORTED_ERR_MESSAGE = "S3 Select accessor does not support write operation.";
    /**
     * Keeps the SDK's shared Netty event loop group alive across
     * S3 Select accessor instances.
     * <p>
     * Each accessor creates and closes its own S3AsyncClient.
     * Without this holder, closing the last client triggers
     * Netty's graceful shutdown with a 2-second quiet period.
     * <p>
     * The holder must remain open while accessors are active.
     */
    private static final SdkAsyncHttpClient EVENT_LOOP_GROUP_HOLDER = NettyNioAsyncHttpClient.builder().build();

    private AtomicBoolean isResultComplete;
    private S3AsyncClient s3Client;
    private AbortableInputStream resultInputStream;
    private S3SelectObjectContentHelper s3SelectObjectContentHelper;
    private BufferedReader reader;
    private int lineReadCount;
    private URI name;

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() {
        name = URI.create(context.getDataSource());
        lineReadCount = 0;
        s3SelectObjectContentHelper = new S3SelectObjectContentHelper();
    }

    @Override
    public boolean openForRead() throws IOException {
        isResultComplete = new AtomicBoolean(false);
        SelectObjectContentRequest request = generateBaseCSVRequest(context);
        s3Client = initS3Client();
        try {
            S3SelectEventStream eventStream = s3SelectObjectContentHelper.select(s3Client, request, name.toString());
            resultInputStream = eventStream.toRecordsInputStream(endEvent -> {
                isResultComplete.set(true);
                LOG.debug("Completed successful S3 select read from {}", name);
            });
        } catch (IOException | RuntimeException e) {
            closeS3Client();
            throw e;
        }
        reader = new BufferedReader(new InputStreamReader(resultInputStream, StandardCharsets.UTF_8));
        return true;
    }

    /**
     * Reads one line at a time
     *
     * @return the next line, or null if the EOF has been reached
     */
    @Override
    public OneRow readNextObject() throws IOException {
        String str;
        try {
            str = reader.readLine();
        } catch (SdkException e) {
            // errors of the event stream are rethrown by the records stream
            throw s3SelectObjectContentHelper.translateException(name.toString(), e);
        }
        if (str != null) {
            lineReadCount++;
            return new OneRow(null, str);
        }

        /*
         * The End Event indicates all matching records have been transmitted.
         * If the End Event is not received, the results may be incomplete.
         */
        if (!isResultComplete.get()) {
            throw new RuntimeException("S3 Select request was incomplete as End Event was not received.");
        }

        return null;
    }

    @Override
    public void closeForRead() {
        LOG.debug("Read {} lines", lineReadCount);

        /*
         * Make sure to close all streams
         */
        if (resultInputStream != null) {
            try {
                resultInputStream.abort();
                resultInputStream.close();
                LOG.debug("ResultInputStream closed");
            } catch (IOException | SdkException e) {
                LOG.error("Unable to close ResultInputStream", e);
            }
        }
        closeS3Client();
    }

    /**
     * Generates the {@link SelectObjectContentRequest} object from
     * the request context.
     *
     * @param context the request context
     * @return a {@link SelectObjectContentRequest}
     */
    SelectObjectContentRequest generateBaseCSVRequest(RequestContext context) {

        InputSerialization inputSerialization = getInputSerialization(context);

        String fileHeaderInfo = context.getOption(FILE_HEADER_INFO);
        boolean usePositionToIdentifyColumn = inputSerialization.csv() != null &&
                (StringUtils.isBlank(fileHeaderInfo) ||
                        !StringUtils.equalsIgnoreCase(FILE_HEADER_INFO_USE, fileHeaderInfo));
        String query = null;
        try {
            DatabaseDialect dialect = new S3SelectDatabaseDialect();
            S3SelectQueryBuilder queryBuilder = new S3SelectQueryBuilder(context, usePositionToIdentifyColumn, dialect);
            query = queryBuilder.buildSelectQuery();
        } catch (SQLException e) {
            LOG.error("Unable to build select query for filter string {}", context.getFilterString());
        }

        LOG.trace("Select query: {}", query);

        SelectObjectContentRequest request = SelectObjectContentRequest.builder()
                .bucket(name.getHost())
                .key(StringUtils.removeStart(name.getPath(), "/"))
                .expression(query)
                .expressionType(ExpressionType.SQL)
                .inputSerialization(inputSerialization)
                .outputSerialization(getOutputSerialization(context))
                .build();

        LOG.debug("With bucket name '{}'", request.bucket());
        LOG.debug("With key '{}'", request.key());
        LOG.debug("With expression query '{}'", query);

        return request;
    }

    /**
     * Returns a {@link OutputSerialization}
     * object with parsed values from the request context.
     *
     * @param context the request context
     * @return a {@link OutputSerialization} object
     */
    private OutputSerialization getOutputSerialization(RequestContext context) {

        GreenplumCSV csv = context.getGreenplumCSV();

        CSVOutput csvOutput = CSVOutput.builder()
                .fieldDelimiter(asString(csv.getDelimiter()))
                .quoteCharacter(String.valueOf(csv.getQuote()))
                .quoteEscapeCharacter(asString(csv.getEscape()))
                .recordDelimiter(csv.getNewline())
                .build();

        return OutputSerialization.builder().csv(csvOutput).build();
    }

    /**
     * Returns a {@link InputSerialization}
     * object with parsed values from the request context.
     *
     * @param context the request context
     * @return a {@link InputSerialization} object
     */
    InputSerialization getInputSerialization(RequestContext context) {
        InputSerialization.Builder inputSerialization = InputSerialization.builder();

        // We need to infer the format name from the profile (i.e. s3:parquet
        // would return parquet for the format)
        String format = context.inferFormatName();
        String compressionType = context.getOption(COMPRESSION_TYPE);

        LOG.debug("With format {}", format);
        if (StringUtils.equalsIgnoreCase(format, "parquet")) {
            inputSerialization.parquet(ParquetInput.builder().build());
        } else if (StringUtils.equalsIgnoreCase(format, "json")) {
            inputSerialization.json(getJSONInput(context));
        } else {
            inputSerialization.csv(getCSVInput(context));
        }

        LOG.debug("With compression type {}", compressionType);
        if (StringUtils.equalsIgnoreCase(compressionType, "gzip")) {
            inputSerialization.compressionType(CompressionType.GZIP);
        } else if (StringUtils.equalsIgnoreCase(compressionType, "bzip2")) {
            inputSerialization.compressionType(CompressionType.BZIP2);
        } else {
            inputSerialization.compressionType(CompressionType.NONE);
        }

        return inputSerialization.build();
    }

    /**
     * Returns a {@link JSONInput}
     * object with parsed values from the request context.
     *
     * @param context the request context
     * @return a {@link JSONInput}
     */
    JSONInput getJSONInput(RequestContext context) {
        JSONInput.Builder jsonInput = JSONInput.builder();

        String jsonType = context.getOption(JSON_TYPE);

        if (StringUtils.isNoneBlank(jsonType)) {
            jsonInput.type(jsonType);
        }
        return jsonInput.build();
    }

    /**
     * Returns a {@link CSVInput}
     * object with parsed values from the request context.
     *
     * @param context the request context
     * @return a {@link CSVInput}
     */
    CSVInput getCSVInput(RequestContext context) {
        CSVInput.Builder csvInput = CSVInput.builder();
        GreenplumCSV csv = context.getGreenplumCSV();

        String fileHeaderInfo = context.getOption(FILE_HEADER_INFO);
        if (fileHeaderInfo != null) {
            LOG.debug("With CSV FileHeaderInfo '{}'", fileHeaderInfo);
            csvInput.fileHeaderInfo(fileHeaderInfo);
        }

        if (csv.getDelimiter() != null) {
            LOG.debug("With CSV field delimiter '{}'", csv.getDelimiter());
            csvInput.fieldDelimiter(asString(csv.getDelimiter()));
        }

        if (csv.getNewline() != null) {
            LOG.debug("With CSV NEWLINE '{}'", csv.getNewline());
            csvInput.recordDelimiter(csv.getNewline());
        }

        if (csv.getEscape() != null) {
            LOG.debug("With CSV quote escape character '{}'", csv.getEscape());
            csvInput.quoteEscapeCharacter(asString(csv.getEscape()));
        }

        LOG.debug("With CSV quote character '{}'", csv.getQuote());
        csvInput.quoteCharacter(String.valueOf(csv.getQuote()));

        return csvInput.build();
    }

    /**
     * Returns a new S3AsyncClient with credentials and settings
     * from the configuration file, the same way S3A does
     */
    private S3AsyncClient initS3Client() throws IOException {
        Configuration conf = propagateBucketOptions(configuration, name.getHost());
        // Always turn off the request timeout: it limits the whole call,
        // so it would interrupt the event stream of a long select.
        conf.setTimeDuration(REQUEST_TIMEOUT, 0, TimeUnit.MILLISECONDS);
        AWSCredentialProviderList credentials = createAWSCredentialProviderList(name, conf);
        try {
            S3ClientFactory.S3ClientCreationParameters parameters = new S3ClientFactory.S3ClientCreationParameters()
                    .withCredentialSet(credentials)
                    .withPathUri(name)
                    .withEndpoint(conf.getTrimmed(ENDPOINT, DEFAULT_ENDPOINT))
                    .withRegion(conf.getTrimmed(AWS_REGION))
                    .withPathStyleAccess(conf.getBoolean(PATH_STYLE_ACCESS, false))
                    .withRequesterPays(conf.getBoolean(ALLOW_REQUESTER_PAYS, DEFAULT_ALLOW_REQUESTER_PAYS))
                    .withFipsEnabled(conf.getBoolean(FIPS_ENDPOINT, ENDPOINT_FIPS_DEFAULT))
                    .withChecksumValidationEnabled(conf.getBoolean(CHECKSUM_VALIDATION, CHECKSUM_VALIDATION_DEFAULT))
                    .withChecksumCalculationEnabled(conf.getBoolean(CHECKSUM_GENERATION, DEFAULT_CHECKSUM_GENERATION))
                    .withMd5HeaderEnabled(conf.getBoolean(REQUEST_MD5_HEADER, DEFAULT_REQUEST_MD5_HEADER))
                    .withMultipartCopyEnabled(false);

            DefaultS3ClientFactory factory = new DefaultS3ClientFactory();
            factory.setConf(conf);
            return factory.createS3AsyncClient(name, parameters);
        } catch (IOException | RuntimeException e) {
            credentials.close();
            throw e;
        }
    }

    private void closeS3Client() {
        if (s3Client != null) {
            s3Client.close();
            s3Client = null;
            LOG.debug("S3AsyncClient closed");
        }
    }

    private String asString(Character character) {
        return character == null ? null : character.toString();
    }

    @Override
    public boolean openForWrite() {
        throw new UnsupportedOperationException(UNSUPPORTED_ERR_MESSAGE);
    }

    @Override
    public boolean writeNextObject(OneRow onerow) {
        throw new UnsupportedOperationException(UNSUPPORTED_ERR_MESSAGE);
    }

    @Override
    public void closeForWrite() {
        throw new UnsupportedOperationException(UNSUPPORTED_ERR_MESSAGE);
    }
}