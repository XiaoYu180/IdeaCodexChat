package com.local.codexchat;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class CodexCliService {
    private static final long TIMEOUT_SECONDS = 90;
    private static final String WINDOWS_CODEX_CMD = "C:\\nvm4w\\nodejs\\codex.cmd";

    private final Project project;

    CodexCliService(Project project) {
        this.project = project;
    }

    Result ask(String prompt, String model, String reasoningEffort) {
        return ask(prompt, model, reasoningEffort, new ArrayList<>());
    }

    Result ask(String prompt, String model, String reasoningEffort, List<String> imagePaths) {
        return ask(prompt, model, reasoningEffort, imagePaths, false);
    }

    Result ask(String prompt, String model, String reasoningEffort, List<String> imagePaths, boolean allowWorkspaceWrites) {
        File workingDirectory = resolveWorkingDirectory();
        Path lastMessageFile = null;

        try {
            lastMessageFile = Files.createTempFile("codex-chat-last-message-", ".txt");
            List<String> command = buildCommand(workingDirectory, lastMessageFile, model, reasoningEffort, imagePaths, allowWorkspaceWrites);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDirectory);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(prompt);
                writer.flush();
            }

            StringBuilder output = new StringBuilder();
            Thread outputReader = new Thread(() -> readOutput(process, output), "Codex CLI output reader");
            outputReader.setDaemon(true);
            outputReader.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                outputReader.join(TimeUnit.SECONDS.toMillis(2));
                return Result.failure("Codex timed out. Reduce the attached context and try again.");
            }
            outputReader.join(TimeUnit.SECONDS.toMillis(2));

            int exitCode = process.exitValue();
            String response = readLastMessage(lastMessageFile);
            if (exitCode != 0) {
                return Result.failure("Codex CLI failed with exit code " + exitCode + ".\n\n" + output);
            }

            if (response.isEmpty()) {
                return Result.failure("Codex CLI did not return a final message.\n\n" + output);
            }
            return Result.success(response);
        } catch (IOException exception) {
            return Result.failure("Unable to start Codex CLI. Confirm `codex --version` works in a terminal.\n\n"
                    + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.failure("Codex request was interrupted.");
        } finally {
            if (lastMessageFile != null) {
                try {
                    Files.deleteIfExists(lastMessageFile);
                } catch (IOException ignored) {
                    // Temporary response files are best-effort cleanup.
                }
            }
        }
    }

    List<ModelInfo> listModels() {
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(resolveCodexCommand());
        command.add("debug");
        command.add("models");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            Thread outputReader = new Thread(() -> readOutput(process, output), "Codex model catalog reader");
            outputReader.setDaemon(true);
            outputReader.start();

            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                outputReader.join(TimeUnit.SECONDS.toMillis(1));
                return fallbackModels();
            }
            outputReader.join(TimeUnit.SECONDS.toMillis(1));

            if (process.exitValue() != 0) {
                return fallbackModels();
            }
            return parseModels(output.toString());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return fallbackModels();
        }
    }

    private List<String> buildCommand(File workingDirectory, Path lastMessageFile, String model, String reasoningEffort, List<String> imagePaths, boolean allowWorkspaceWrites) {
        List<String> codexArgs = new ArrayList<>();
        codexArgs.add(resolveCodexCommand());
        codexArgs.add("exec");
        codexArgs.add("--skip-git-repo-check");
        codexArgs.add("--color");
        codexArgs.add("never");
        if (imagePaths != null) {
            for (String imagePath : imagePaths) {
                if (imagePath != null && !imagePath.trim().isEmpty() && new File(imagePath).isFile()) {
                    codexArgs.add("--image");
                    codexArgs.add(imagePath.trim());
                }
            }
        }
        if (model != null && !model.trim().isEmpty()) {
            codexArgs.add("-m");
            codexArgs.add(model.trim());
        }
        if (reasoningEffort != null && !reasoningEffort.trim().isEmpty()) {
            codexArgs.add("-c");
            codexArgs.add("model_reasoning_effort=\"" + reasoningEffort.trim() + "\"");
        }
        codexArgs.add("--sandbox");
        codexArgs.add(allowWorkspaceWrites ? "workspace-write" : "read-only");
        codexArgs.add("-C");
        codexArgs.add(workingDirectory.getAbsolutePath());
        codexArgs.add("--output-last-message");
        codexArgs.add(lastMessageFile.toAbsolutePath().toString());
        codexArgs.add("-");

        if (isWindows()) {
            List<String> command = new ArrayList<>();
            command.add("cmd.exe");
            command.add("/c");
            command.addAll(codexArgs);
            return command;
        }

        return codexArgs;
    }

    private String resolveCodexCommand() {
        if (isWindows()) {
            File explicit = new File(WINDOWS_CODEX_CMD);
            if (explicit.isFile()) {
                return explicit.getAbsolutePath();
            }
            return "codex.cmd";
        }
        return "codex";
    }

    private File resolveWorkingDirectory() {
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir != null) {
            return new File(projectDir.getPath());
        }

        String basePath = project.getBasePath();
        if (basePath != null) {
            return new File(basePath);
        }

        return new File(System.getProperty("user.home"));
    }

    private void readOutput(Process process, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (output) {
                    output.append(line).append('\n');
                }
            }
        } catch (IOException exception) {
            synchronized (output) {
                output.append("\n[Failed to read Codex output: ").append(exception.getMessage()).append("]\n");
            }
        }
    }

    private String readLastMessage(Path lastMessageFile) throws IOException {
        if (!Files.exists(lastMessageFile)) {
            return "";
        }
        return new String(Files.readAllBytes(lastMessageFile), StandardCharsets.UTF_8).trim();
    }

    private List<ModelInfo> parseModels(String json) {
        List<ModelInfo> models = new ArrayList<>();
        int index = 0;
        while (true) {
            int slugKey = json.indexOf("\"slug\"", index);
            if (slugKey < 0) {
                break;
            }
            String slug = readJsonStringValue(json, slugKey);
            int displayKey = json.indexOf("\"display_name\"", slugKey);
            int nextSlug = json.indexOf("\"slug\"", slugKey + 6);
            String displayName = displayKey > 0 && (nextSlug < 0 || displayKey < nextSlug)
                    ? readJsonStringValue(json, displayKey)
                    : slug;
            if (slug != null && !slug.trim().isEmpty() && !containsModel(models, slug)) {
                models.add(new ModelInfo(slug, displayName == null || displayName.trim().isEmpty() ? slug : displayName));
            }
            index = slugKey + 6;
        }
        return models.isEmpty() ? fallbackModels() : models;
    }

    private String readJsonStringValue(String json, int keyIndex) {
        int colon = json.indexOf(':', keyIndex);
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int i = start + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaping) {
                value.append(ch);
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else if (ch == '"') {
                return value.toString();
            } else {
                value.append(ch);
            }
        }
        return null;
    }

    private boolean containsModel(List<ModelInfo> models, String slug) {
        for (ModelInfo model : models) {
            if (model.slug().equals(slug)) {
                return true;
            }
        }
        return false;
    }

    private List<ModelInfo> fallbackModels() {
        List<ModelInfo> models = new ArrayList<>();
        models.add(new ModelInfo("gpt-5.5", "GPT-5.5"));
        return models;
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    static final class Result {
        private final boolean success;
        private final String output;

        private Result(boolean success, String output) {
            this.success = success;
            this.output = output;
        }

        static Result success(String output) {
            return new Result(true, output);
        }

        static Result failure(String output) {
            return new Result(false, output);
        }

        boolean isSuccess() {
            return success;
        }

        String output() {
            return output;
        }
    }

    static final class ModelInfo {
        private final String slug;
        private final String displayName;

        private ModelInfo(String slug, String displayName) {
            this.slug = slug;
            this.displayName = displayName;
        }

        String slug() {
            return slug;
        }

        String displayName() {
            return displayName;
        }
    }
}
