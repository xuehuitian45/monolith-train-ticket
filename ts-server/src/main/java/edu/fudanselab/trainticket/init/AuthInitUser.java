package edu.fudanselab.trainticket.init;

import edu.fudanselab.trainticket.entity.AuthUser;
import edu.fudanselab.trainticket.repository.AuthUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * @author fdse
 */
@Component
public class AuthInitUser implements CommandLineRunner {

    @Autowired
    private AuthUserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;


    @Override
    public void run(String... strings) throws Exception {
        System.out.println("[AuthInitUser] Starting user initialization...");
        
        // Handle fdse_microservice user
        try {
            List<AuthUser> existingUsers = userRepository.findAllByUsername("fdse_microservice");
            if (existingUsers.isEmpty()) {
                AuthUser user = AuthUser.builder()
                        .userId("4d2a46c7-71cb-4cf1-b5bb-b68406d9da6f")
                        .username("fdse_microservice")
                        .password(passwordEncoder.encode("111111"))
                        .roles(new HashSet<>(Arrays.asList("ROLE_USER")))
                        .build();
                userRepository.save(user);
                System.out.println("[AuthInitUser] Created user: fdse_microservice");
            } else {
                // If duplicates exist, keep the first one and delete others
                if (existingUsers.size() > 1) {
                    System.out.println("[AuthInitUser] Found " + existingUsers.size() + " duplicate users for fdse_microservice, cleaning up...");
                    for (int i = 1; i < existingUsers.size(); i++) {
                        userRepository.delete(existingUsers.get(i));
                    }
                }
                System.out.println("[AuthInitUser] User fdse_microservice already exists");
            }
        } catch (Exception e) {
            System.err.println("[AuthInitUser] Error initializing fdse_microservice user: " + e.getMessage());
            e.printStackTrace();
        }

        // Handle admin user
        try {
            List<AuthUser> existingAdmins = userRepository.findAllByUsername("admin");
            if (existingAdmins.isEmpty()) {
                String encodedPassword = passwordEncoder.encode("222222");
                AuthUser admin = AuthUser.builder()
                        .userId(UUID.randomUUID().toString())
                        .username("admin")
                        .password(encodedPassword)
                        .roles(new HashSet<>(Arrays.asList("ROLE_ADMIN")))
                        .build();
                userRepository.save(admin);
                System.out.println("[AuthInitUser] Created admin user: admin, encoded password: " + encodedPassword);
            } else {
                // If duplicates exist, keep the first one and delete others
                if (existingAdmins.size() > 1) {
                    System.out.println("[AuthInitUser] Found " + existingAdmins.size() + " duplicate admin users, cleaning up...");
                    for (int i = 1; i < existingAdmins.size(); i++) {
                        userRepository.delete(existingAdmins.get(i));
                    }
                }
                System.out.println("[AuthInitUser] Admin user already exists, password hash: " + existingAdmins.get(0).getPassword());
            }
        } catch (Exception e) {
            System.err.println("[AuthInitUser] Error initializing admin user: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
