package tj.railway.mesplan.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Пароль не задан — служба не стартует.
 *
 * В application.properties пароль — ссылка на переменную среды
 * ${MESPLAN_DB_PASSWORD}. Spring Boot при незаданной переменной не падает:
 * в пароль уходит сам текст «${MESPLAN_DB_PASSWORD}», вход в SQL Server не
 * проходит, а служба остаётся запущенной и отвечает ошибками. Проверка идёт до
 * создания любого компонента и называет каждую незаданную переменную.
 * Подключена в META-INF/spring.factories. Тот же приём, что в AsoupServer.
 */
public class RequiredSecrets implements EnvironmentPostProcessor {

    static final String[] KEYS = {"spring.datasource.password"};

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        List<String> missing = missingFor(env);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Служба не запущена: не заданы переменные среды " + String.join(", ", missing)
                    + ". На рабочем сервере — <env name=\"…\" value=\"…\"/> в xml обёртки WinSW.");
        }
    }

    public static List<String> missingFor(ConfigurableEnvironment env) {
        List<String> missing = new ArrayList<>();
        for (String key : KEYS) {
            String value;
            try {
                value = env.getProperty(key);
            } catch (IllegalArgumentException unresolved) {
                Matcher m = PLACEHOLDER.matcher(String.valueOf(unresolved.getMessage()));
                missing.add(m.find() ? m.group(1) : key);
                continue;
            }
            if (value == null || value.isEmpty()) {
                missing.add(key);
            }
        }
        return missing;
    }
}
