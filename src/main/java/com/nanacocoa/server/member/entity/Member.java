package com.nanacocoa.server.member.entity;

import com.nanacocoa.server.common.exception.NanacocoaException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static com.nanacocoa.server.common.exception.ErrorCode.ADMIN_ACCESS_REQUIRED;
import static com.nanacocoa.server.common.exception.ErrorCode.NOT_VALID_PASSWORD;

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

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "phone_number", nullable = false, length = 13)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_receive_consent", nullable = false, length = 1)
    private EmailReceiveConsent emailReceiveConsent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_admin", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean admin = false;

    @Builder
    public Member(
            String email,
            String name,
            String password,
            String address,
            String phoneNumber,
            EmailReceiveConsent emailReceiveConsent
    ) {
        this.email = email;
        this.name = name;
        this.password = password;
        this.address = address == null ? "" : address;
        this.phoneNumber = phoneNumber == null ? "" : phoneNumber;
        this.emailReceiveConsent = emailReceiveConsent == null ? EmailReceiveConsent.N : emailReceiveConsent;
        this.admin = false;
    }

    public void checkPassword(PasswordEncoder passwordEncoder, String password){
        if(! passwordEncoder.matches(password, this.password)){
            throw new NanacocoaException(NOT_VALID_PASSWORD);
        }
    }

    public void updateProfile(
            String name,
            String address,
            String phoneNumber,
            EmailReceiveConsent emailReceiveConsent
    ) {
        this.name = name;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.emailReceiveConsent = emailReceiveConsent;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
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
        if (address == null) {
            address = "";
        }
        if (phoneNumber == null) {
            phoneNumber = "";
        }
        if (emailReceiveConsent == null) {
            emailReceiveConsent = EmailReceiveConsent.N;
        }
    }
}
