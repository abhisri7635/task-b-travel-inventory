package com.example.navan.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

    /**
     * All ordinary Jackson tuning (big-decimal floats, plain BigDecimal output, date
     * handling, unknown-property leniency, null inclusion) is declared in
     * application.yml under {@code spring.jackson.*} and applied by Boot's builder.
     *
     * The single thing that has no YAML equivalent is the node factory: the default
     * one strips trailing zeros when parsing numbers into a JsonNode (250.00 -> 2.5E+2),
     * which mangles money. {@code withExactBigDecimals(true)} preserves scale.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.build();
        mapper.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        return mapper;
    }
}
