package com.crypto.arbitrage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptoArbitrageApplication {

	public static void main(String[] args) {
		SpringApplication.run(CryptoArbitrageApplication.class, args);
	}

}
