package tj.railway.mesplan.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Настройки проверки доступа.
 *
 *   mesplan.auth.mode        off | log | enforce   (переменная среды MESPLAN_AUTH_MODE, по умолчанию off)
 *   mesplan.auth.jwt-secret  секрет Backend-Auth   (по умолчанию переменная среды JWT_SECRET)
 *
 * По умолчанию off — JAR без новых настроек ведёт себя как прежде.
 * В log и enforce без секрета служба не стартует: молча работать без защиты нельзя.
 *
 * ⚠ Фронт при любом 401 выходит из системы. Перед enforce обязательно пожить в log
 * и убедиться, что в журнале нет «был бы отклонён (401 …)» для своих операторов —
 * иначе неверный JWT_SECRET выкинет из системы всех, кто откроет МЕСПЛАН.
 */
@Configuration
public class AuthConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

    @Bean
    public AuthMode authMode(@Value("${mesplan.auth.mode:off}") String modeValue) {
        return AuthMode.parse(modeValue);
    }

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilter(
            AuthMode mode,
            @Value("${mesplan.auth.jwt-secret:${JWT_SECRET:}}") String secret) {
        JwtVerifier verifier = mode == AuthMode.OFF ? null : new JwtVerifier(secret);
        if (mode == AuthMode.OFF) {
            log.warn("Проверка токенов ВЫКЛЮЧЕНА (mesplan.auth.mode=off): /api/** доступен без входа");
        } else {
            log.warn("Проверка токенов: {}", mode == AuthMode.LOG
                    ? "LOG — всё пропускается, отказы только пишутся в журнал"
                    : "ENFORCE — без токена 401, без роли МЕСПЛАН 403");
        }
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>(new AuthFilter(mode, verifier));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
