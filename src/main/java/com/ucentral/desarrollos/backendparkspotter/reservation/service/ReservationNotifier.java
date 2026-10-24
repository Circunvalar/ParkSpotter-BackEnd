package com.ucentral.desarrollos.backendparkspotter.reservation.service;

import com.ucentral.desarrollos.backendparkspotter.notification.entity.NotificationType;
import com.ucentral.desarrollos.backendparkspotter.notification.service.NotificationService;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.Reservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Textos de las notificaciones de reservas para el conductor y el dueño del garaje (PB-23). */
@Component
@RequiredArgsConstructor
public class ReservationNotifier {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.forLanguageTag("es-CO"));

    private final NotificationService notificationService;
    private final Clock clock;

    public void created(Reservation r) {
        notificationService.notify(r.getDriver(), NotificationType.RESERVATION_CREATED, "Reserva creada: falta el pago",
                "Tu reserva " + r.getCode() + " en " + r.getGarage().getName() + " para el " + when(r)
                        + " queda apartada hasta " + time(r.getPaymentDeadline()) + ". Paga para confirmarla.",
                r.getId(), false);
    }

    public void confirmed(Reservation r) {
        notificationService.notify(r.getDriver(), NotificationType.RESERVATION_CONFIRMED, "Reserva confirmada",
                "Pago recibido. Tu código de entrada es " + r.getCode() + " (" + r.getGarage().getName() + ", " + when(r) + ").",
                r.getId(), true);
        notificationService.notify(r.getGarage().getOwner(), NotificationType.RESERVATION_CONFIRMED, "Nueva reserva pagada",
                "Reserva " + r.getCode() + " en " + r.getGarage().getName() + " para el " + when(r)
                        + (r.getVehiclePlate() == null ? "." : " — placa " + r.getVehiclePlate() + "."),
                r.getId(), true);
    }

    public void cancelled(Reservation r, boolean byDriver) {
        String body = "La reserva " + r.getCode() + " en " + r.getGarage().getName() + " para el " + when(r) + " fue cancelada"
                + (r.getCancellationReason() == null ? "." : ": " + r.getCancellationReason());
        notificationService.notify(r.getDriver(), NotificationType.RESERVATION_CANCELLED, "Reserva cancelada", body, r.getId(), !byDriver);
        notificationService.notify(r.getGarage().getOwner(), NotificationType.RESERVATION_CANCELLED, "Reserva cancelada", body, r.getId(), false);
    }

    public void expired(Reservation r) {
        notificationService.notify(r.getDriver(), NotificationType.RESERVATION_EXPIRED, "Reserva vencida",
                "La reserva " + r.getCode() + " venció porque no se completó el pago a tiempo. La plaza quedó libre.",
                r.getId(), false);
    }

    public void noShow(Reservation r) {
        notificationService.notify(r.getDriver(), NotificationType.RESERVATION_NO_SHOW, "No registraste entrada",
                "La franja de tu reserva " + r.getCode() + " terminó sin registrar entrada en " + r.getGarage().getName() + ".",
                r.getId(), false);
    }

    public void checkedIn(Reservation r) {
        notificationService.notify(r.getDriver(), NotificationType.CHECK_IN, "Entrada registrada",
                "Bienvenido a " + r.getGarage().getName() + ". Tu reserva termina a las " + time(r.getEndTime()) + ".",
                r.getId(), false);
    }

    public void checkedOut(Reservation r) {
        String overtime = r.getOvertimeMinutes() > 0
                ? " Tiempo adicional: " + r.getOvertimeMinutes() + " min ($" + r.getOvertimeAmount().toPlainString() + ", se paga en el garaje)."
                : "";
        notificationService.notify(r.getDriver(), NotificationType.CHECK_OUT, "Salida registrada",
                "Gracias por usar " + r.getGarage().getName() + "." + overtime + " ¡Califica tu experiencia!",
                r.getId(), false);
    }

    private String when(Reservation r) {
        return WHEN.format(ZonedDateTime.ofInstant(r.getStartTime(), clock.getZone()));
    }

    private String time(java.time.Instant instant) {
        return DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-CO")).format(ZonedDateTime.ofInstant(instant, clock.getZone()));
    }
}
