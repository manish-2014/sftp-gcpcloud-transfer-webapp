#!/bin/bash

# --- Configuration ---
IMAGE_BASE_NAME="us-central1-docker.pkg.dev/<$projectid>/cloud-transfer-repo/cloud-transfer-webapp"
# --- End Configuration ---

# Generate a timestamp tag (e.g., 20250425-173055)
VERSION_TAG=$(date +'%Y%m%d-%H%M%S')

# Construct the full image name with the tag
FULL_IMAGE_NAME="${IMAGE_BASE_NAME}:${VERSION_TAG}"

echo "Building image: ${FULL_IMAGE_NAME}"
docker build -t "${FULL_IMAGE_NAME}" .

# Check if build was successful
if [ $? -ne 0 ]; then
  echo "Error: Docker build failed."
  exit 1
fi

echo "Pushing image: ${FULL_IMAGE_NAME}"
docker push "${FULL_IMAGE_NAME}"

# Check if push was successful
if [ $? -ne 0 ]; then
  echo "Error: Docker push failed."
  exit 1
fi

echo "Successfully built and pushed: ${FULL_IMAGE_NAME}"
echo "Use this image URI in your Terraform apply command."

exit 0

