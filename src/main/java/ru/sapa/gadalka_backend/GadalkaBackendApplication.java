package ru.sapa.gadalka_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class GadalkaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GadalkaBackendApplication.class, args);
	}

}
