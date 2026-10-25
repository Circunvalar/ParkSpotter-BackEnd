package com.ucentral.desarrollos.backendparkspotter.garageManagement.service;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;

import java.time.LocalTime;

/**
 * Reglas de horario de un garaje. Soporta horarios que cruzan la medianoche (ej. 18:00 - 06:00).
 */
public final class OpeningHours {

    private OpeningHours() {
    }

    public static boolean isOpenAt(Garage garage, LocalTime time) {
        return isOpenAt(garage.isOpen24Hours(), garage.getOpeningTime(), garage.getClosingTime(), time);
    }

    public static boolean isOpenAt(boolean open24Hours, LocalTime opening, LocalTime closing, LocalTime time) {
        if (open24Hours) {
            return true;
        }
        if (opening == null || closing == null) {
            return false;
        }
        if (opening.equals(closing)) {
            return true;
        }
        if (opening.isBefore(closing)) {
            return !time.isBefore(opening) && time.isBefore(closing);
        }
        // Horario nocturno: abierto desde la apertura hasta medianoche y desde medianoche hasta el cierre.
        return !time.isBefore(opening) || time.isBefore(closing);
    }
}
