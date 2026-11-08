package com.ucentral.desarrollos.backendparkspotter.helpdesk.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.dto.ContactResponse;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.dto.CreateTicketRequest;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.dto.FaqResponse;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.dto.TicketMessageRequest;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.dto.TicketResponse;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.entity.SupportTicket;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.entity.SupportTicketMessage;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.entity.TicketCategory;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.entity.TicketStatus;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.repository.SupportTicketRepository;
import com.ucentral.desarrollos.backendparkspotter.notification.entity.NotificationType;
import com.ucentral.desarrollos.backendparkspotter.notification.service.NotificationService;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.Reservation;
import com.ucentral.desarrollos.backendparkspotter.reservation.repository.ReservationRepository;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ConflictException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Módulo de soporte (RNF-10): preguntas frecuentes, canal de contacto y casos (tickets) atendidos por
 * los administradores. Cada respuesta o cambio de estado de soporte avisa al usuario por notificación.
 */
@Service
public class SupportService {

    static final int MAX_ACTIVE_TICKETS = 5;
    static final String STAFF_NAME = "Soporte ParkSpotter";
    private static final List<TicketStatus> ACTIVE = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);

    private final SupportTicketRepository ticketRepository;
    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;
    private final Clock clock;
    private final ContactResponse contact;

    public SupportService(SupportTicketRepository ticketRepository, ReservationRepository reservationRepository,
                          NotificationService notificationService, Clock clock,
                          @Value("${app.support.email:soporte@parkspotter.co}") String email,
                          @Value("${app.support.phone:}") String phone,
                          @Value("${app.support.hours:Lunes a viernes, 8:00 a 18:00 (hora de Bogotá)}") String hours) {
        this.ticketRepository = ticketRepository;
        this.reservationRepository = reservationRepository;
        this.notificationService = notificationService;
        this.clock = clock;
        this.contact = new ContactResponse(email, phone == null || phone.isBlank() ? null : phone, hours);
    }

    // ---------- público ----------

    public List<FaqResponse> faqs(TicketCategory category) {
        return category == null ? FaqCatalog.FAQS
                : FaqCatalog.FAQS.stream().filter(f -> f.category().equals(category.name())).toList();
    }

    public ContactResponse contact() {
        return contact;
    }

    // ---------- usuario ----------

    @Transactional
    public TicketResponse create(CreateTicketRequest request, UserAccount user) {
        if (ticketRepository.countByUserIdAndStatusIn(user.getId(), ACTIVE) >= MAX_ACTIVE_TICKETS) {
            throw new ConflictException("Ya tienes " + MAX_ACTIVE_TICKETS + " casos abiertos. Espera la respuesta de soporte o cierra alguno.");
        }
        if (request.reservationId() != null) {
            Reservation reservation = reservationRepository.findById(request.reservationId())
                    .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada"));
            boolean related = reservation.getDriver().getId().equals(user.getId())
                    || reservation.getGarage().getOwner().getId().equals(user.getId());
            if (!related && !user.isAdmin()) {
                throw new AccessDeniedException("La reserva no es tuya ni de tus garajes");
            }
        }
        SupportTicket ticket = new SupportTicket();
        ticket.setUser(user);
        ticket.setSubject(request.subject().strip());
        ticket.setCategory(request.category());
        ticket.setReservationId(request.reservationId());
        addMessage(ticket, user, false, request.message());
        return toResponse(ticketRepository.saveAndFlush(ticket), true, user.isAdmin());
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> mine(UserAccount user, TicketStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<SupportTicket> result = status == null
                ? ticketRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                : ticketRepository.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), status, pageable);
        return PageResponse.of(result, t -> toResponse(t, false, false));
    }

    @Transactional(readOnly = true)
    public TicketResponse get(UUID ticketId, UserAccount user) {
        SupportTicket ticket = findAccessible(ticketId, user);
        return toResponse(ticket, true, user.isAdmin());
    }

    /**
     * Agrega un mensaje. Si lo escribe soporte (un admin en un caso ajeno) el caso pasa a IN_PROGRESS y
     * se avisa al usuario; si lo escribe el usuario, vuelve a OPEN (esperando a soporte), aunque estuviera resuelto.
     */
    @Transactional
    public TicketResponse reply(UUID ticketId, TicketMessageRequest request, UserAccount user) {
        SupportTicket ticket = findAccessible(ticketId, user);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new ConflictException("El caso está cerrado. Abre uno nuevo si necesitas más ayuda.");
        }
        boolean fromStaff = !ticket.getUser().getId().equals(user.getId());
        addMessage(ticket, user, fromStaff, request.body());
        if (fromStaff) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticket.setResolvedAt(null);
            notificationService.notify(ticket.getUser(), NotificationType.SUPPORT_UPDATE, "Soporte respondió tu caso",
                    "\"" + ticket.getSubject() + "\": " + request.body().strip(), ticket.getId(), true);
        } else {
            ticket.setStatus(TicketStatus.OPEN);
            ticket.setResolvedAt(null);
        }
        return toResponse(ticketRepository.saveAndFlush(ticket), true, user.isAdmin());
    }

    // ---------- soporte (administradores) ----------

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> inbox(TicketStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<SupportTicket> result = status == null
                ? ticketRepository.findAllByOrderByCreatedAtAsc(pageable)
                : ticketRepository.findByStatusOrderByCreatedAtAsc(status, pageable);
        return PageResponse.of(result, t -> toResponse(t, false, true));
    }

    @Transactional
    public TicketResponse changeStatus(UUID ticketId, TicketStatus status) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Caso de soporte no encontrado"));
        if (ticket.getStatus() == status) {
            return toResponse(ticket, true, true);
        }
        ticket.setStatus(status);
        ticket.setResolvedAt(status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED ? clock.instant() : null);
        if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
            String verb = status == TicketStatus.RESOLVED ? "resuelto" : "cerrado";
            notificationService.notify(ticket.getUser(), NotificationType.SUPPORT_UPDATE, "Tu caso fue " + verb,
                    "Soporte marcó como " + verb + " el caso \"" + ticket.getSubject() + "\".", ticket.getId(), true);
        }
        return toResponse(ticketRepository.saveAndFlush(ticket), true, true);
    }

    // ---------- helpers ----------

    private SupportTicket findAccessible(UUID ticketId, UserAccount user) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Caso de soporte no encontrado"));
        if (!ticket.getUser().getId().equals(user.getId()) && !user.isAdmin()) {
            throw new AccessDeniedException("Este caso no es tuyo");
        }
        return ticket;
    }

    private static void addMessage(SupportTicket ticket, UserAccount author, boolean fromStaff, String body) {
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicket(ticket);
        message.setAuthor(author);
        message.setFromStaff(fromStaff);
        message.setBody(body.strip());
        ticket.getMessages().add(message);
    }

    private static TicketResponse toResponse(SupportTicket ticket, boolean withMessages, boolean forStaff) {
        List<TicketResponse.Message> messages = withMessages
                ? ticket.getMessages().stream().map(SupportService::toMessage).toList()
                : List.of();
        UserAccount owner = ticket.getUser();
        return new TicketResponse(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getCategory(),
                ticket.getStatus(),
                ticket.getReservationId(),
                owner.getId(),
                forStaff ? owner.getEmail() : null,
                ticket.getMessages().size(),
                messages,
                ticket.getResolvedAt(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }

    private static TicketResponse.Message toMessage(SupportTicketMessage message) {
        String name;
        if (message.isFromStaff()) {
            name = STAFF_NAME;
        } else {
            UserAccount author = message.getAuthor();
            name = author == null || author.getDeletedAt() != null || author.getFullName() == null || author.getFullName().isBlank()
                    ? "Usuario" : author.getFullName().strip().split("\\s+")[0];
        }
        return new TicketResponse.Message(message.getId(), message.isFromStaff(), name, message.getBody(), message.getCreatedAt());
    }
}
