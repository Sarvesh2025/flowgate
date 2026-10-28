package com.flowgate.gateway.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder passwords;

    public UserSeeder(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Override
    public void run(String... args) {
        seed("admin", "admin123", "ADMIN");
        seed("operator", "operator123", "OPERATOR");
        seed("viewer", "viewer123", "VIEWER");
    }

    private void seed(String username, String rawPassword, String role) {
        if (users.findByUsername(username).isPresent()) {
            return;
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwords.encode(rawPassword));
        user.setRole(role);
        users.save(user);
    }
}
