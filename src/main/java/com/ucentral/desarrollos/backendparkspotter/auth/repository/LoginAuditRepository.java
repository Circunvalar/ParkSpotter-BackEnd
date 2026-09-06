package com.ucentral.desarrollos.backendparkspotter.auth.repository;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.LoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoginAuditRepository extends JpaRepository<LoginAudit, UUID> {
}
