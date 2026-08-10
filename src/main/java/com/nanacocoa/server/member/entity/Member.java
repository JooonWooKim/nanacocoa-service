package com.nanacocoa.server.member.entity;

import com.nanacocoa.server.common.exception.NanacocoaException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static com.nanacocoa.server.common.exception.ErrorCode.ALREADY_EXIST_EMAIL;
import static com.nanacocoa.server.common.exception.ErrorCode.ADMIN_ACCESS_REQUIRED;

@Entity
@Table(name = "members")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_admin", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean admin = false;

    @Builder
    public Member(String email, String name, String password) {
        this.email = email;
        this.name = name;
        this.password = password;
        this.admin = false;
    }

    public void checkEmailDuplicate(String email){
        if(! this.email.equals(email)){
            throw new NanacocoaException(ALREADY_EXIST_EMAIL);
        }
    }

    public void checkPassword(PasswordEncoder passwordEncoder, String password){
        if(! passwordEncoder.matches(password, this.password)){
            throw new NanacocoaException(ALREADY_EXIST_EMAIL);
        }
    }

    public void checkAdmin() {
        if (!admin) {
            throw new NanacocoaException(ADMIN_ACCESS_REQUIRED);
        }
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
