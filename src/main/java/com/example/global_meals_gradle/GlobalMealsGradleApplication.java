package com.example.global_meals_gradle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class, //
		ServletWebSecurityAutoConfiguration.class })
public class GlobalMealsGradleApplication {

	public static void main(String[] args) {
		SpringApplication.run(GlobalMealsGradleApplication.class, args);
	}

}