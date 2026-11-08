package com.ucentral.desarrollos.backendparkspotter.helpdesk.service;

import com.ucentral.desarrollos.backendparkspotter.helpdesk.dto.FaqResponse;

import java.util.List;

/** Preguntas frecuentes (RNF-10). Están en el código para que viajen versionadas con la API. */
final class FaqCatalog {

    private FaqCatalog() {
    }

    static final List<FaqResponse> FAQS = List.of(
            new FaqResponse("cuenta-roles", "ACCOUNT", "¿Puedo ser conductor y propietario con la misma cuenta?",
                    "Sí. Al registrarte eliges un rol y después puedes activar el otro desde tu perfil."),
            new FaqResponse("cuenta-contrasena", "ACCOUNT", "Olvidé mi contraseña, ¿qué hago?",
                    "En la pantalla de inicio de sesión toca \"Olvidé mi contraseña\". Te llegará un correo con un enlace que vence en 30 minutos."),
            new FaqResponse("reserva-pago-plazo", "RESERVATION", "¿Cuánto tiempo tengo para pagar una reserva?",
                    "15 minutos desde que la creas. Si no se paga en ese tiempo, la reserva vence y la plaza se libera."),
            new FaqResponse("reserva-cancelar", "RESERVATION", "¿Puedo cancelar una reserva?",
                    "Sí, mientras esté pendiente de pago o confirmada. Si ya la pagaste, el reembolso se gestiona con la pasarela de pagos."),
            new FaqResponse("reserva-llegada", "RESERVATION", "¿Qué muestro al llegar al garaje?",
                    "El código de la reserva (por ejemplo PS-7K3MQ2), como texto o QR. Puedes entrar desde 30 minutos antes del inicio."),
            new FaqResponse("reserva-tiempo-extra", "RESERVATION", "¿Qué pasa si salgo después de la hora reservada?",
                    "Se calcula el tiempo extra con el mismo precio por bloques de 30 minutos y se paga en el garaje."),
            new FaqResponse("pago-seguro", "PAYMENT", "¿Es seguro pagar en ParkSpotter?",
                    "Sí. El pago lo procesa Wompi; ParkSpotter nunca ve ni guarda los datos de tu tarjeta."),
            new FaqResponse("pago-comprobante", "PAYMENT", "¿Dónde encuentro el comprobante de pago?",
                    "En el detalle de la reserva, opción \"Comprobante\", disponible cuando el pago fue aprobado."),
            new FaqResponse("garaje-verificado", "GARAGE", "¿Qué significa la insignia de garaje verificado?",
                    "Que un administrador revisó las fotos y los datos del garaje. Los propietarios la piden desde el detalle de su garaje."),
            new FaqResponse("garaje-publicar", "GARAGE", "¿Cómo publico mi garaje?",
                    "Con el rol de propietario, crea el garaje con dirección, ubicación, precio y horario. Sube fotos y pide la verificación."),
            new FaqResponse("soporte-contacto", "OTHER", "¿Cómo contacto a soporte?",
                    "Abre un caso desde Ayuda > Contactar soporte. Te avisaremos por notificación cuando respondamos.")
    );
}
