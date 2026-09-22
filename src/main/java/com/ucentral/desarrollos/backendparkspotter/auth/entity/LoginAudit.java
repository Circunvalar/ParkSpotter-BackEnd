package com.ucentral.desarrollos.backendparkspotter.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "login_audit")
public class LoginAudit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 64)
    private String clientId;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(nullable = false, length = 128)
    private String ipAddress;

    @Column(nullable = false, length = 512)
    private String userAgent;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
