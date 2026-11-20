package org.greenplum.pxf.plugins.s3;

import org.apache.hadoop.conf.Configuration;
import org.greenplum.pxf.api.OneRow;
import org.greenplum.pxf.api.model.RequestContext;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.CompressionType;
import software.amazon.awssdk.services.s3.model.InputSerialization;
import software.amazon.awssdk.services.s3.model.SelectObjectContentRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class S3SelectAccessorTest {

    @Test
    public void testGetInputSerializationDefaults() {
        RequestContext context = getDefaultRequestContext();

        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertNotNull(inputSerialization);
        assertNotNull(inputSerialization.csv());
        assertNull(inputSerialization.csv().allowQuotedRecordDelimiter());
        assertNull(inputSerialization.csv().comments());
        assertNull(inputSerialization.csv().fileHeaderInfoAsString());
        assertEquals(",", inputSerialization.csv().fieldDelimiter());
        assertEquals("\"", inputSerialization.csv().quoteCharacter());
        assertEquals("\"", inputSerialization.csv().quoteEscapeCharacter());
        assertEquals("\n", inputSerialization.csv().recordDelimiter());
        assertNull(inputSerialization.json());
        assertNull(inputSerialization.parquet());
        assertEquals(CompressionType.NONE, inputSerialization.compressionType());
    }

    @Test
    public void testCompressionTypeGZIP() {
        RequestContext context = getDefaultRequestContext();
        context.addOption("COMPRESSION_CODEC", "GZIP");

        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertEquals(CompressionType.GZIP, inputSerialization.compressionType());
    }

    @Test
    public void testCompressionTypeBZIP2() {
        RequestContext context = getDefaultRequestContext();
        context.addOption("COMPRESSION_CODEC", "BZIP2");

        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertEquals(CompressionType.BZIP2, inputSerialization.compressionType());
    }

    @Test
    public void testParquetInputSerialization() {
        RequestContext context = getRequestContext("s3:parquet");
        context.setFormat("parquet");

        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertNotNull(inputSerialization.parquet());
        assertNull(inputSerialization.json());
        assertNull(inputSerialization.csv());
    }

    @Test
    public void testJSONInputSerialization() {
        RequestContext context = getRequestContext("s3:json");
        context.setFormat("json");

        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertNotNull(inputSerialization.json());
        assertNull(inputSerialization.csv());
        assertNull(inputSerialization.parquet());
    }

    @Test
    public void testJSONInputSerializationWithDocumentJsonType() {
        RequestContext context = getRequestContext("s3:json");
        context.setFormat("json");
        context.addOption("JSON-TYPE", "document");

        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertNotNull(inputSerialization.json());
        assertNull(inputSerialization.csv());
        assertNull(inputSerialization.parquet());
        assertEquals("document", inputSerialization.json().typeAsString());
    }

    @Test
    public void testJSONInputSerializationWithLinesJsonType() {
        RequestContext context = getRequestContext("s3:json");
        context.setFormat("json");
        context.addOption("JSON-TYPE", "lines");

        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertNotNull(inputSerialization.json());
        assertNull(inputSerialization.csv());
        assertNull(inputSerialization.parquet());
        assertEquals("lines", inputSerialization.json().typeAsString());
    }

    @Test
    public void testPipeDelimiter() {
        RequestContext context = getDefaultRequestContext();
        context.getGreenplumCSV().withDelimiter("|");

        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertEquals("|", inputSerialization.csv().fieldDelimiter());
    }

    @Test
    public void testFileHeaderInfoIsIgnore() {
        RequestContext context = getDefaultRequestContext();
        context.addOption("FILE_HEADER", "IGNORE");
        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertEquals("IGNORE", inputSerialization.csv().fileHeaderInfoAsString());
    }

    @Test
    public void testFileHeaderInfoIsUse() {
        RequestContext context = getDefaultRequestContext();
        context.addOption("FILE_HEADER", "USE");
        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertEquals("USE", inputSerialization.csv().fileHeaderInfoAsString());
    }

    @Test
    public void testFileHeaderInfoIsNone() {
        RequestContext context = getDefaultRequestContext();
        context.addOption("FILE_HEADER", "NONE");
        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertEquals("NONE", inputSerialization.csv().fileHeaderInfoAsString());
    }

    @Test
    public void testQuoteEscapeCharacter() {
        RequestContext context = getDefaultRequestContext();
        context.addOption("ESCAPE", "\"");
        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertEquals("\"", inputSerialization.csv().quoteEscapeCharacter());
    }

    @Test
    public void testRecordDelimiter() {
        RequestContext context = getDefaultRequestContext();
        context.addOption("NEWLINE", "\n");
        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertEquals("\n", inputSerialization.csv().recordDelimiter());
    }

    @Test
    public void testQuoteCharacter() {
        RequestContext context = getDefaultRequestContext();
        context.addOption("QUOTE", "\"");
        InputSerialization inputSerialization =
                new S3SelectAccessor().getInputSerialization(context);
        assertEquals("\"", inputSerialization.csv().quoteCharacter());
    }

    @Test
    public void testCorrectlyParsesDataSource() {
        RequestContext context = getDefaultRequestContext();
        context.setConfig("default");
        context.setUser("test-user");
        context.setDataSource("s3a://my-bucket/my/s3/path/");
        context.setConfiguration(new Configuration());

        S3SelectAccessor accessor = new S3SelectAccessor();
        accessor.setRequestContext(context);
        accessor.afterPropertiesSet();
        SelectObjectContentRequest request = accessor.generateBaseCSVRequest(context);
        assertEquals("my-bucket", request.bucket());
        assertEquals("my/s3/path/", request.key());
    }

    @Test
    public void testCorrectlyParsesDataSourceWithNoKey() {
        RequestContext context = getDefaultRequestContext();
        context.setConfig("default");
        context.setUser("test-user");
        context.setDataSource("s3a://my-bucket");
        context.setConfiguration(new Configuration());

        S3SelectAccessor accessor = new S3SelectAccessor();
        accessor.setRequestContext(context);
        accessor.afterPropertiesSet();
        SelectObjectContentRequest request = accessor.generateBaseCSVRequest(context);
        assertEquals("my-bucket", request.bucket());
        assertEquals("", request.key());
    }

    @Test
    public void testFailsToParseNullDataSource() {
        RequestContext context = new RequestContext();

        assertThrows(NullPointerException.class,
                () -> new S3SelectAccessor().generateBaseCSVRequest(context));
    }

    @Test
    public void testFailsOnOpenForWrite() {
        Exception e = assertThrows(UnsupportedOperationException.class,
                () -> new S3SelectAccessor().openForWrite());
        assertEquals("S3 Select accessor does not support write operation.", e.getMessage());
    }

    @Test
    public void testFailsOnWriteNextObject() {
        Exception e = assertThrows(UnsupportedOperationException.class,
                () -> new S3SelectAccessor().writeNextObject(new OneRow()));
        assertEquals("S3 Select accessor does not support write operation.", e.getMessage());
    }

    @Test
    public void testFailsOnCloseForWrite() {
        Exception e = assertThrows(UnsupportedOperationException.class,
                () -> new S3SelectAccessor().closeForWrite());
        assertEquals("S3 Select accessor does not support write operation.", e.getMessage());
    }

    private RequestContext getDefaultRequestContext() {
        return getRequestContext("s3:csv");
    }

    private RequestContext getRequestContext(String s) {
        RequestContext context = new RequestContext();
        context.setProfile(s);
        return context;
    }
}
