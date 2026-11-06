package com.devisri.reposcout.repo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Unpacks a GitHub repository zip safely.
 *
 * <ul>
 *   <li>GitHub puts everything inside one top folder ({@code repo-main/}); that folder is removed.</li>
 *   <li>Entries that would land outside the target folder ("zip slip") stop the whole unpack.</li>
 *   <li>Limits on file count and total unpacked bytes are counted from the real bytes written,
 *       not from the sizes the zip claims, so a fake header cannot get around them.</li>
 *   <li>Single files over the size limit are skipped instead of failing the repo.</li>
 * </ul>
 */
public final class ZipExtractor {

    private final long maxUnzippedBytes;
    private final long maxFileBytes;
    private final int maxFiles;

    public ZipExtractor(long maxUnzippedBytes, long maxFileBytes, int maxFiles) {
        this.maxUnzippedBytes = maxUnzippedBytes;
        this.maxFileBytes = maxFileBytes;
        this.maxFiles = maxFiles;
    }

    /** How many files were written and how many were skipped for being too big. */
    public record Result(int filesWritten, int filesSkipped, long bytesWritten) {
    }

    public Result extract(Path zip, Path targetDir) throws IOException {
        Path target = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(target);

        int written = 0;
        int skipped = 0;
        long totalBytes = 0;
        byte[] buffer = new byte[8192];

        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String relative = stripTopFolder(entry.getName());
                if (relative.isEmpty()) {
                    continue;
                }

                Path out = target.resolve(relative).normalize();
                if (!out.startsWith(target) || out.equals(target)) {
                    throw new RepoLoadException("The repository zip contains an unsafe path: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                if (entry.getSize() > maxFileBytes) {
                    skipped++;
                    continue;
                }
                if (written >= maxFiles) {
                    throw new RepoLoadException("The repository has more than " + maxFiles + " files.");
                }

                Files.createDirectories(out.getParent());
                long fileBytes = 0;
                boolean tooBig = false;
                try (InputStream in = zipFile.getInputStream(entry);
                     OutputStream os = Files.newOutputStream(out)) {
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (fileBytes + n > maxFileBytes) {
                            tooBig = true;
                            break;
                        }
                        if (totalBytes + fileBytes + n > maxUnzippedBytes) {
                            throw new RepoLoadException("The repository is too large once unpacked.");
                        }
                        os.write(buffer, 0, n);
                        fileBytes += n;
                    }
                }
                if (tooBig) {
                    // The zip header claimed a smaller size than the real content; drop the file.
                    Files.deleteIfExists(out);
                    skipped++;
                } else {
                    totalBytes += fileBytes;
                    written++;
                }
            }
        }
        return new Result(written, skipped, totalBytes);
    }

    /** {@code repo-main/src/App.java} becomes {@code src/App.java}; the top folder itself becomes "". */
    static String stripTopFolder(String entryName) {
        String name = entryName.replace('\\', '/');
        int slash = name.indexOf('/');
        return slash < 0 ? "" : name.substring(slash + 1);
    }
}
