package org.budgetanalyzer.permission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PermissionServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(PermissionServiceApplication.class, args);
  }
}
