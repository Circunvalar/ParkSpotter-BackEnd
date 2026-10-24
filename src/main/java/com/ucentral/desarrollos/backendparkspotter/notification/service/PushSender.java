package com.ucentral.desarrollos.backendparkspotter.notification.service;

import java.util.Map;

/** Envío de notificaciones push a un dispositivo. */
public interface PushSender {

    enum Result { SENT, INVALID_TOKEN, FAILED, DISABLED }

    Result send(String deviceToken, String title, String body, Map<String, String> data);
}
