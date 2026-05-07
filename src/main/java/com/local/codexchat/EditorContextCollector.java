package com.local.codexchat;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;

final class EditorContextCollector {
    private static final int MAX_SELECTION_CHARS = 24_000;
    private static final int MAX_AUTO_FILE_CHARS = 24_000;
    private static final int AUTO_CONTEXT_LINES_BEFORE = 120;
    private static final int AUTO_CONTEXT_LINES_AFTER = 180;

    private EditorContextCollector() {
    }

    static PromptContext collect(Project project) {
        return ApplicationManager.getApplication().runReadAction(
                (com.intellij.openapi.util.Computable<PromptContext>) () -> collectUnderReadLock(project)
        );
    }

    static PromptContext collectAuto(Project project) {
        return ApplicationManager.getApplication().runReadAction(
                (com.intellij.openapi.util.Computable<PromptContext>) () -> collectAutoUnderReadLock(project)
        );
    }

    private static PromptContext collectUnderReadLock(Project project) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null || !editor.getSelectionModel().hasSelection()) {
            return null;
        }

        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) {
            return null;
        }

        Document document = editor.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        String filePath = file == null ? "(unknown file)" : file.getPath();
        String language = file == null || file.getFileType() == null
                ? "unknown"
                : file.getFileType().getName();

        int startOffset = editor.getSelectionModel().getSelectionStart();
        int endOffset = editor.getSelectionModel().getSelectionEnd();
        int startLine = document.getLineNumber(startOffset) + 1;
        int endLine = document.getLineNumber(Math.max(startOffset, endOffset - 1)) + 1;

        String text = selectedText;
        if (text.length() > MAX_SELECTION_CHARS) {
            text = text.substring(0, MAX_SELECTION_CHARS)
                    + "\n\n[Truncated: selected code exceeds " + MAX_SELECTION_CHARS + " characters]";
        }

        StringBuilder body = new StringBuilder();
        body.append("File: ").append(filePath).append('\n');
        body.append("Language: ").append(language).append('\n');
        body.append("Lines: ").append(startLine).append("-").append(endLine).append('\n');
        body.append('\n');
        body.append(text);

        return new PromptContext(
                PromptContext.Type.CODE,
                "Selected code: " + filePath + ":" + startLine,
                body.toString()
        );
    }

    private static PromptContext collectAutoUnderReadLock(Project project) {
        PromptContext selectedContext = collectUnderReadLock(project);
        if (selectedContext != null) {
            return selectedContext;
        }

        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return null;
        }

        Document document = editor.getDocument();
        if (document.getTextLength() == 0) {
            return null;
        }

        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        String filePath = file == null ? "(unknown file)" : file.getPath();
        String language = file == null || file.getFileType() == null
                ? "unknown"
                : file.getFileType().getName();

        int caretOffset = Math.max(0, Math.min(editor.getCaretModel().getOffset(), document.getTextLength()));
        int caretLine = document.getLineNumber(caretOffset);
        int lineCount = Math.max(1, document.getLineCount());
        int startLine;
        int endLine;
        String source;

        if (document.getTextLength() <= MAX_AUTO_FILE_CHARS) {
            startLine = 0;
            endLine = lineCount - 1;
            source = document.getText();
        } else {
            startLine = Math.max(0, caretLine - AUTO_CONTEXT_LINES_BEFORE);
            endLine = Math.min(lineCount - 1, caretLine + AUTO_CONTEXT_LINES_AFTER);
            int startOffset = document.getLineStartOffset(startLine);
            int endOffset = document.getLineEndOffset(endLine);
            source = document.getText(new TextRange(startOffset, endOffset));
            if (source.length() > MAX_AUTO_FILE_CHARS) {
                source = source.substring(0, MAX_AUTO_FILE_CHARS)
                        + "\n\n[Truncated: current file context exceeds " + MAX_AUTO_FILE_CHARS + " characters]";
            }
        }

        if (source.trim().isEmpty()) {
            return null;
        }

        StringBuilder body = new StringBuilder();
        body.append("Project: ").append(project.getBasePath() == null ? "(unknown project)" : project.getBasePath()).append('\n');
        body.append("File: ").append(filePath).append('\n');
        body.append("Language: ").append(language).append('\n');
        body.append("Caret line: ").append(caretLine + 1).append('\n');
        body.append("Included lines: ").append(startLine + 1).append("-").append(endLine + 1).append('\n');
        body.append("Source: current editor file context").append('\n');
        body.append('\n');
        body.append(source);

        return new PromptContext(
                PromptContext.Type.CODE,
                "Current file: " + filePath + ":" + (caretLine + 1),
                body.toString()
        );
    }
}
