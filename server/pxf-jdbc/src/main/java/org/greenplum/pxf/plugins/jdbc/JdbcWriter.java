package org.greenplum.pxf.plugins.jdbc;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import lombok.extern.slf4j.Slf4j;
import org.greenplum.pxf.api.OneRow;
import org.greenplum.pxf.api.error.PxfRuntimeException;
import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;
import org.greenplum.pxf.plugins.jdbc.writercallable.TimeoutFixedThreadPoolExecutor;
import org.greenplum.pxf.plugins.jdbc.writercallable.WriterCallable;
import org.greenplum.pxf.plugins.jdbc.writercallable.WriterCallableFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
public class JdbcWriter {
    public static final int TERMINATION_TIMEOUT = 5;
    private final ConcurrentLinkedQueue<Future<SQLException>> poolTasks;
    private final WriterCallableFactory writerCallableFactory;
    private final AtomicReference<Exception> firstException;
    private final ExecutorService writerExecutor;
    private final int terminationTimeoutSeconds;
    private final Semaphore semaphore;
    private WriterCallable writerCallable = null;
    private boolean isCanceled;

    JdbcWriter(JdbcBasePlugin plugin,
               int batchSize,
               int batchTimeout,
               String query,
               int poolSize,
               int terminationTimeoutSeconds,
               DatabaseDialect dialect
    ) {
        log.debug("Creating JdbcWriter with batchSize={}, batchTimeout={}, query={}, poolSize={}, terminationTimeoutSeconds={}",
                batchSize, batchTimeout, query, poolSize, terminationTimeoutSeconds);
        this.terminationTimeoutSeconds = terminationTimeoutSeconds == 0 ? TERMINATION_TIMEOUT : terminationTimeoutSeconds;
        // Process poolSize
        if (poolSize < 1) {
            poolSize = Runtime.getRuntime().availableProcessors();
            log.info("The POOL_SIZE is set to the number of CPUs available ({})", poolSize);
        }

        if (batchTimeout == 0) {
            writerExecutor = Executors.newFixedThreadPool(poolSize);
            log.debug("Writer will use fixed thread pool with {} thread(s)", poolSize);
        } else {
            writerExecutor = new TimeoutFixedThreadPoolExecutor(poolSize, batchTimeout, TimeUnit.SECONDS);
            log.debug("Writer will use fixed thread pool with timeout {} seconds and {} thread(s)", batchTimeout, poolSize);
        }

        semaphore = new Semaphore(poolSize);
        poolTasks = new ConcurrentLinkedQueue<>();
        firstException = new AtomicReference<>();

        // Setup WriterCallableFactory
        writerCallableFactory = new WriterCallableFactory(plugin, query, batchSize, semaphore::release, dialect);
        log.debug("JdbcWriter is created with batchSize={}, batchTimeout={}, query={}, poolSize={}, terminationTimeoutSeconds={}",
                batchSize, batchTimeout, query, poolSize, terminationTimeoutSeconds);
    }

    public static JdbcWriter fromProps(JdbcWriterProperties props) {
        return new JdbcWriter(
                props.getPlugin(),
                props.getBatchSize(),
                props.getBatchTimeout(),
                props.getQuery(),
                props.getPoolSize(),
                props.getTerminationTimeoutSeconds(),
                props.getDatabaseDialect()
        );
    }

    public boolean write(OneRow row) throws Exception {
        checkCanceled();
        if (writerCallableFactory == null) {
            throwException(new IllegalStateException("The JDBC connection was not properly initialized: writerCallableFactory is null"));
        } else if (writerCallable == null) {
            writerCallable = writerCallableFactory.get();
            log.trace("Created new writer {}", writerCallable);
        }

        writerCallable.supply(row);
        if (writerCallable.isCallRequired()) {
            if (log.isTraceEnabled()) {
                log.trace("Accessor try to acquire semaphore to submit the task for writer {}", writerCallable);
                log.trace("Current thread pool active task: {}; Queue used: {}; Semaphore remains: {}",
                        ((ThreadPoolExecutor) writerExecutor).getActiveCount(),
                        Integer.MAX_VALUE - ((ThreadPoolExecutor) writerExecutor).getQueue().remainingCapacity(),
                        semaphore.availablePermits());
            }
            // Semaphore#release runs as onComplete.run() in a 'finally' statement of WriterCallable#call
            semaphore.acquire();
            checkCanceled();
            Future<SQLException> future = writerExecutor.submit(writerCallable);
            poolTasks.add(future);
            log.trace("Accessor submitted the task for writer {} with future result {}", writerCallable, future);
            writerCallable = writerCallableFactory.get();
            log.trace("Created new writer {}", writerCallable);
            // Check results for tasks that has already done
            checkWriteNextObjectResults();
        }
        return true;
    }

    public void close() throws Exception {
        log.debug("Accessor starts closeForWrite()");
        if (writerCallable == null) {
            return;
        }

        try {
            if (firstException.get() == null) {
                if (writerCallable != null) {
                    // Send data that is left
                    checkCanceled();
                    Future<SQLException> future = writerExecutor.submit(writerCallable);
                    poolTasks.add(future);
                    log.trace("Accessor submitted the last task for writer {} with future result {}", writerCallable, future);
                    checkCloseForWriteResults();
                }
            } else {
                throw firstException.get();
            }
        } finally {
            shutdownExecutorService(writerExecutor);
        }
    }

    public void cancelWrite() {
        isCanceled = true;
        try {
            log.debug("Number of tasks to be canceled: {}", poolTasks.size());
            poolTasks.forEach(task -> task.cancel(true));
            log.debug("Shutdown writer executor service");
            shutdownExecutorService(writerExecutor);
        } finally {
            // We need to release semaphore because some drivers will not react on interrupt
            semaphore.release();
        }
    }

    private void checkCanceled() throws Exception {
        if (isCanceled) {
            String message = "The write operation was canceled";
            log.warn(message);
            throwException(new PxfRuntimeException(message));
        }
    }

    private void checkWriteNextObjectResults() throws Exception {
        for (Future<SQLException> future : poolTasks) {
            Exception exception;
            if (future.isDone()) {
                try {
                    exception = future.get();
                    log.trace("Accessor completed intermediate task with the future {}", future);
                } catch (Exception ex) {
                    exception = ex;
                }
                poolTasks.remove(future);
                if (exception != null) {
                    throwException(exception);
                }
            }
        }
    }

    private void checkCloseForWriteResults() throws Exception {
        for (Future<SQLException> future : poolTasks) {
            Exception exception;
            try {
                exception = future.get();
                log.trace("Accessor completed one of the last task with the future {}", future);
            } catch (Exception ex) {
                exception = ex;
            }
            if (exception != null) {
                throwException(exception);
            }
        }
        poolTasks.clear();
    }

    private void throwException(Exception exception) throws Exception {
        firstException.compareAndSet(null, exception);
        String msg = Optional.ofNullable(exception.getMessage())
                .orElse(exception.getClass().getName());
        log.error("Accessor completed the task with exception: {}", msg);
        throw exception;
    }

    private void shutdownExecutorService(ExecutorService executorService) {
        log.debug("Accessor starts shutdown executor service for write");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(terminationTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in the specified time.");
                List<Runnable> droppedTasks = executorService.shutdownNow();
                log.warn("Dropped {} tasks", droppedTasks.size());
            }
        } catch (InterruptedException e) {
            log.warn("Thread received interrupted signal");
            Thread.currentThread().interrupt();
        }
        log.debug("Accessor completed to shutdown executor service");
    }
}
