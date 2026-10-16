package tj.railway.mesplan.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Журнал вызовов точек с @NoKnownClient: первый вызов каждой — WARN (один раз
 * за запуск службы, чтобы не засорять журнал), остальные — DEBUG.
 * Ничего не запрещает: точка работает как прежде.
 */
@Configuration
public class NoKnownClientInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(NoKnownClientInterceptor.class);

    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod) {
            HandlerMethod method = (HandlerMethod) handler;
            NoKnownClient mark = method.getMethodAnnotation(NoKnownClient.class);
            if (mark != null) {
                String name = method.getBeanType().getSimpleName() + "." + method.getMethod().getName();
                if (this.reported.add(name)) {
                    log.warn("Вызвана точка без известного клиента: {} {} ({}) с адреса {} — прежде чем удалять, выяснить, кто вызывает",
                            request.getMethod(), request.getRequestURI(), name, request.getRemoteAddr());
                } else {
                    log.debug("Снова вызвана точка без известного клиента: {} {}", request.getMethod(), request.getRequestURI());
                }
            }
        }
        return true;
    }
}
