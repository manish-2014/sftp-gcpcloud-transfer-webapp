package org.poc.gcp.cloudtransfer.cloud_transfer_webapp.controller;

import org.poc.gcp.cloudtransfer.config.TransferConfig;
import org.poc.gcp.cloudtransfer.cloud_transfer_webapp.service.AsyncTransferService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
// Removed StringUtils as explicit check is less needed with @Value required properties
// import org.springframework.util.StringUtils;

// Removed PostConstruct
// import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


@RestController
public class FileTransferController {

    private static final Logger log = LoggerFactory.getLogger(FileTransferController.class);

    // Use property names matching env vars (Spring Boot handles mapping)
    // Or use specific property names and map env vars to them if preferred.
    private static final String GCP_SECRET_PROPERTY = "sa_gcp_key";
    private static final String SFTP_SECRET_PROPERTY = "sftp_secret_key";
    private static final int MAX_CONCURRENT_JOBS = 2;

    private final AtomicInteger activeJobs = new AtomicInteger(0);
    private final ConcurrentHashMap<String, JobStatus> jobStatuses = new ConcurrentHashMap<>();

    public enum JobStatus {
        ACCEPTED, PENDING, RUNNING, COMPLETED, FAILED, REJECTED
    }

    @Value("${controller.app.version:2.2-value-inject}") // Updated version
    private String controllerVersion;

    // Inject secrets using @Value referencing the environment variable names
    // Spring Boot automatically checks environment variables for properties.
    // If these are absolutely required for the app to function,
    // not providing them (or a default) will cause startup failure,
    // except during tests where we override them.
    @Value("${" + GCP_SECRET_PROPERTY + "}")
    private String gcpServiceAccountKeyContent;

    @Value("${" + SFTP_SECRET_PROPERTY + "}")
    private String sftpPrivateKeyContent;

    private final Validator validator;
    private final AsyncTransferService asyncTransferService;

    @Autowired
    public FileTransferController(Validator validator, AsyncTransferService asyncTransferService) {
        this.validator = validator;
        this.asyncTransferService = asyncTransferService;
        // Secrets are injected by Spring *after* construction but before the bean is fully ready.
        // We can log them later if needed, e.g., in an init method or first use.
        log.info("FileTransferController instantiated. Version: {}", controllerVersion);
    }


    @GetMapping("/healthcheck")
    public ResponseEntity<Map<String, String>> healthCheck() {
        log.debug("Health check requested.");
        return ResponseEntity.ok(Collections.singletonMap("status", "UP"));
    }

    @GetMapping("/jobstatus/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        log.debug("Status requested for job ID: {}", jobId);
        Map<String, Object> responseBody = new HashMap<>();
        JobStatus status = jobStatuses.get(jobId);

        if (status == null) {
            log.warn("Status requested for unknown job ID: {}", jobId);
            responseBody.put("message", "Job ID not found.");
            responseBody.put("jobId", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(responseBody);
        } else {
            responseBody.put("jobId", jobId);
            responseBody.put("status", status.name());
            log.info("Returning status '{}' for job ID: {}", status.name(), jobId);
            return ResponseEntity.ok(responseBody);
        }
    }

    @GetMapping("/jobs/active")
    public ResponseEntity<Map<String, Object>> getActiveJobs() {

        log.debug("Request received for active job IDs.");
        List<String> activeJobIds = jobStatuses.entrySet().stream()
                .filter(entry -> entry.getValue() == JobStatus.ACCEPTED || entry.getValue() == JobStatus.RUNNING)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.info("Found {} active jobs.", activeJobIds.size());
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("activeJobCount", activeJobIds.size());
        responseBody.put("activeJobIds", activeJobIds);
        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/process-job")
    public ResponseEntity<Map<String, Object>> processTransferJob(@RequestBody TransferConfig transferConfig) {

        Map<String, Object> responseBody = new HashMap<>();
        String jobId = java.util.UUID.randomUUID().toString();
        responseBody.put("jobId", jobId);

        log.info("[{}] Received transfer request (Controller Version: {}).", jobId, controllerVersion);

        // Concurrency Check
        if (activeJobs.get() >= MAX_CONCURRENT_JOBS) {
            log.warn("[{}] Rejected (Sync Check): Max concurrent limit ({}) reached. Active: {}",
                    jobId, MAX_CONCURRENT_JOBS, activeJobs.get());
            jobStatuses.put(jobId, JobStatus.REJECTED);
            responseBody.put("message", "Server busy: Maximum concurrent transfer jobs reached.");
            responseBody.put("details", "Limit: " + MAX_CONCURRENT_JOBS + ", Active: " + activeJobs.get());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(responseBody);
        }

        // Validation
        Set<ConstraintViolation<TransferConfig>> violations = validator.validate(transferConfig);
        if (!violations.isEmpty()) {
            String errorDetails = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));
            log.warn("[{}] Invalid TransferConfig received: {}", jobId, errorDetails);
            jobStatuses.put(jobId, JobStatus.REJECTED);
            responseBody.put("message", "Invalid configuration provided for transfer.");
            responseBody.put("errors", errorDetails);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseBody);
        }
        log.info("[{}] TransferConfig validated successfully.", jobId);

