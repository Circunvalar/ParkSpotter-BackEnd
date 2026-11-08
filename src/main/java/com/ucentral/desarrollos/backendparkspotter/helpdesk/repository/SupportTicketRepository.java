package com.ucentral.desarrollos.backendparkspotter.helpdesk.repository;

import com.ucentral.desarrollos.backendparkspotter.helpdesk.entity.SupportTicket;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    Page<SupportTicket> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<SupportTicket> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, TicketStatus status, Pageable pageable);

    /** Bandeja de soporte: los más antiguos primero (se atienden en orden de llegada). */
    Page<SupportTicket> findAllByOrderByCreatedAtAsc(Pageable pageable);

    Page<SupportTicket> findByStatusOrderByCreatedAtAsc(TicketStatus status, Pageable pageable);

    long countByUserIdAndStatusIn(UUID userId, Collection<TicketStatus> statuses);
}
