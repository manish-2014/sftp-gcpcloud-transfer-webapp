package org.poc.gcp.cloudtransfer.cloud_transfer_webapp.providers;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier; // INSECURE
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.poc.gcp.cloudtransfer.config.SftpConfig; // Assuming correct package
import org.poc.gcp.cloudtransfer.providers.SftpClientProvider; // Assuming correct package
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * An SftpClientProvider that loads the private key from a String (e.g., fetched from Vault).
 */
public class SftpClientProviderImpl implements SftpClientProvider {

    private static final Logger logger = LoggerFactory.getLogger(SftpClientProviderImpl.class);
    private final String privateKeyMaterial;
    private final String credentialSourceName; // For logging

    /**
     * Creates a provider that uses the provided private key string.
     * @param privateKeyMaterial The content of the SFTP private key.
     * @param credentialSourceName A descriptive name for the source of the credential (for logging).
     */
    public SftpClientProviderImpl(String privateKeyMaterial, String credentialSourceName) {
        if (privateKeyMaterial == null || privateKeyMaterial.isBlank()) {
            throw new IllegalArgumentException("SFTP private key material cannot be null or empty.");
        }
        this.privateKeyMaterial = privateKeyMaterial;
        this.credentialSourceName = (credentialSourceName != null) ? credentialSourceName : "String Input";
        logger.info("Initialized SftpClientProviderImpl using credential source: {}", this.credentialSourceName);
    }

    @Override
    public SSHClient getSshClient(SftpConfig config) throws IOException {
        logger.info("Attempting to create SSH client for user '{}' at {}:{} using credentials from {}",
                config.getSshUser(), config.getSftpServer(), config.getSftpPort(), credentialSourceName);

        SSHClient client = new SSHClient();

        // !!! SECURITY WARNING !!! Use a proper verifier in production.
        logger.warn("Using PromiscuousVerifier for host key verification. This is INSECURE and should NOT be used in production environments.");
        client.addHostKeyVerifier(new PromiscuousVerifier());

        client.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(30));
        client.setTimeout((int) TimeUnit.SECONDS.toMillis(60));

        KeyProvider keyProvider;
        try {
            logger.debug("Loading private key from string provided by {}...", credentialSourceName);
            // Load the key from the String. Assumes no passphrase.
            keyProvider = client.loadKeys(privateKeyMaterial, null, null);
            logger.debug("Private key loaded successfully into KeyProvider.");
        } catch (IOException e) {
            logger.error("Failed to load SFTP private key material from {}. Ensure format is correct. Error: {}", credentialSourceName, e.getMessage(), e);
            throw new IOException("Failed to load SFTP private key material from " + credentialSourceName + ": " + e.getMessage(), e);
        }

        try {
            logger.debug("Connecting to SFTP server {}:{}", config.getSftpServer(), config.getSftpPort());
            client.connect(config.getSftpServer(), config.getSftpPort());

            String username = config.getSshUser();
            logger.debug("Authenticating SFTP user '{}' with public key from {}", username, credentialSourceName);

            client.authPublickey(config.getSshUser(), keyProvider);

            if (!client.isAuthenticated()) {
                throw new IOException("SFTP authentication failed for user '" + username + "' (post-authentication check).");
            }
            logger.info("SFTP connection established and authenticated successfully for user '{}' using {}.", username, credentialSourceName);
            return client;

        } catch (UserAuthException e) {
            logger.error("SFTP Authentication failed for user '{}' on {}:{}. Source: {}. Message: {}",
                    config.getSshUser(), config.getSftpServer(), config.getSftpPort(), credentialSourceName, e.getMessage(), e);
            try { client.disconnect(); } catch (IOException ignored) {}
            throw new IOException("SFTP Authentication failed: " + e.getMessage(), e);
        } catch (TransportException e) {
            logger.error("SFTP Transport layer error during connection/authentication for user '{}' on {}:{}. Source: {}. Message: {}",
                    config.getSshUser(), config.getSftpServer(), config.getSftpPort(), credentialSourceName, e.getMessage(), e);
            try { client.disconnect(); } catch (IOException ignored) {}
            throw new IOException("SFTP Transport error: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to connect or authenticate to SFTP server {}:{} for user '{}' using {}: {}",
                    config.getSftpServer(), config.getSftpPort(), config.getSshUser(), credentialSourceName, e.getMessage(), e);
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
            } catch (IOException disconnectEx) {
                logger.warn("Error disconnecting SFTP client after connection failure: {}", disconnectEx.getMessage(), disconnectEx);
                e.addSuppressed(disconnectEx);
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw new IOException("SFTP connection/authentication failed using " + credentialSourceName + ": " + e.getMessage(), e);
            }
        }
    }
}
