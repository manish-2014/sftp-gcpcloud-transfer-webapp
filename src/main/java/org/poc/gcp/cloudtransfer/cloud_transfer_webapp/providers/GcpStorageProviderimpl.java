package org.poc.gcp.cloudtransfer.cloud_transfer_webapp.providers;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.poc.gcp.cloudtransfer.config.GcpConfig; // Assuming this is the correct package
import org.poc.gcp.cloudtransfer.providers.GcpStorageProvider; // Assuming this is the correct package
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A GcpStorageProvider that loads the Service Account key from a String (e.g., fetched from Vault).
 */
public class GcpStorageProviderimpl implements GcpStorageProvider {

    private static final Logger logger = LoggerFactory.getLogger(GcpStorageProviderimpl.class);
    private final String serviceAccountKeyJson;
    private final String credentialSourceName; // For logging (e.g., "Vault Secret 'sa_gcp'")

    /**
     * Creates a provider that uses the provided Service Account JSON string.
     * @param serviceAccountKeyJson The JSON content of the Service Account key.
     * @param credentialSourceName A descriptive name for the source of the credential (for logging).
     */
    public GcpStorageProviderimpl(String serviceAccountKeyJson, String credentialSourceName) {
        if (serviceAccountKeyJson == null || serviceAccountKeyJson.isBlank()) {
            throw new IllegalArgumentException("Service Account key JSON content cannot be null or empty.");
        }
        this.serviceAccountKeyJson = serviceAccountKeyJson;
        this.credentialSourceName = (credentialSourceName != null) ? credentialSourceName : "String Input";
        logger.info("Initialized GcpStorageProviderimpl using credential source: {}", this.credentialSourceName);
    }

    @Override
    public Storage getStorageClient(GcpConfig config) throws IOException {
        logger.info("Attempting to create GCP Storage client using credentials from {}", credentialSourceName);

        // Create an InputStream from the JSON string
        try (InputStream is = new ByteArrayInputStream(serviceAccountKeyJson.getBytes(StandardCharsets.UTF_8))) {

            GoogleCredentials credentials = ServiceAccountCredentials.fromStream(is);

            // Simpler way if Project ID is in the key file (common)
            StorageOptions storageOptions = StorageOptions.newBuilder().setCredentials(credentials).build();
            Storage storage = storageOptions.getService();

            if (storage == null) {
                throw new IOException("Failed to initialize GCP Storage service after loading credentials from " + credentialSourceName);
            }

            logger.info("Successfully obtained GCP Storage client using {}. Project ID: {}",
                    credentialSourceName, storage.getOptions().getProjectId());
            return storage;

        } catch (IOException e) {
            logger.error("Failed to read or process GCP Service Account key from {}: {}", credentialSourceName, e.getMessage(), e);
            throw new IOException("Failed to initialize GCP Storage client from " + credentialSourceName + ": " + e.getMessage(), e);
        } catch (Exception e) {
            // Catch potential runtime exceptions
            logger.error("An unexpected error occurred while initializing GCP Storage client from {}: {}", credentialSourceName, e.getMessage(), e);
            throw new IOException("Unexpected error initializing GCP Storage client from " + credentialSourceName + ": " + e.getMessage(), e);
        }
    }
}
