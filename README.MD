# Cloud Transfer Webapp

## Overview

This Spring Boot web application provides a REST API to manage and execute file/directory transfers between different storage locations:

* Local Filesystem
* SFTP Servers
* Google Cloud Storage (GCS) Buckets

It uses an asynchronous, throttled job execution model suitable for potentially long-running transfers.

## Features

* **Flexible Transfers:** Supports transferring single files or entire directory structures.
* **Multiple Location Types:** Transfer between Local, SFTP, and GCS.
* **JSON Job Configuration:** Define transfer sources and destinations using simple JSON files.
* **Asynchronous Processing:** Transfer jobs are executed asynchronously, allowing the API to respond quickly.
* **Job Throttling:** Limits the number of concurrently running jobs to prevent resource exhaustion (configurable limit, currently 2).
* **Job Status Tracking:** Provides endpoints to check the status of submitted jobs.
* **Externalized Credentials:** Reads sensitive SFTP private keys and GCP service account keys from environment variables for security.
* **Docker Support:** Includes scripts to build and push a Docker image.

## Project Structure

* **`bns.cbe.cloudtransfer.cloud_transfer_webapp`**: Main package for the Spring Boot application.
    * **`CloudTransferWebappApplication`**: Main application class, enables `@EnableAsync`.
    * **`controller.FileTransferController`**: Handles incoming REST API requests, validates input, manages job queue/throttling, and interacts with the async service.
    * **`service.AsyncTransferService`**: Executes the core transfer logic asynchronously using `@Async`.
    * **`providers`**: Contains implementations (`SftpClientProviderImpl`, `GcpStorageProviderimpl`) that load credentials from strings (environment variables).
* **`bns.cbe.cloudtransfer`**: Core library handling the transfer logic.
    * **`CloudTransfer`**: Main class to execute a transfer based on config.
    * **`config`**: Defines the configuration objects (e.g., `TransferConfig`, `LocalConfig`, `SftpConfig`, `GcpConfig`) used for JSON parsing and validation.
    * **`core`**: Interfaces (`DataSource`, `DataDestination`) and core classes (`ItemInfo`, `TransferService`) defining the transfer abstraction.
    * **`local`, `sftp`, `gcp`**: Implementations of `DataSource` and `DataDestination` for each location type.
    * **`providers`**: Interfaces (`SftpClientProvider`, `GcpStorageProvider`) for abstracting authentication client creation.
    * **`utils`**: Helper classes (`GcpUtils`, `SftpUtils`).
* **`src/main/resources`**: Contains `application.yaml` for Spring Boot configuration.
* **`src/test`**: Contains unit/integration tests (if any).
* **`jobfile/`**: Contains example JSON configuration files for various transfer types and scripts to submit jobs.
* **`pom.xml`**: Maven project configuration, defining dependencies and build settings.
* **`run.sh`**: Script to run the application locally (requires environment variables to be set).
* **`docker-build-push.sh`**: Script to build and push the Docker image.
* **`Readme-docker.md`**: Instructions related to Docker usage.

## Configuration

### Job Definition (JSON)

Transfer jobs are defined in JSON format. The root object contains `source` and `destination` objects.

**Location Object Structure:**

Each `source` and `destination` object requires a `type` field:

* `"type": "LOCAL"`
* `"type": "SFTP"`
* `"type": "GCP_BUCKET"`

Based on the `type`, specific fields are required:

* **LOCAL:**
    * `path`: Absolute path to the directory (for directory transfers).
    * `file_path`: Absolute path to the file (for single file transfers).
    * *Note:* Exactly one of `path` or `file_path` must be provided. Destination must use `path`.
* **SFTP:**
    * `sftp_server`: Hostname or IP address of the SFTP server.
    * `sftp_port`: (Optional) Port number (default: 22).
    * `ssh_user`: Username for SFTP login.
    * `path`: Remote path for the directory (for directory transfers).
    * `file_path`: Remote path to the file (for single file transfers).
    * *Note:* Exactly one of `path` or `file_path` must be provided. Destination must use `path`. SSH key is provided via environment variable, not in the JSON.
* **GCP_BUCKET:**
    * `bucket`: Name of the GCS bucket.
    * `folder`: (Optional) Prefix/folder within the bucket (use "" or omit for root). Required for directory transfers.
    * `file_name`: (Optional) Specific object name relative to the folder/root. Providing this triggers single file mode.
    * *Note:* Service Account Key is provided via environment variable, not in the JSON.

See the `jobfile/` directory for examples.

### Authentication (Environment Variables)

Credentials are **not** placed in the JSON configuration files. The application expects the following environment variables to be set when it runs:

1.  **`sa_gcp_key`**: Should contain the **full JSON content** of the Google Cloud Service Account key file required for accessing GCS buckets.
2.  **`sftp_secret_key`**: Should contain the **full text content** of the SSH private key file required for authenticating with the SFTP server.

The `run.sh` script demonstrates sourcing these variables before starting the application. Ensure these variables are securely provided in your deployment environment (e.g., Cloud Run secrets, Kubernetes secrets).

## Usage

### Prerequisites

* Java 17 or later
* Maven 3.x
* Docker (optional, for containerization)
* Environment variables `sa_gcp_key` and `sftp_secret_key` set with appropriate credentials.

### Building

Use Maven to compile and package the application:

```bash
mvn clean package -Dmaven.test.skip=true

./docerk-build-push.sh

```

### Running Locally
./run.sh


## sample (json) job file to  transfer file from sftp (localhost) to GCP
```
{
    "source": {
        "type": "SFTP",
        "sftp_server": "localhost",
        "sftp_port": 2222,        
        "ssh_user": "foo",
      "file_path":  "/upload/web2/video2.mp4"
    },
    "destination": {
      "type": "GCP_BUCKET",
      "bucket": "poc-bucket-169bec51",
      "folder": "clourundest/may1/aha"

    }
  }

```

## sample (json) job file to to transfer file from GCP to sftp
```
{
    "source": {
      "type": "GCP_BUCKET",
      "bucket": "poc-bucket-169bec51",
      "folder": "poctest1/",
      "file_name": "REC_2024_10_25_16_54_26_F.MP4"

    },

    "destination": {
        "type": "SFTP",
        "sftp_server": "localhost",
        "sftp_port": 2222,        
        "ssh_user": "foo",
        "path": "/upload/web2/"

    }    

  }

  


```
## sample (json) job file to to transfer folder from  GCP to sftp (localhost)
```
{
    "source": {
      "type": "GCP_BUCKET",
      "bucket": "poc-bucket-169bec51",
      "folder": "pocdestinationf1a/"

    },
  
    "destination": {
        "type": "SFTP",
        "sftp_server": "localhost",
        "sftp_port": 9999,        
        "ssh_user": "foo",
        "path": "/uploads/cloudrun/april24a/"

    }
  }

```

## sample (json) job file to t transfer folder from   sftp to gcp
```
{
    "source": {
        "type": "SFTP",
        "sftp_server": "34.172.228.11",
        "sftp_port": 22,        
        "ssh_user": "sftpuser",
        "path": "/uploads/cloudrun/april24a/"


    },
    "destination": {
      "type": "GCP_BUCKET",
      "bucket": "poc-bucket-169bec51",
      "folder": "cloudrun/april25/00/"
    }
  }


```

