#!/usr/bin/env bash

# Exit on error, treat unset variables as error, propagate pipeline errors
set -euo pipefail

source /home/manish/projects/devops/environment-center/file-tranfer-web-secrets/load-web-secrets.sh
# Check if the required environment variables are set
if [[ -z "${sa_gcp_key:-}" || -z "${sftp_secret_key:-}"  ]]; then
    echo "Error: Required environment variables are not set."
    echo "Please ensure sa_gcp_key,and sftp_secret_keys are set."
    exit 1
fi

echo "Starting the application..."
java -jar target/cloud-transfer-webapp-0.0.1-SNAPSHOT.jar
