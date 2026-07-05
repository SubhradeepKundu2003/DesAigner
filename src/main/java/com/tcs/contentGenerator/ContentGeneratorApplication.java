package com.tcs.contentGenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ContentGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ContentGeneratorApplication.class, args);
	}

}
