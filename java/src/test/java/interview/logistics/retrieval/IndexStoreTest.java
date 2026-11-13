package interview.logistics.retrieval;

import interview.logistics.data.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class IndexStoreTest {
    @TempDir Path directory;
    private final IndexStore store = new IndexStore();
    private final DataLoader loader = new DataLoader();
    private Path dataDir;
    private Path index;

    @BeforeEach
    void fixtures() throws Exception {
        dataDir = Files.createDirectory(directory.resolve("data"));
        index = directory.resolve("index/sop-index.json");
        for (String name : List.of("sop.json", "orders.json", "synonyms.json")) {
            Files.copy(Path.of("data", name), dataDir.resolve(name));
        }
    }

    @Test
    void rebuildIsByteReproducibleAndContainsNoOrdersOrCopiedFullText() throws Exception {
        var data = loader.load(dataDir);
        var built = store.rebuild(index, data);
        byte[] first = Files.readAllBytes(index);
        store.rebuild(index, data);
        assertThat(Files.readAllBytes(index)).isEqualTo(first);
        assertThat(store.load(index, data)).isEqualTo(built);
        assertThat(built.documentCount()).isEqualTo(47);
        String content = Files.readString(index);
        assertThat(content).doesNotContain("HD202611001", "warehouse", "order_status");
        assertThat(content).doesNotContain(data.sops().get("INV-01").text());
        assertThatThrownBy(() -> built.postings().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void frequenciesAndLengthsSupportBm25WithoutIdScoring() {
        var data = new DataSnapshot(dataDir, Map.of("TST-01", new SopEntry("TST-01", "盘点", "盘点盘点 A1")),
                Map.of(), Map.of(), Map.of("sop.json", "s", "synonyms.json", "q"));
        var built = new IndexBuilder().build(data);
        assertThat(built.postings().get("盘点")).containsExactly(new SopIndex.Posting("TST-01", 1, 2));
        assertThat(built.postings().get("点盘")).containsExactly(new SopIndex.Posting("TST-01", 0, 1));
        assertThat(built.documents()).containsExactly(new SopIndex.Document("TST-01", 1, 4));
        assertThat(built.averageDocumentLength()).isEqualTo(5);
        assertThat(built.postings()).doesNotContainKey("TST-01");
    }

    @Test
    void rejectsMissingBrokenAndTamperedIndex() throws Exception {
        var data = loader.load(dataDir);
        assertThatThrownBy(() -> store.load(index, data)).hasMessageContaining("rebuild-index.sh");
        store.rebuild(index, data);
        var original = (ObjectNode) JsonFiles.mapper().readTree(index.toFile());
        for (String field : List.of("schemaVersion", "documentCount")) {
            var changed = original.deepCopy().put(field, 999);
            JsonFiles.mapper().writeValue(index.toFile(), changed);
            assertThatThrownBy(() -> store.load(index, data)).isInstanceOf(DataValidationException.class);
        }
        var changed = original.deepCopy();
        var firstTerm = changed.get("postings").fields().next().getValue();
        ((ObjectNode) firstTerm.get(0)).put("bodyFrequency", 999);
        JsonFiles.mapper().writeValue(index.toFile(), changed);
        assertThatThrownBy(() -> store.load(index, data)).hasMessageContaining("inconsistent");
        for (String bad : List.of("{", "null", "{}", "[]")) {
            Files.writeString(index, bad);
            assertThatThrownBy(() -> store.load(index, data)).isInstanceOf(DataValidationException.class);
        }
    }

    @Test
    void sopAndSynonymChangesInvalidateIndexWhileOrderChangesDoNot() throws Exception {
        store.rebuild(index, loader.load(dataDir));
        Files.writeString(dataDir.resolve("orders.json"), Files.readString(dataDir.resolve("orders.json")).replace("待审核", "核实中"));
        assertThat(store.load(index, loader.load(dataDir)).documentCount()).isEqualTo(47);
        for (String name : List.of("sop.json", "synonyms.json")) {
            var before = loader.load(dataDir);
            store.rebuild(index, before);
            Files.writeString(dataDir.resolve(name), Files.readString(dataDir.resolve(name)) + "\n");
            assertThatThrownBy(() -> store.load(index, loader.load(dataDir))).hasMessageContaining("stale");
        }
    }

    @Test
    void cannotOverwriteSourceDataEvenThroughSymlink() throws Exception {
        var data = loader.load(dataDir);
        byte[] original = Files.readAllBytes(dataDir.resolve("sop.json"));
        assertThatThrownBy(() -> store.rebuild(dataDir.resolve("sop.json"), data)).hasMessageContaining("overwrite source");
        Path alias = directory.resolve("alias.json");
        Files.createSymbolicLink(alias, dataDir.resolve("sop.json"));
        assertThatThrownBy(() -> store.rebuild(alias, data)).hasMessageContaining("overwrite source");
        assertThat(Files.readAllBytes(dataDir.resolve("sop.json"))).isEqualTo(original);
    }

    @Test
    void failedReplacementLeavesPreviousTargetAndCleansTemporaryFiles() throws Exception {
        Path targetDirectory = Files.createDirectory(directory.resolve("existing"));
        Files.writeString(targetDirectory.resolve("keep.txt"), "original");
        assertThatThrownBy(() -> store.rebuild(targetDirectory, loader.load(dataDir))).isInstanceOf(DataValidationException.class);
        assertThat(Files.readString(targetDirectory.resolve("keep.txt"))).isEqualTo("original");
        try (var paths = Files.list(directory)) {
            assertThat(paths.filter(path -> path.getFileName().toString().startsWith(".sop-index-")).toList()).isEmpty();
        }
    }

    @Test
    void tokenizerRespectsUnicodeAndSeparators() {
        var analyzer = new TextAnalyzer();
        assertThat(analyzer.analyze("盘点，少件 ＡＢ１２ 𠀀库"))
                .containsExactly("盘点", "少件", "ab12", "𠀀库");
        assertThat(analyzer.analyze("盘 点")).containsExactly("盘", "点");
    }
}
