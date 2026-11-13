package interview.logistics.config;

import interview.logistics.data.DataSnapshot;
import interview.logistics.retrieval.IndexStore;
import interview.logistics.retrieval.SopIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class DataConfigurationTest {
    @TempDir Path directory;

    private ApplicationContextRunner runner(Path data, Path index) {
        return new ApplicationContextRunner().withUserConfiguration(DataConfiguration.class)
                .withPropertyValues("assistant.data-dir=" + data, "assistant.index-path=" + index);
    }

    @Test
    void refusesToInitializeWhenSourceDataIsMissing() {
        runner(directory, directory.resolve("index.json")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(java.nio.file.NoSuchFileException.class);
        });
    }

    @Test
    void refusesToInitializeWithMissingIndexButLoadsValidatedSnapshots() {
        Path index = directory.resolve("index.json");
        runner(Path.of("data"), index).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("rebuild-index.sh");
        });
        var data = new interview.logistics.data.DataLoader().load(Path.of("data"));
        new IndexStore().rebuild(index, data);
        runner(Path.of("data"), index).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DataSnapshot.class).hasSingleBean(SopIndex.class);
            assertThat(context.getBean(SopIndex.class).documentCount()).isEqualTo(47);
        });
    }
}
