package io.arenadata.pxf.plugins.iceberg.writer;

import lombok.RequiredArgsConstructor;
import org.apache.iceberg.data.Record;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Consumer;

@RequiredArgsConstructor
public class IcebergWriterWithSynchronization implements IcebergWriter {

    private final int segmentId;
    private final IcebergWriter delegate;
    private final WriteSynchronizer synchronizer;
    private final Consumer<Boolean> synchronizerCleaner;

    @Override
    public void write(Record record) throws IOException {
        delegate.write(record);
    }

    @Override
    public Collection<FileToCommit> completeAndGetFilesToCommit() throws IOException {
        try{
            var filesToCommit = synchronizer.saveAndGetFullListIfCompleted(segmentId, delegate.completeAndGetFilesToCommit());
            synchronizerCleaner.accept(false);
            return filesToCommit;
        } catch(Exception e) {
            synchronizerCleaner.accept(true);
            throw e;
        }
    }

}
