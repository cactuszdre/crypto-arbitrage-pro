package com.crypto.arbitrage;

import com.crypto.arbitrage.config.DotenvInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class CryptoArbitrageApplication {

	public static void main(String[] args) {
		// Load .env file BEFORE Spring context starts
		DotenvInitializer.loadEnvFile();
		
		SpringApplication.run(CryptoArbitrageApplication.class, args);
	}

}
