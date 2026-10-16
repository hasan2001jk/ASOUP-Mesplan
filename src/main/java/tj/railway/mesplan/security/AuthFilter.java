package tj.railway.mesplan.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Проверка доступа на каждом запросе к /api/** (регистрация — AuthConfig).
 *
 * Два шага:
 *  1. действительный токен Backend-Auth в «Authorization: Bearer …» — иначе 401;
 *  2. роль ROLE_MESPLAN или ROLE_ADMIN — иначе 403. Это те же роли, с которыми
 *     фронт пускает на страницу /oper/mesplan, так что оператору с доступом к
 *     странице ничего не отказывается.
 *
 * Роли читаются из claim authorities. Его кладёт только НОВЫЙ Backend-Auth;
 * токены старого его не несут, и в enforce такой пользователь получит 403.
 * Поэтому порядок выката: новый Backend-Auth → Mesplan в log → enforce.
 *
 * OPTIONS (предварительные запросы CORS) пропускаются всегда: браузер шлёт их
 * без Authorization.
 */
public class AuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    static final Set<String> ALLOWED_ROLES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("ROLE_MESPLAN", "ROLE_ADMIN")));

    private final AuthMode mode;
    private final JwtVerifier verifier;

    public AuthFilter(AuthMode mode, JwtVerifier verifier) {
        this.mode = mode;
        this.verifier = verifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return this.mode == AuthMode.OFF || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        int status;
        String reason;
        String message;
        try {
            JwtVerifier.Caller caller = this.verifier.verify(tokenOf(request));
            if (!Collections.disjoint(caller.getRoles(), ALLOWED_ROLES)) {
                chain.doFilter(request, response);
                return;
            }
            status = HttpServletResponse.SC_FORBIDDEN;
            reason = "нет роли МЕСПЛАН у " + caller.getName();
            message = "Нет доступа к МЕСПЛАН";
        } catch (JwtVerifier.Rejected rejected) {
            status = HttpServletResponse.SC_UNAUTHORIZED;
            reason = rejected.getMessage();
            message = "Требуется вход в систему";
        }
        if (this.mode == AuthMode.LOG) {
            log.warn("Запрос был бы отклонён ({} {}): {} {} с {}",
                    status, reason, request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
            chain.doFilter(request, response);
            return;
        }
        log.warn("Запрос отклонён ({} {}): {} {} с {}",
                status, reason, request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
        deny(response, status, message);
    }

    static String tokenOf(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return null;
    }

    /**
     * Отказ — JSON {"message": "…"}, как остальные ошибки службы. Заголовок CORS
     * обязателен: отказ не доходит до контроллера с @CrossOrigin, и без заголовка
     * браузер покажет фронту не 401/403, а «status 0».
     */
    static void deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8));
    }
}
