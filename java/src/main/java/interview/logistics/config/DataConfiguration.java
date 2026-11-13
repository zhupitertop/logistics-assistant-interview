package interview.logistics.config;

import interview.logistics.data.DataLoader;
import interview.logistics.data.DataSnapshot;
import interview.logistics.retrieval.IndexStore;
import interview.logistics.retrieval.SopIndex;
import interview.logistics.retrieval.SopSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Path;
import interview.logistics.order.OrderQueryTool;
import interview.logistics.routing.QuestionRouter;
import interview.logistics.routing.EvidenceCollector;

@Configuration(proxyBeanMethods = false)
public class DataConfiguration {
    @Bean
    OrderQueryTool orderQueryTool(DataSnapshot data) { return new OrderQueryTool(data); }

    @Bean
    QuestionRouter questionRouter() { return new QuestionRouter(); }

    @Bean
    EvidenceCollector evidenceCollector(QuestionRouter router, OrderQueryTool orders, SopSearchService sops) {
        return new EvidenceCollector(router, orders, sops);
    }

    @Bean
    DataSnapshot dataSnapshot(@Value("${assistant.data-dir}") String directory) {
        return new DataLoader().load(Path.of(directory));
    }

    @Bean
    SopSearchService sopSearchService(DataSnapshot data, SopIndex index) {
        return new SopSearchService(data, index);
    }

    @Bean
    SopIndex sopIndex(DataSnapshot data, @Value("${assistant.index-path}") String file) {
        return new IndexStore().load(Path.of(file), data);
    }
}
