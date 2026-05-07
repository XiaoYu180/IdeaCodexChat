package com.local.codexchat;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import javax.swing.JComponent;
import javax.swing.text.JTextComponent;

final class ConsoleContextCollector {
    private static final int MAX_CONSOLE_CHARS = 24_000;
    private static final int TAIL_CHARS = 8_000;
    private static final int AUTO_ERROR_TAIL_CHARS = 12_000;
    private static final String[] ERROR_MARKERS = {
            "error",
            "exception",
            "failed",
            "failure",
            "fatal",
            "traceback",
            "stacktrace",
            "stack trace",
            "caused by",
            "cannot ",
            "could not",
            "unable to",
            "npm err!",
            "build failed",
            "test failed",
            "报错",
            "错误",
            "异常",
            "失败"
    };

    private ConsoleContextCollector() {
    }

    static PromptContext collect(Project project) {
        return ApplicationManager.getApplication().runReadAction(
                (com.intellij.openapi.util.Computable<PromptContext>) () -> collectUnderReadLock(project)
        );
    }

    static PromptContext collectAutoErrors(Project project) {
        return ApplicationManager.getApplication().runReadAction(
                (com.intellij.openapi.util.Computable<PromptContext>) () -> collectAutoErrorsUnderReadLock(project)
        );
    }

    private static PromptContext collectUnderReadLock(Project project) {
        RunContentDescriptor descriptor = ExecutionManager.getInstance(project)
                .getContentManager()
                .getSelectedContent();

        if (descriptor == null) {
            return null;
        }

        ConsoleText consoleText = findConsoleText(descriptor);
        if (consoleText == null || consoleText.text.trim().isEmpty()) {
            return null;
        }

        String displayName = descriptor.getDisplayName() == null
                ? "Run/Debug Console"
                : descriptor.getDisplayName();

        StringBuilder body = new StringBuilder();
        body.append("Run Configuration: ").append(displayName).append('\n');
        body.append("Source: ").append(consoleText.selection ? "selected console text" : "console tail").append('\n');
        body.append('\n');
        body.append(limit(consoleText.text, MAX_CONSOLE_CHARS));

        return new PromptContext(
                PromptContext.Type.CONSOLE,
                "Console output: " + displayName,
                body.toString()
        );
    }

    private static PromptContext collectAutoErrorsUnderReadLock(Project project) {
        RunContentDescriptor descriptor = ExecutionManager.getInstance(project)
                .getContentManager()
                .getSelectedContent();

        if (descriptor == null) {
            return null;
        }

        ConsoleText consoleText = findConsoleText(descriptor);
        if (consoleText == null || consoleText.text.trim().isEmpty()) {
            return null;
        }

        String text = tail(consoleText.text, AUTO_ERROR_TAIL_CHARS);
        if (!looksLikeError(text)) {
            return null;
        }

        String displayName = descriptor.getDisplayName() == null
                ? "Run/Debug Console"
                : descriptor.getDisplayName();

        StringBuilder body = new StringBuilder();
        body.append("Run Configuration: ").append(displayName).append('\n');
        body.append("Source: automatically detected Run/Debug Console error tail").append('\n');
        body.append('\n');
        body.append(limit(text, MAX_CONSOLE_CHARS));

        return new PromptContext(
                PromptContext.Type.CONSOLE,
                "Detected console error: " + displayName,
                body.toString()
        );
    }

    private static ConsoleText findConsoleText(RunContentDescriptor descriptor) {
        JComponent component = descriptor.getComponent();
        return findTextComponent(component);
    }

    private static ConsoleText findTextComponent(JComponent component) {
        if (component == null) {
            return null;
        }

        if (component instanceof JTextComponent) {
            JTextComponent textComponent = (JTextComponent) component;
            String selected = textComponent.getSelectedText();
            if (selected != null && !selected.trim().isEmpty()) {
                return new ConsoleText(selected, true);
            }

            String allText = textComponent.getText();
            if (allText != null && !allText.trim().isEmpty()) {
                return new ConsoleText(tail(allText, TAIL_CHARS), false);
            }
        }

        for (java.awt.Component child : component.getComponents()) {
            if (child instanceof JComponent) {
                ConsoleText text = findTextComponent((JComponent) child);
                if (text != null) {
                    return text;
                }
            }
        }

        return null;
    }

    private static String tail(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return "[Only the last " + maxChars + " console characters are included]\n"
                + value.substring(value.length() - maxChars);
    }

    private static String limit(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars)
                + "\n\n[Truncated: console content exceeds " + maxChars + " characters]";
    }

    private static boolean looksLikeError(String value) {
        String lower = value.toLowerCase();
        for (String marker : ERROR_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static final class ConsoleText {
        private final String text;
        private final boolean selection;

        private ConsoleText(String text, boolean selection) {
            this.text = text;
            this.selection = selection;
        }
    }
}
