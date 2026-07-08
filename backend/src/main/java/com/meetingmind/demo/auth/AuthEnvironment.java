package com.meetingmind.demo.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AuthEnvironment {

    private static final Path DOTENV_PATH = Path.of(".env");
    private final ConfigProvider configProvider;

    public AuthEnvironment() {
        this(new EnvironmentConfigProvider(DOTENV_PATH));
    }

    AuthEnvironment(ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    String get(String key) {
        return configProvider.get(key);
    }

    String requireConfig(String... keys) {
        for (String key : keys) {
            String value = get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        if (keys.length == 1) {
            throw new IllegalStateException(keys[0] + " 환경변수가 설정되지 않았습니다.");
        }

        throw new IllegalStateException(String.join(" 또는 ", keys) + " 환경변수가 설정되지 않았습니다.");
    }

    interface ConfigProvider {
        String get(String key);
    }

    private static final class EnvironmentConfigProvider implements ConfigProvider {
        private final Map<String, String> dotenv;

        private EnvironmentConfigProvider(Path dotenvPath) {
            this.dotenv = loadDotEnv(dotenvPath);
        }

        @Override
        public String get(String key) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                return value;
            }

            return dotenv.get(key);
        }

        private static Map<String, String> loadDotEnv(Path dotenvPath) {
            Map<String, String> values = new HashMap<>();

            if (!Files.exists(dotenvPath)) {
                return values;
            }

            try {
                List<String> lines = Files.readAllLines(dotenvPath, StandardCharsets.UTF_8);

                for (String line : lines) {
                    String trimmed = line.trim();

                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }

                    int separatorIndex = trimmed.indexOf('=');
                    String key = trimmed.substring(0, separatorIndex).trim();
                    String value = trimmed.substring(separatorIndex + 1).trim();
                    values.put(key, stripQuotes(value));
                }
            } catch (IOException exception) {
                throw new IllegalStateException(".env 파일을 읽는 중 오류가 발생했습니다.", exception);
            }

            return values;
        }

        private static String stripQuotes(String value) {
            if (value.length() >= 2) {
                boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
                boolean singleQuoted = value.startsWith("'") && value.endsWith("'");

                if (doubleQuoted || singleQuoted) {
                    return value.substring(1, value.length() - 1);
                }
            }

            return value;
        }
    }
}
