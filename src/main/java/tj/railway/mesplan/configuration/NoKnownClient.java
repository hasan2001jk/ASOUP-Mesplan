package tj.railway.mesplan.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Точка без известного клиента.
 *
 * У отмеченной точки нет вызова во фронтенде Oper (oper-dev/src, сверено при
 * доработке). Удалять сразу не стали: NoKnownClientInterceptor пишет в журнал
 * первый вызов каждой такой точки (WARN «Вызвана точка без известного клиента …»)
 * с адресом вызывающего. Нет таких записей за несколько месяцев — можно удалять.
 *
 * value — почему клиента нет.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoKnownClient {
    String value();
}
