package interview.logistics.data;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record DataSnapshot(Path sourceDirectory, Map<String, SopEntry> sops, Map<String, OrderEntry> orders,
                           Map<String, List<String>> synonyms, Map<String, String> sourceHashes) {
    public DataSnapshot {
        sourceDirectory = sourceDirectory.toAbsolutePath().normalize();
        sops = Collections.unmodifiableMap(new TreeMap<>(sops));
        orders = Collections.unmodifiableMap(new TreeMap<>(orders));
        var phrases = new TreeMap<String, List<String>>();
        synonyms.forEach((key, values) -> phrases.put(key, List.copyOf(values)));
        synonyms = Collections.unmodifiableMap(phrases);
        sourceHashes = Collections.unmodifiableMap(new TreeMap<>(sourceHashes));
    }
}
