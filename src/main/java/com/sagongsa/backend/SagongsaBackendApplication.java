package com.sagongsa.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SagongsaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SagongsaBackendApplication.class, args);
	}

}
