package com.local.codexchat;

import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

final class ProjectChangeSnapshot {
    private static final int MAX_CAPTURE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DIFF_BYTES = 120 * 1024;

    private final Path root;
    private final Map<String, byte[]> files;

    private ProjectChangeSnapshot(Path root, Map<String, byte[]> files) {
        this.root = root;
        this.files = files;
    }

    static ProjectChangeSnapshot capture(Project project) throws IOException {
        String basePath = project.getBasePath();
        if (basePath == null) {
            throw new IOException("Project base path is unavailable.");
        }

        Path root = Path.of(basePath).toAbsolutePath().normalize();
        Map<String, byte[]> files = new TreeMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && isIgnoredDirectory(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || attrs.size() > MAX_CAPTURE_BYTES) {
                    return FileVisitResult.CONTINUE;
                }

                Path normalized = file.toAbsolutePath().normalize();
                String relative = root.relativize(normalized).toString().replace('\\', '/');
                files.put(relative, Files.readAllBytes(normalized));
                return FileVisitResult.CONTINUE;
            }
        });

        return new ProjectChangeSnapshot(root, files);
    }

    ChangeSet diff(ProjectChangeSnapshot after) {
        List<FileChange> changes = new ArrayList<>();
        TreeSet<String> paths = new TreeSet<>();
        paths.addAll(files.keySet());
        paths.addAll(after.files.keySet());

        for (String path : paths) {
            byte[] before = files.get(path);
            byte[] afterBytes = after.files.get(path);
            if (before == null && afterBytes != null) {
                changes.add(new FileChange(ChangeType.ADDED, path, null, afterBytes));
            } else if (before != null && afterBytes == null) {
                changes.add(new FileChange(ChangeType.DELETED, path, before, null));
            } else if (before != null && !Arrays.equals(before, afterBytes)) {
                changes.add(new FileChange(ChangeType.MODIFIED, path, before, afterBytes));
            }
        }

        return new ChangeSet(root, changes);
    }

    private static boolean isIgnoredDirectory(String name) {
        return name.equals(".git")
                || name.equals(".idea")
                || name.equals(".gradle")
                || name.equals(".mvn")
                || name.equals(".m2")
                || name.equals("target")
                || name.equals("build")
                || name.equals("out")
                || name.equals("node_modules")
                || name.equals("metastore_db");
    }

    enum ChangeType {
        ADDED,
        MODIFIED,
        DELETED
    }

    static final class ChangeSet {
        private final Path root;
        private final List<FileChange> changes;

        private ChangeSet(Path root, List<FileChange> changes) {
            this.root = root;
            this.changes = changes;
        }

        boolean isEmpty() {
            return changes.isEmpty();
        }

        int size() {
            return changes.size();
        }

        String summary() {
            int added = 0;
            int modified = 0;
            int deleted = 0;
            for (FileChange change : changes) {
                if (change.type == ChangeType.ADDED) {
                    added++;
                } else if (change.type == ChangeType.MODIFIED) {
                    modified++;
                } else if (change.type == ChangeType.DELETED) {
                    deleted++;
                }
            }
            return "新增 " + added + "，修改 " + modified + "，删除 " + deleted;
        }

        String toMarkdown() {
            StringBuilder markdown = new StringBuilder();
            markdown.append("本次变更：").append(summary()).append("\n\n");
            for (FileChange change : changes) {
                markdown.append("- ").append(change.marker()).append(" `").append(change.path).append("`\n");
            }
            markdown.append('\n');

            int rendered = 0;
            for (FileChange change : changes) {
                if (rendered >= 12) {
                    markdown.append("\n其余变更省略，避免输出过长。\n");
                    break;
                }
                markdown.append("### ").append(change.marker()).append(' ').append(change.path).append("\n\n");
                markdown.append("```diff\n");
                markdown.append(change.diffText());
                markdown.append("\n```\n\n");
                rendered++;
            }
            return markdown.toString();
        }

        void restore() throws IOException {
            for (FileChange change : changes) {
                Path target = root.resolve(change.path).toAbsolutePath().normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Refusing to restore outside project: " + change.path);
                }

                if (change.type == ChangeType.ADDED) {
                    Files.deleteIfExists(target);
                    continue;
                }

                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.write(target, change.before);
            }
        }
    }

    private static final class FileChange {
        private final ChangeType type;
        private final String path;
        private final byte[] before;
        private final byte[] after;

        private FileChange(ChangeType type, String path, byte[] before, byte[] after) {
            this.type = type;
            this.path = path;
            this.before = before;
            this.after = after;
        }

        private String marker() {
            if (type == ChangeType.ADDED) {
                return "A";
            }
            if (type == ChangeType.DELETED) {
                return "D";
            }
            return "M";
        }

        private String diffText() {
            if (!isText(before) || !isText(after)) {
                return marker() + " " + path + "\n(binary or large file diff omitted)";
            }

            String beforeText = before == null ? "" : new String(before, StandardCharsets.UTF_8);
            String afterText = after == null ? "" : new String(after, StandardCharsets.UTF_8);
            if (beforeText.length() + afterText.length() > MAX_DIFF_BYTES) {
                return marker() + " " + path + "\n(diff omitted because file is large)";
            }

            if (type == ChangeType.ADDED) {
                return prefixLines("+", afterText);
            }
            if (type == ChangeType.DELETED) {
                return prefixLines("-", beforeText);
            }
            return compactModifiedDiff(beforeText, afterText);
        }

        private static boolean isText(byte[] bytes) {
            if (bytes == null) {
                return true;
            }
            for (byte value : bytes) {
                if (value == 0) {
                    return false;
                }
            }
            return true;
        }

        private static String prefixLines(String prefix, String text) {
            StringBuilder result = new StringBuilder();
            String[] lines = text.split("\n", -1);
            int limit = Math.min(lines.length, 240);
            for (int i = 0; i < limit; i++) {
                result.append(prefix).append(lines[i]).append('\n');
            }
            if (lines.length > limit) {
                result.append("... omitted ").append(lines.length - limit).append(" lines\n");
            }
            return result.toString().trim();
        }

        private static String compactModifiedDiff(String beforeText, String afterText) {
            String[] beforeLines = beforeText.split("\n", -1);
            String[] afterLines = afterText.split("\n", -1);
            int prefix = 0;
            while (prefix < beforeLines.length
                    && prefix < afterLines.length
                    && beforeLines[prefix].equals(afterLines[prefix])) {
                prefix++;
            }

            int suffix = 0;
            while (suffix + prefix < beforeLines.length
                    && suffix + prefix < afterLines.length
                    && beforeLines[beforeLines.length - 1 - suffix].equals(afterLines[afterLines.length - 1 - suffix])) {
                suffix++;
            }

            int contextStart = Math.max(0, prefix - 3);
            int beforeEnd = Math.min(beforeLines.length, beforeLines.length - suffix + 3);
            int afterEnd = Math.min(afterLines.length, afterLines.length - suffix + 3);

            StringBuilder result = new StringBuilder();
            if (contextStart > 0) {
                result.append("...\n");
            }
            for (int i = contextStart; i < prefix; i++) {
                result.append(' ').append(beforeLines[i]).append('\n');
            }
            for (int i = prefix; i < beforeLines.length - suffix; i++) {
                result.append('-').append(beforeLines[i]).append('\n');
            }
            for (int i = prefix; i < afterLines.length - suffix; i++) {
                result.append('+').append(afterLines[i]).append('\n');
            }
            int suffixStartBefore = Math.max(prefix, beforeLines.length - suffix);
            for (int i = suffixStartBefore; i < beforeEnd; i++) {
                result.append(' ').append(beforeLines[i]).append('\n');
            }
            if (beforeEnd < beforeLines.length || afterEnd < afterLines.length) {
                result.append("...\n");
            }
            return result.toString().trim();
        }
    }
}
