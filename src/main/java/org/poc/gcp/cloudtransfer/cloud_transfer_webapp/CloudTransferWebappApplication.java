package org.poc.gcp.cloudtransfer.cloud_transfer_webapp;



import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@SpringBootApplication
@EnableAsync // <<< Add this annotation
public class CloudTransferWebappApplication {

	public static void main(String[] args) {
		SpringApplication.run(CloudTransferWebappApplication.class, args);
	}

	// You might want to configure the async executor bean here too for fine-tuning

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); // Start with 2 threads
        executor.setMaxPoolSize(5); // Allow up to 5 threads
        executor.setQueueCapacity(50); // Queue jobs if all threads are busy
        executor.setThreadNamePrefix("FileTransfer-");
        executor.initialize();
        return executor;
    }

}
