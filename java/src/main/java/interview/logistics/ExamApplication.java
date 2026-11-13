package interview.logistics;

import interview.logistics.data.DataLoader;
import interview.logistics.data.DataValidationException;
import interview.logistics.retrieval.IndexStore;
import interview.logistics.retrieval.SopSearchService;
import interview.logistics.data.JsonFiles;
import interview.logistics.order.OrderQueryTool;
import interview.logistics.routing.EvidenceCollector;
import interview.logistics.routing.QuestionRouter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.nio.file.Path;
import java.util.Arrays;

@SpringBootApplication
public class ExamApplication {
    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--rebuild-index")) {
            try {
                if (args.length != 1) throw new DataValidationException("--rebuild-index accepts paths via DATA_DIR and INDEX_PATH only");
                var data = new DataLoader().load(Path.of(environment("DATA_DIR", "data")));
                var index = new IndexStore().rebuild(Path.of(environment("INDEX_PATH", "index/sop-index.json")), data);
                System.out.println("SOP index rebuilt: " + index.documentCount() + " documents, " + index.postings().size() + " terms");
            } catch (DataValidationException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
            return; // CLI never starts Spring or binds a port.
        }
        if (args.length > 0 && (args[0].equals("--search-sop") || args[0].equals("--collect-evidence"))) {
            try {
                if (args.length != 2) throw new IllegalArgumentException(args[0] + " requires one question argument");
                var data = new DataLoader().load(Path.of(environment("DATA_DIR", "data")));
                var index = new IndexStore().load(Path.of(environment("INDEX_PATH", "index/sop-index.json")), data);
                var search = new SopSearchService(data, index);
                Object result = args[0].equals("--search-sop") ? search.search(args[1])
                        : new EvidenceCollector(new QuestionRouter(), new OrderQueryTool(data), search).collect(args[1]);
                System.out.println(JsonFiles.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
            } catch (DataValidationException | IllegalArgumentException | java.io.IOException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
            return;
        }
        SpringApplication.run(ExamApplication.class, args);
    }

    private static String environment(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }
}
