package com.example.hellospringapi;

import com.example.hellospringapi.market.simulator.MarketDataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MarketDataProperties.class)
public class HelloSpringApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(HelloSpringApiApplication.class, args);
	}

}
