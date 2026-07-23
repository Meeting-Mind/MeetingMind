package com.meetingmind.stt.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DotenvConfig {

    private static final List<Path> DOTENV_PATHS = List.of(
            Path.of("stt", ".env"),
            Path.of(".env"),
            Path.of("..", ".env")
    );

    private DotenvConfig() {
    }

    public static String require(String... keys) {
        Map<String, String> dotenv = loadDotEnv();

        for (String key : keys) {
            String systemProperty = System.getProperty(key);

            if (systemProperty != null && !systemProperty.isBlank()) {
                return systemProperty;
            }

            String value = System.getenv(key);

            if (value != null && !value.isBlank()) {
                return value;
            }

            String fileValue = dotenv.get(key);
            if (fileValue != null && !fileValue.isBlank()) {
                return fileValue;
            }
        }

        if (keys.length == 1) {
            throw new IllegalStateException(keys[0] + " 환경변수가 설정되지 않았습니다.");
        }

        throw new IllegalStateException(String.join(" 또는 ", keys) + " 환경변수가 설정되지 않았습니다.");
    }

    public static Optional<String> optional(String key) {
        Map<String, String> dotenv = loadDotEnv();
        String systemProperty = System.getProperty(key);
        if (systemProperty != null && !systemProperty.isBlank()) {
            return Optional.of(systemProperty);
        }
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return Optional.of(value);
        }
        String fileValue = dotenv.get(key);
        if (fileValue != null && !fileValue.isBlank()) {
            return Optional.of(fileValue);
        }
        return Optional.empty();
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new HashMap<>();

        for (Path dotenvPath : DOTENV_PATHS) {
            if (!Files.exists(dotenvPath)) {
                continue;
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
                    values.putIfAbsent(key, stripQuotes(value));
                }
            } catch (IOException exception) {
                throw new IllegalStateException(dotenvPath + " 파일을 읽는 중 오류가 발생했습니다.", exception);
            }
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
