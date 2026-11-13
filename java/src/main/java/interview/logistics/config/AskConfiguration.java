package interview.logistics.config;

import interview.logistics.application.AskService;
import interview.logistics.generation.TemplateAnswerGenerator;
import interview.logistics.routing.EvidenceCollector;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AskConfiguration {
    @Bean
    TemplateAnswerGenerator answerGenerator(@Value("${agent.llm:off}") String mode) { return new TemplateAnswerGenerator(mode); }
    @Bean
    AskService askService(EvidenceCollector collector, TemplateAnswerGenerator generator) { return new AskService(collector, generator); }
    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictRequestJson() {
        return builder -> builder.featuresToEnable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
}
