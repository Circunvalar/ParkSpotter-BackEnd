package com.ucentral.desarrollos.backendparkspotter.shared.validation;

/**
 * Expresiones regulares compartidas por los DTOs, para que web y Android validen igual que el backend.
 */
public final class ValidationPatterns {

    /**
     * Correo con dominio y extensión (más estricto que @Email, que acepta "a@b").
     * Tolera espacios alrededor (autocompletado del teclado en celular): el servicio los recorta.
     */
    public static final String EMAIL = "^\\s*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\s*$";

    /** Al menos un carácter que no sea espacio (para campos opcionales que, si llegan, no pueden ir vacíos). */
    public static final String NOT_BLANK = "(?s).*\\S.*";

    /** Teléfono: dígitos, espacios, guiones y paréntesis, con + opcional al inicio. 7 a 20 caracteres. */
    public static final String PHONE = "^\\+?[0-9()\\- ]{7,20}$";

    /**
     * Ciudad, departamento o país: letras (con tildes y ñ), espacios, puntos, apóstrofes y guiones.
     * Se toleran espacios al inicio y al final porque el servicio los recorta antes de guardar.
     */
    public static final String PLACE_NAME = "^\\s*\\p{L}[\\p{L} .'-]*$";

    /** Código postal: letras, números, espacios o guiones. 3 a 10 caracteres. */
    public static final String POSTAL_CODE = "^[A-Za-z0-9 -]{3,10}$";

    /** Código de plaza: letras, números y guiones (ej. P-001, A1-12). */
    public static final String SPOT_CODE = "^[A-Za-z0-9-]+$";

    /** Refresh token opaco (base64 url-safe). */
    public static final String OPAQUE_TOKEN = "^[A-Za-z0-9_-]+$";

    /** Identificador de cliente (X-Client-Id): letras, números, punto, guion y guion bajo. */
    public static final String CLIENT_ID = "^[A-Za-z0-9._-]+$";

    private ValidationPatterns() {
    }
}
