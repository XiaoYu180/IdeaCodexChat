package com.local.codexchat;

import java.util.List;

final class PromptBuilder {
    private PromptBuilder() {
    }

    static String build(String message, List<PromptContext> contexts) {
        return build(message, contexts, false);
    }

    static String build(String message, List<PromptContext> contexts, boolean allowCodeChanges) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are Codex integrated into an IntelliJ IDEA plugin. ");
        prompt.append("Answer in Chinese. Prefer actionable debugging or code-change guidance. ");
        prompt.append("If context includes code, file paths, line numbers, console errors, or image metadata, base the answer on that context. ");
        prompt.append("When images are attached, analyze the visual content directly.\n\n");
        if (allowCodeChanges) {
            prompt.append("You are allowed to edit files in the current IntelliJ IDEA project. ");
            prompt.append("Make the requested code changes directly when the user asks for implementation or fixes. ");
            prompt.append("Do not answer with replacement code unless editing fails. ");
            prompt.append("Do not claim the sandbox is read-only unless the actual command fails with a write-permission error. ");
            prompt.append("Final answer must be concise Chinese only: state the result, changed files, and verification status.\n\n");
        }

        if (!contexts.isEmpty()) {
            prompt.append("## IDE Context\n\n");
            for (PromptContext context : contexts) {
                prompt.append("### ").append(context.title()).append('\n');
                if (context.type() == PromptContext.Type.CODE) {
                    prompt.append("```text\n");
                } else if (context.type() == PromptContext.Type.CONSOLE) {
                    prompt.append("```log\n");
                } else {
                    prompt.append("```text\n");
                }
                prompt.append(context.body().trim()).append('\n');
                prompt.append("```\n\n");
            }
        }

        prompt.append("## User Question\n\n");
        if (message == null || message.trim().isEmpty()) {
            prompt.append("Please analyze the IDE context above.");
        } else {
            prompt.append(message.trim());
        }
        prompt.append('\n');

        return prompt.toString();
    }
}
