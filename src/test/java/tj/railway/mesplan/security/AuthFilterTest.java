package tj.railway.mesplan.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AuthFilterTest {

    private static final JwtVerifier VERIFIER = new JwtVerifier(JwtVerifierTest.SECRET);

    private static String token(String... roles) {
        return JwtVerifierTest.token(JwtVerifierTest.SECRET, "planner", Arrays.asList(roles), 60_000);
    }

    private static final class Result {
        final MockHttpServletResponse response;
        final boolean passed;

        Result(MockHttpServletResponse response, boolean passed) {
            this.response = response;
            this.passed = passed;
        }

        String body() throws Exception {
            return response.getContentAsString(StandardCharsets.UTF_8);
        }
    }

    private static Result run(AuthMode mode, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new AuthFilter(mode, mode == AuthMode.OFF ? null : VERIFIER).doFilter(request, response, chain);
        return new Result(response, chain.getRequest() != null);
    }

    private static MockHttpServletRequest req(String token) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/order/save");
        if (token != null) {
            r.addHeader("Authorization", "Bearer " + token);
        }
        return r;
    }

    @Test
    void enforceAcceptsMesplanAndAdmin() throws Exception {
        assertTrue(run(AuthMode.ENFORCE, req(token("ROLE_USER", "ROLE_MESPLAN"))).passed);
        assertTrue(run(AuthMode.ENFORCE, req(token("ROLE_ADMIN"))).passed);
    }

    /** Те же роли, что у страницы /oper/mesplan во фронте: ROLE_D туда не пускают — и сюда тоже. */
    @Test
    void enforceRefusesOtherRolesWith403() throws Exception {
        for (String[] roles : new String[][]{{"ROLE_D"}, {"ROLE_USER"}, {"ROLE_STAT"}, {}}) {
            Result r = run(AuthMode.ENFORCE, req(token(roles)));
            assertFalse(r.passed, Arrays.toString(roles));
            assertEquals(403, r.response.getStatus());
            assertEquals("{\"message\":\"Нет доступа к МЕСПЛАН\"}", r.body());
            assertEquals("*", r.response.getHeader("Access-Control-Allow-Origin"));
        }
    }

    @Test
    void enforceRefusesMissingForeignExpiredWith401() throws Exception {
        String foreign = JwtVerifierTest.token(JwtVerifierTest.OTHER, "planner",
                Collections.singletonList("ROLE_MESPLAN"), 60_000);
        String expired = JwtVerifierTest.token(JwtVerifierTest.SECRET, "planner",
                Collections.singletonList("ROLE_MESPLAN"), -1_000);
        for (String t : new String[]{null, foreign, expired, "garbage"}) {
            Result r = run(AuthMode.ENFORCE, req(t));
            assertFalse(r.passed);
            assertEquals(401, r.response.getStatus());
            assertEquals("{\"message\":\"Требуется вход в систему\"}", r.body());
            assertEquals("*", r.response.getHeader("Access-Control-Allow-Origin"),
                    "без заголовка CORS браузер покажет фронту status 0");
        }
    }

    @Test
    void preflightAlwaysPasses() throws Exception {
        assertTrue(run(AuthMode.ENFORCE, new MockHttpServletRequest("OPTIONS", "/api/order/save")).passed);
    }

    @Test
    void tokenInQueryIsNotAccepted() throws Exception {
        MockHttpServletRequest r = req(null);
        r.setParameter("access_token", token("ROLE_MESPLAN"));
        assertFalse(run(AuthMode.ENFORCE, r).passed, "здесь нет потока EventSource — токен только в заголовке");
    }

    @Test
    void logPassesEverything() throws Exception {
        assertTrue(run(AuthMode.LOG, req(null)).passed);
        assertTrue(run(AuthMode.LOG, req(token("ROLE_D"))).passed);
        assertEquals(200, run(AuthMode.LOG, req(null)).response.getStatus());
    }

    @Test
    void offChecksNothing() throws Exception {
        assertTrue(run(AuthMode.OFF, req("garbage")).passed);
    }

    @Test
    void modeParsing() {
        assertEquals(AuthMode.OFF, AuthMode.parse(null));
        assertEquals(AuthMode.LOG, AuthMode.parse(" Log "));
        assertEquals(AuthMode.ENFORCE, AuthMode.parse("ENFORCE"));
        assertThrows(IllegalStateException.class, () -> AuthMode.parse("on"));
    }
}