        // Try Increment
        int currentJobCount = activeJobs.incrementAndGet();
        if (currentJobCount > MAX_CONCURRENT_JOBS) {
            activeJobs.decrementAndGet(); // Decrement immediately
            log.warn("[{}] Rejected (Race Condition): Max concurrent limit ({}) reached after increment. Active: {}",
                    jobId, MAX_CONCURRENT_JOBS, activeJobs.get());
            jobStatuses.put(jobId, JobStatus.REJECTED);
            responseBody.put("message", "Server busy: Maximum concurrent transfer jobs reached (race condition).");
            responseBody.put("details", "Limit: " + MAX_CONCURRENT_JOBS);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(responseBody);
        }

        // Job Accepted - Queue
        log.info("[{}] Job accepted. Active jobs: {}. Queuing for async execution.", jobId, currentJobCount);
        jobStatuses.put(jobId, JobStatus.ACCEPTED);

        try {
            // Log that we are about to pass the secrets (optional)
            log.debug("[{}] Passing secrets to async service.", jobId);
            // Ensure secrets are not null/empty before passing, though @Value should prevent this if required
            if (gcpServiceAccountKeyContent == null || gcpServiceAccountKeyContent.isEmpty() ||
                    sftpPrivateKeyContent == null || sftpPrivateKeyContent.isEmpty()) {
                log.error("[{}] Secrets not available when trying to queue job!", jobId);
                // This case should ideally not happen if @Value injection worked/failed context loading
                throw new IllegalStateException("Secrets were not injected properly before job submission.");
            }

            // Call the async service method, passing the injected secret values
            asyncTransferService.performTransfer(
                    jobId,
                    transferConfig,
                    this.gcpServiceAccountKeyContent, // Pass injected value
                    this.sftpPrivateKeyContent,       // Pass injected value
                    this.jobStatuses,
                    this.activeJobs,
                    GCP_SECRET_PROPERTY,  // Pass property name for logging
                    SFTP_SECRET_PROPERTY // Pass property name for logging
            );

            // Return HTTP 202 Accepted
            responseBody.put("message", "Transfer job accepted and queued for processing.");
            responseBody.put("status", JobStatus.ACCEPTED.name());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(responseBody);

        } catch (Exception e) {
            // Catch submission errors or the IllegalStateException from the check above
            log.error("[{}] Failed to submit job for asynchronous execution: {}", jobId, e.getMessage(), e);
            activeJobs.decrementAndGet(); // Decrement since submission failed
            jobStatuses.put(jobId, JobStatus.FAILED);
            responseBody.put("message", "Failed to queue transfer job for execution.");
            responseBody.put("details", e.getMessage());
            responseBody.put("status", JobStatus.FAILED.name());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseBody);
        }
    }
}