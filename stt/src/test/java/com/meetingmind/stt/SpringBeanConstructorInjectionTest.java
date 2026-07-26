package com.meetingmind.stt;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

/**
 * Spring bean이 생성자 주입으로 조립될 수 있는지 검사한다.
 *
 * <p>생성자가 둘 이상인데 어느 것도 {@code @Autowired}가 아니고 기본 생성자도 없으면, Spring은
 * 주입 대상을 고를 수 없어 기본 생성자를 찾다가 기동에 실패한다. 실제로
 * {@code TranscriptionCoordinator}가 이 상태였고 STT 서비스는 아예 뜨지 못했다.
 *
 * <p>이 검사를 컨텍스트 기동 테스트 대신 reflection으로 하는 이유가 있다. STT는 JPA를 쓰므로
 * {@code @SpringBootTest}로 컨텍스트를 띄우려면 실제 데이터베이스가 필요하고, CI STT job에는
 * Postgres service가 없다. DB를 붙이면 CI 실행 시간이 늘어난다. 반면 이 검사는 DB 없이
 * 밀리초 단위로 끝나면서 **모든 bean**을 한 번에 덮는다.
 *
 * <p>한계: 조립 가능 여부만 본다. 설정값 바인딩이나 실제 기동 성공까지 보장하지는 않는다.
 */
class SpringBeanConstructorInjectionTest {

    private static final String BASE_PACKAGE = "com.meetingmind.stt";

    @Test
    void everySpringBeanHasAResolvableInjectionConstructor() throws Exception {
        List<Class<?>> beans = scanBeanClasses();

        // 스캔이 실패해 빈 목록이면 아래 검사가 공허하게 통과한다. 먼저 막는다.
        assertThat(beans)
                .as("Spring bean 클래스를 하나도 찾지 못했다면 이 검사는 아무것도 검증하지 않는다")
                .isNotEmpty();

        List<String> unresolvable = new ArrayList<>();
        for (Class<?> type : beans) {
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            if (constructors.length <= 1) {
                continue;
            }
            boolean hasAutowired = Arrays.stream(constructors)
                    .anyMatch(constructor -> constructor.isAnnotationPresent(Autowired.class));
            boolean hasNoArg = Arrays.stream(constructors)
                    .anyMatch(constructor -> constructor.getParameterCount() == 0);
            if (!hasAutowired && !hasNoArg) {
                unresolvable.add(type.getName() + " (생성자 " + constructors.length + "개)");
            }
        }

        assertThat(unresolvable)
                .as("생성자가 둘 이상이면 @Autowired로 주입 대상을 지정하거나 기본 생성자를 둬야 한다")
                .isEmpty();
    }

    private List<Class<?>> scanBeanClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        // @Service, @Repository, @RestController 등은 모두 @Component를 메타 애노테이션으로 갖는다.
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        List<Class<?>> beans = new ArrayList<>();
        for (var definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            String className = definition.getBeanClassName();
            if (className != null) {
                beans.add(Class.forName(className));
            }
        }
        return beans;
    }
}
