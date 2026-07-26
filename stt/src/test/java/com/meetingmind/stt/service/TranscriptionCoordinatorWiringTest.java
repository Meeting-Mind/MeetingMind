package com.meetingmind.stt.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;

class TranscriptionCoordinatorWiringTest {

    /**
     * 생성자가 둘 이상인 클래스는 @Autowired 지정이 없으면 컨테이너가 no-arg
     * 생성자로 폴백해 기동에 실패한다. 컨테이너와 동일한 후보 결정 로직으로
     * 주입 생성자가 정확히 하나 선택되는지 고정한다.
     */
    @Test
    void containerResolvesExactlyOneInjectionConstructor() {
        Constructor<?>[] candidates = new AutowiredAnnotationBeanPostProcessor()
                .determineCandidateConstructors(TranscriptionCoordinator.class, "transcriptionCoordinator");

        assertThat(candidates).hasSize(1);
        assertThat(candidates[0].getParameterCount()).isEqualTo(3);
    }
}
