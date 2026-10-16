package tj.railway.mesplan.security;

import java.util.Locale;

/**
 * Положение переключателя mesplan.auth.mode.
 *
 *  OFF     — токен не проверяется вовсе (поведение до этапа 2; откат за секунды).
 *  LOG     — проверяется, но всё пропускается; каждый запрос, который был бы
 *            отвергнут, пишется в журнал. Для первых дней на рабочем сервере.
 *  ENFORCE — без действительного токена ответ 401.
 */
public enum AuthMode {
    OFF, LOG, ENFORCE;

    public static AuthMode parse(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (v.isEmpty()) {
            return OFF;
        }
        try {
            return AuthMode.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("mesplan.auth.mode = '" + value
                    + "' — допустимо off, log или enforce");
        }
    }
}
