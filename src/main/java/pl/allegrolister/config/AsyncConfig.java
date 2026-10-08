package pl.allegrolister.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    /**
     * Pula wątków do wystawiania i operacji grupowych.
     * Mała liczba wątków = bezpiecznie względem limitów API Allegro.
     */
    @Bean(name = "listingExecutor")
    public Executor listingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(10000);
        executor.setThreadNamePrefix("allegro-");
        executor.initialize();
        return executor;
    }
}
