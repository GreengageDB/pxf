package io.arenadata.pxf.plugins.iceberg.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

@Slf4j
@RequiredArgsConstructor
public class WriteSynchronizer {

    private final String transactionId;
    private final Semaphore semaphore = new Semaphore(Integer.MAX_VALUE);
    private final ConcurrentMap<Integer, Collection<FileToCommit>> files = new ConcurrentHashMap<>();

    public boolean isInUse() {
        return semaphore.availablePermits() < Integer.MAX_VALUE;
    }

    public boolean open(int segmentId) {
        log.info("Open transaction id {}, segment id {}", transactionId, segmentId);
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            log.error("Error during write opening", e);
            return false;
        }
        return true;
    }

    public void clean() {
        files.clear();
    }

    public Collection<FileToCommit> saveAndGetFullListIfCompleted(int segmentId, Collection<FileToCommit> inputFiles) {
        log.info("Complete transaction id {}, segment id {}", transactionId, segmentId);
        files.put(segmentId, inputFiles);
        try {
            return tryToCompleteEverything(segmentId);
        } finally {
            semaphore.release();
        }
    }

    private Collection<FileToCommit> tryToCompleteEverything(int segmentId) {
        //check if the current segment is last
        if(!semaphore.tryAcquire(Integer.MAX_VALUE - 1)) {
            return List.of();
        }
        log.info("Attempt to complete transaction id {}, segment id {} on node", transactionId, segmentId);
        try{
            return files.values().stream().flatMap(Collection::stream).toList();
        } finally {
            semaphore.release(Integer.MAX_VALUE - 1);
        }
    }

}
