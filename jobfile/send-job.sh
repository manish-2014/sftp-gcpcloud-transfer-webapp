#!/usr/bin/env bash

# --- Configuration ---
# Set the URL for the REST endpoint
ENDPOINT_URL="http://localhost:8080/process-job"

# --- Argument Handling ---
# Check if exactly one argument (the config file path) was provided
if [[ $# -ne 1 ]]; then
  echo "[ERROR] Usage: $0 <path_to_config.json>" >&2
  echo "Example: $0 /path/to/your/config.json" >&2
  exit 1 # Exit with an error code
fi

# Assign the first command-line argument to the CONFIG_FILE variable
CONFIG_FILE="$1"

# --- Pre-flight Check ---
# Check if the configuration file exists before trying to send it
if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "[ERROR] Configuration file not found: $CONFIG_FILE" >&2
  exit 1 # Exit with an error code
fi

# --- Execute curl command ---
echo "[INFO] Sending configuration from '$CONFIG_FILE' to '$ENDPOINT_URL'..."

# Execute the curl command, sending the file content as the request body
# Use "$CONFIG_FILE" in quotes to handle paths with spaces
curl --silent --show-error \
     -X POST \
     -H "Content-Type: application/json" \
     -d "@$CONFIG_FILE" \
     "$ENDPOINT_URL"

# Capture the exit code of the curl command
CURL_EXIT_CODE=$?

# Add a newline after curl output for better readability
echo

# --- Check curl result ---
if [[ $CURL_EXIT_CODE -eq 0 ]]; then
  # Note: A successful curl command (exit code 0) doesn't guarantee the server processed the request successfully.
  # The server might have returned an HTTP error (like 400 or 500), which curl might still consider a successful transfer.
  # You should check the output printed by curl (the server's response body) to confirm success.
  echo "[INFO] curl command completed. Check server response above for job status."
else
  # This indicates a curl-level error (e.g., connection refused, invalid URL, file not found by curl).
  echo "[ERROR] curl command failed with exit code $CURL_EXIT_CODE." >&2
fi

# Exit the script with the curl command's exit code
exit $CURL_EXIT_CODE
