package tj.railway.mesplan;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import tj.railway.mesplan.configuration.RequiredSecrets;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** Пароль только из переменной среды, без неё служба не стартует. */
class RequiredSecretsTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static StandardEnvironment env(String... envPairs) throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        Map<String, Object> vars = new HashMap<>();
        for (int i = 0; i < envPairs.length; i += 2) vars.put(envPairs[i], envPairs[i + 1]);
        env.getPropertySources().addFirst(new MapPropertySource("test-env", vars));
        Properties props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        env.getPropertySources().addLast(new MapPropertySource("application.properties", new HashMap(props)));
        return env;
    }

    @Test
    void packagedConfigHasNoPasswordAndNoBrokenSsl() throws Exception {
        Properties props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        assertEquals("${MESPLAN_DB_PASSWORD}", props.getProperty("spring.datasource.password"));
        for (String key : props.stringPropertyNames()) {
            if (key.toLowerCase().contains("password")) {
                assertTrue(props.getProperty(key).startsWith("${"), key);
            }
        }
        assertNull(props.getProperty("server.ssl.enabled"),
                "ssl.enabled без хранилища ключей не давал службе запуститься");
        assertEquals("none", props.getProperty("spring.jpa.hibernate.ddl-auto"), "схему рабочей базы не трогать");
        assertEquals("8687", props.getProperty("server.port"));
    }

    @Test
    void setStarts() throws Exception {
        StandardEnvironment env = env("MESPLAN_DB_PASSWORD", "a");
        new RequiredSecrets().postProcessEnvironment(env, null);
        assertEquals("a", env.getProperty("spring.datasource.password"));
    }

    @Test
    void missingOrEmptyIsNamed() throws Exception {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new RequiredSecrets().postProcessEnvironment(env(), null));
        assertTrue(e.getMessage().contains("MESPLAN_DB_PASSWORD"), e.getMessage());
        assertEquals(1, RequiredSecrets.missingFor(env("MESPLAN_DB_PASSWORD", "")).size());
    }

    @Test
    void registeredBeforeAnyBean() throws Exception {
        Properties f = PropertiesLoaderUtils.loadProperties(new ClassPathResource("META-INF/spring.factories"));
        assertEquals(RequiredSecrets.class.getName(), f.getProperty("org.springframework.boot.env.EnvironmentPostProcessor"));
    }
}
