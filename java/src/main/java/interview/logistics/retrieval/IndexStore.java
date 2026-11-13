package interview.logistics.retrieval;

import interview.logistics.data.DataSnapshot;
import interview.logistics.data.DataValidationException;
import interview.logistics.data.JsonFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class IndexStore {
    public SopIndex load(Path file, DataSnapshot data) {
        try {
            var index = JsonFiles.mapper().readValue(Files.readAllBytes(file), SopIndex.class);
            if (index == null || !index.equals(new IndexBuilder().build(data))) {
                throw new DataValidationException("SOP index is stale or inconsistent with data/configuration. " + rebuildHelp());
            }
            return index;
        } catch (IOException | IllegalArgumentException | NullPointerException e) {
            throw new DataValidationException("SOP index is missing or corrupt. " + rebuildHelp(), e);
        }
    }

    public SopIndex rebuild(Path file, DataSnapshot data) {
        SopIndex index = new IndexBuilder().build(data);
        Path target = file.toAbsolutePath().normalize();
        Path temporary = null;
        try {
            for (String name : data.sourceHashes().keySet()) {
                Path source = data.sourceDirectory().resolve(name);
                if (target.equals(source) || (Files.exists(target) && Files.isSameFile(target, source))) {
                    throw new DataValidationException("Index path must not overwrite source data: " + name);
                }
            }
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".sop-index-", ".tmp");
            byte[] bytes = JsonFiles.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(index);
            Files.write(temporary, bytes);
            // Read back and validate before replacing the existing index.
            load(temporary, data);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return index;
        } catch (IOException e) {
            // No non-atomic fallback: an unsupported filesystem must not risk the previous index.
            throw new DataValidationException("Cannot atomically rebuild SOP index", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { /* Replacement failure remains the primary error. */ }
            }
        }
    }

    private String rebuildHelp() {
        return "Run ./scripts/rebuild-index.sh using the same DATA_DIR and INDEX_PATH.";
    }
}
