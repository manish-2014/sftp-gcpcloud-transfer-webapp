package org.poc.gcp.cloudtransfer.cloud_transfer_webapp.service;

import org.poc.gcp.cloudtransfer.CloudTransfer;
import org.poc.gcp.cloudtransfer.config.TransferConfig;
import org.poc.gcp.cloudtransfer.providers.GcpStorageProvider;
import org.poc.gcp.cloudtransfer.providers.SftpClientProvider;
import org.poc.gcp.cloudtransfer.cloud_transfer_webapp.controller.FileTransferController.JobStatus; // Use enum from controller
import org.poc.gcp.cloudtransfer.cloud_transfer_webapp.providers.GcpStorageProviderimpl;
import org.poc.gcp.cloudtransfer.cloud_transfer_webapp.providers.SftpClientProviderImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AsyncTransferService {

    private static final Logger log = LoggerFactory.getLogger(AsyncTransferService.class);

    /**
     * Executes the file transfer asynchronously.
     * This method runs in a separate thread managed by Spring's task executor.
     *
     * @param jobId                 The unique ID for this transfer job.
     * @param transferConfig        The configuration for the transfer.
     * @param gcpKeyContent         The GCP service account key JSON content.
     * @param sftpKeyContent        The SFTP private key content.
     * @param jobStatuses           Reference to the map tracking job statuses (from controller).
     * @param activeJobs            Reference to the atomic counter for active jobs (from controller).
     * @param gcpEnvVarName         Name of the GCP env var (for logging).
     * @param sftpEnvVarName        Name of the SFTP env var (for logging).
     */
    @Async // Make this method execute asynchronously
    // You can specify the executor bean name: @Async("taskExecutor")
    public void performTransfer(String jobId,
                                TransferConfig transferConfig,
                                String gcpKeyContent,
                                String sftpKeyContent,
                                ConcurrentHashMap<String, JobStatus> jobStatuses,
                                AtomicInteger activeJobs,
                                String gcpEnvVarName,
                                String sftpEnvVarName) {

        log.info("[{}] Async task started. Initializing providers...", jobId);
        jobStatuses.put(jobId, JobStatus.RUNNING); // Update status: Job is now actively running

        GcpStorageProvider gcpProvider = null;
        SftpClientProvider sftpProvider = null;

        try {
            // 1. Initialize Providers
            log.debug("[{}] Initializing GCP provider using secret from env var '{}'", jobId, gcpEnvVarName);
            gcpProvider = new GcpStorageProviderimpl(gcpKeyContent, "Secret from Env Var '" + gcpEnvVarName + "'");

            log.debug("[{}] Initializing SFTP provider using secret from env var '{}'", jobId, sftpEnvVarName);
            sftpProvider = new SftpClientProviderImpl(sftpKeyContent, "Secret from Env Var '" + sftpEnvVarName + "'");
            log.info("[{}] Providers initialized successfully.", jobId);

            // 2. Execute Transfer
            log.info("[{}] Starting core transfer execution...", jobId);
            CloudTransfer transferExecutor = new CloudTransfer();
            transferExecutor.execute(transferConfig, sftpProvider, gcpProvider); // The potentially long-running operation

            // 3. Success: Update status
            log.info("[{}] Async transfer operation completed successfully.", jobId);
            jobStatuses.put(jobId, JobStatus.COMPLETED);

        } catch (Exception e) {
            // 4. Failure: Log error and update status
            log.error("[{}] Async transfer execution failed: {}", jobId, e.getMessage(), e);
            jobStatuses.put(jobId, JobStatus.FAILED);

        } finally {
            // 5. CRUCIAL: Decrement active job count regardless of outcome
            int jobsLeft = activeJobs.decrementAndGet();
            log.info("[{}] Async task finished (Status: {}). Active jobs remaining: {}", jobId, jobStatuses.get(jobId), jobsLeft);
        }
    }
}