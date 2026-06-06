package com.cryptofolio.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptofolioBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(CryptofolioBackendApplication.class, args);
	}

}
