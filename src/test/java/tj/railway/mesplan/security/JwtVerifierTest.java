package tj.railway.mesplan.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtVerifierTest {

    static final String SECRET = "test-secret-for-mesplan-only-not-production-0123456789abcdefghijklm";
    static final String OTHER = "another-secret-that-is-also-long-enough-for-hs512-0123456789abcdefgh";

    /** Ровно как Backend-Auth: JwtProvider.generateJwtToken. */
    static String token(String secret, String name, List<String> roles, long ttlMs) {
        return Jwts.builder()
                .setSubject(name)
                .claim("authorities", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512)
                .compact();
    }

    private final JwtVerifier verifier = new JwtVerifier(SECRET);

    @Test
    void acceptsBackendAuthToken() throws Exception {
        JwtVerifier.Caller c = verifier.verify(token(SECRET, "Regar", Arrays.asList("ROLE_USER", "ROLE_D"), 60_000));
        assertEquals("Regar", c.getName());
        assertEquals(Arrays.asList("ROLE_USER", "ROLE_D"), Arrays.asList(c.getRoles().toArray()));
        assertFalse(c.isAdmin());
        assertTrue(verifier.verify(token(SECRET, "admin", Collections.singletonList("ROLE_ADMIN"), 60_000)).isAdmin());
    }

    @Test
    void oldTokenWithoutRolesIsOrdinaryUser() throws Exception {
        String t = Jwts.builder().setSubject("Regar").setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512).compact();
        JwtVerifier.Caller c = verifier.verify(t);
        assertTrue(c.getRoles().isEmpty());
        assertFalse(c.isAdmin(), "без claim ролей — не администратор");
    }

    @Test
    void springStyleAuthoritiesAreUnderstood() throws Exception {
        String t = Jwts.builder().setSubject("admin")
                .claim("authorities", Collections.singletonList(Collections.singletonMap("authority", "ROLE_ADMIN")))
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512).compact();
        assertTrue(verifier.verify(t).isAdmin());
    }

    @Test
    void rejectsForeignSecret() {
        JwtVerifier.Rejected e = assertThrows(JwtVerifier.Rejected.class,
                () -> verifier.verify(token(OTHER, "Regar", Collections.emptyList(), 60_000)));
        assertEquals("токен недействителен", e.getMessage());
    }

    @Test
    void rejectsExpired() {
        JwtVerifier.Rejected e = assertThrows(JwtVerifier.Rejected.class,
                () -> verifier.verify(token(SECRET, "Regar", Collections.emptyList(), -1_000)));
        assertEquals("срок токена истёк", e.getMessage());
    }

    /** Подмена имени или ролей в теле без пересчёта подписи. */
    @Test
    void rejectsTamperedPayload() {
        String[] parts = token(SECRET, "Regar", Collections.singletonList("ROLE_USER"), 60_000).split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"admin\",\"authorities\":[\"ROLE_ADMIN\"],\"exp\":" + (System.currentTimeMillis() / 1000 + 600) + "}")
                        .getBytes(StandardCharsets.UTF_8));
        assertThrows(JwtVerifier.Rejected.class, () -> verifier.verify(parts[0] + "." + forged + "." + parts[2]));
    }

    /** Токен без подписи («alg: none») — классическая атака. */
    @Test
    void rejectsUnsignedToken() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"admin\",\"authorities\":[\"ROLE_ADMIN\"],\"exp\":" + (System.currentTimeMillis() / 1000 + 600) + "}")
                        .getBytes(StandardCharsets.UTF_8));
        assertThrows(JwtVerifier.Rejected.class, () -> verifier.verify(header + "." + body + "."));
    }

    @Test
    void rejectsGarbageAndEmpty() {
        assertEquals("нет токена", assertThrows(JwtVerifier.Rejected.class, () -> verifier.verify(null)).getMessage());
        assertEquals("нет токена", assertThrows(JwtVerifier.Rejected.class, () -> verifier.verify("  ")).getMessage());
        assertThrows(JwtVerifier.Rejected.class, () -> verifier.verify("abc.def.ghi"));
    }

    @Test
    void rejectsTokenWithoutName() {
        String t = Jwts.builder().setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512).compact();
        assertEquals("в токене нет имени пользователя",
                assertThrows(JwtVerifier.Rejected.class, () -> verifier.verify(t)).getMessage());
    }

    @Test
    void refusesMissingOrShortSecretAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtVerifier(null));
        assertThrows(IllegalStateException.class, () -> new JwtVerifier(""));
        assertThrows(IllegalStateException.class, () -> new JwtVerifier("short-secret"));
    }
}
