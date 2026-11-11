package com.devisri.reposcout.citations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Which line ranges of which files the agent has really read during one question. */
public class ReadRanges {

    private final Map<String, List<int[]>> byFile = new HashMap<>();

    public synchronized void add(String path, int from, int to) {
        byFile.computeIfAbsent(path, p -> new ArrayList<>()).add(new int[]{from, to});
    }

    /** True if every line from {@code from} to {@code to} was covered by some read. */
    public synchronized boolean covers(String path, int from, int to) {
        List<int[]> ranges = byFile.get(path);
        if (ranges == null) {
            return false;
        }
        for (int line = from; line <= to; line++) {
            final int l = line;
            if (ranges.stream().noneMatch(r -> r[0] <= l && l <= r[1])) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean readAny(String path) {
        return byFile.containsKey(path);
    }

    /**
     * The one file the agent read whose path ends with {@code /name}, e.g. {@code application.properties}
     * matches {@code src/main/resources/application.properties}. Empty if none or more than one match.
     */
    public synchronized java.util.Optional<String> uniqueReadFileEndingWith(String name) {
        List<String> matches = byFile.keySet().stream().filter(p -> p.endsWith("/" + name)).toList();
        return matches.size() == 1 ? java.util.Optional.of(matches.get(0)) : java.util.Optional.empty();
    }
}
