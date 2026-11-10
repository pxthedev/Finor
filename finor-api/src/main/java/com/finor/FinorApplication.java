package com.finor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class FinorApplication {
  public static void main(String[] args) {
    SpringApplication.run(FinorApplication.class, args);
  }
}
