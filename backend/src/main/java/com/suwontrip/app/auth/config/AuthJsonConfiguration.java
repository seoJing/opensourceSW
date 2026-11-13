package com.suwontrip.app.auth.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthJsonConfiguration {

    // Spring이 구성하는 기본 ObjectMapper 전체에 적용
    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictRequestJson() {
        return builder -> {

            // 동일한 JSON 키를 두 번 보내거나 DTO에 없는 필드를 보내면 요청을 거부
            // @JsonIgnore로 입력을 금지한 필드도 오류로 처리
            builder.featuresToEnable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION,
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

            // Jackson의 기본 타입 변환으로 숫자나 불리언이 문자열 필드에 들어가는 일을 막음
            // loginId와 password는 JSON 문자열이어야 하며, 변환 실패는 잘못된 요청으로 처리
            builder.postConfigurer(mapper -> mapper.coercionConfigFor(String.class)
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
        };
    }
}
