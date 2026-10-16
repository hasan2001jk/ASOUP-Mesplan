package tj.railway.mesplan.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Проверка токена, выданного Backend-Auth.
 *
 * Формат тот же, что в Backend-Auth (JwtProvider.generateJwtToken): HS512,
 * ключ — байты секрета в UTF-8 (Keys.hmacShaKeyFor), sub — имя пользователя,
 * authorities — список ролей строками ["ROLE_D", "ROLE_ADMIN"].
 *
 * Подпись проверяется здесь же, без обращения к Backend-Auth: служба
 * не зависит от его доступности, а подделать или изменить токен нельзя.
 *
 * Роли берутся из токена. Backend-Auth у себя перечитывает их из своей базы
 * на каждый запрос; у Mesplan доступа к той базе нет. Следствие: снятая
 * роль перестаёт действовать здесь, когда истечёт срок токена.
 */
public final class JwtVerifier {

    /** HS512 требует ключ не короче 512 бит — Keys.hmacShaKeyFor иначе откажет. */
    static final int MIN_SECRET_BYTES = 64;

    /** Кто вызывает: имя пользователя (= имя абонента) и его роли. */
    public static final class Caller {
        private final String name;
        private final Set<String> roles;

        Caller(String name, Set<String> roles) {
            this.name = name;
            this.roles = Collections.unmodifiableSet(roles);
        }

        public String getName() {
            return name;
        }

        public Set<String> getRoles() {
            return roles;
        }

        public boolean isAdmin() {
            return roles.contains("ROLE_ADMIN");
        }

        @Override
        public String toString() {
            return name + roles;
        }
    }

    /** Токен не принят. reason — для журнала и ответа, без содержимого токена. */
    public static final class Rejected extends Exception {
        Rejected(String reason) {
            super(reason);
        }
    }

    private final JwtParser parser;

    public JwtVerifier(String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("Не задан секрет токенов (переменная среды JWT_SECRET "
                    + "или mesplan.auth.jwt-secret) — тот же, что у Backend-Auth");
        }
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("Секрет токенов короче " + MIN_SECRET_BYTES
                    + " байт: для HS512 он не годится (Backend-Auth такой тоже не примет)");
        }
        this.parser = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(key)).build();
    }

    public Caller verify(String token) throws Rejected {
        if (token == null || token.trim().isEmpty()) {
            throw new Rejected("нет токена");
        }
        Claims claims;
        try {
            claims = this.parser.parseClaimsJws(token.trim()).getBody();
        } catch (ExpiredJwtException e) {
            throw new Rejected("срок токена истёк");
        } catch (JwtException | IllegalArgumentException e) {
            throw new Rejected("токен недействителен");
        }
        String name = claims.getSubject();
        if (name == null || name.trim().isEmpty()) {
            throw new Rejected("в токене нет имени пользователя");
        }
        return new Caller(name, roles(claims.get("authorities")));
    }

    /**
     * Роли из claim authorities. Нынешний Backend-Auth пишет список строк;
     * на всякий случай понимаем и форму Spring [{"authority": "ROLE_X"}].
     * Нет claim — нет ролей (обычный пользователь).
     */
    private static Set<String> roles(Object claim) {
        Set<String> result = new LinkedHashSet<>();
        if (claim instanceof Collection) {
            for (Object item : (Collection<?>) claim) {
                if (item instanceof String) {
                    result.add((String) item);
                } else if (item instanceof Map && ((Map<?, ?>) item).get("authority") instanceof String) {
                    result.add((String) ((Map<?, ?>) item).get("authority"));
                }
            }
        }
        return result;
    }
}
