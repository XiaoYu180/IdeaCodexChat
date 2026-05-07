package com.local.codexchat;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class CodexChatWindow {
    private static final JBColor BG_TOP = new JBColor(new Color(0xFAFBFC), new Color(0x111315));
    private static final JBColor BG_BOTTOM = new JBColor(new Color(0xF1F3F6), new Color(0x111315));
    private static final JBColor SURFACE = new JBColor(Color.WHITE, new Color(0x181A1F));
    private static final JBColor SURFACE_RAISED = new JBColor(new Color(0xF2F4F7), new Color(0x202329));
    private static final JBColor SURFACE_HOVER = new JBColor(new Color(0xE8EEF7), new Color(0x292D35));
    private static final JBColor BORDER = new JBColor(new Color(0xDCE3EC), new Color(0x30343B));
    private static final JBColor BORDER_STRONG = new JBColor(new Color(0xC8D2DF), new Color(0x3B424D));
    private static final JBColor TEXT = new JBColor(new Color(0x111827), new Color(0xEEF2F7));
    private static final JBColor MUTED = new JBColor(new Color(0x697586), new Color(0x929BAA));
    private static final JBColor SUBTLE = new JBColor(new Color(0x8893A5), new Color(0x667080));
    private static final JBColor ACCENT = new JBColor(new Color(0x0E7AE6), new Color(0x42A5FF));
    private static final JBColor ACCENT_SOFT = new JBColor(new Color(0xE8F3FF), new Color(0x142A3F));
    private static final JBColor OPENAI = new JBColor(new Color(0x1595C8), new Color(0x2AA8D8));
    private static final JBColor USER_BUBBLE = new JBColor(new Color(0xEAF3FF), new Color(0x242B35));
    private static final JBColor ASSISTANT_BUBBLE = new JBColor(new Color(0xFFFFFF), new Color(0x15181D));
    private static final JBColor SYSTEM = new JBColor(new Color(0x9A6500), new Color(0xF0B45A));
    private static final String AUTO_CONTEXT_LABEL = "自动识别代码/报错";

    private final Project project;
    private final GradientPanel root = new GradientPanel(new BorderLayout());
    private final JPanel messages = new JPanel();
    private final JBScrollPane messagesScroll;
    private final PlaceholderTextArea input = new PlaceholderTextArea("要求后续变更");
    private final JPanel contextChips = new WrapPanel(JBUI.scale(6), JBUI.scale(6));
    private final RoundButton plusButton = RoundButton.secondary("+", 34, 34);
    private final RoundButton reviewButton = RoundButton.secondary("自动上下文 ▾", 128, 34);
    private final RoundButton providerButton = RoundButton.primary("OpenAI", 76, 34);
    private final RoundButton modelButton = RoundButton.secondary("5.5 高 ▾", 104, 34);
    private final RoundButton writeModeButton = RoundButton.ghost("可改代码 ▾", 96, 28);
    private final RoundButton sendButton = RoundButton.circle("↑", 42);
    private final List<PromptContext> contexts = new ArrayList<>();
    private final CodexCliService codexCliService;
    private String selectedModel = "gpt-5.5";
    private String selectedModelLabel = "5.5";
    private String selectedEffort = "high";
    private String selectedEffortLabel = "高";
    private ThinkingBubble thinkingBubble;
    private javax.swing.Timer thinkingTimer;
    private JLabel subtitleLabel;
    private JComponent statusPill;
    private boolean allowCodeChanges = true;
    private boolean requestRunning;

    CodexChatWindow(Project project) {
        this.project = project;
        this.codexCliService = new CodexCliService(project);
        this.messagesScroll = scroll(messages);
        buildUi();
        bindActions();
        bindResponsiveLayout();
    }

    JComponent getComponent() {
        return root;
    }

    private void buildUi() {
        root.setBorder(JBUI.Borders.empty(10));

        JPanel center = new JPanel(new BorderLayout(0, JBUI.scale(10)));
        center.setOpaque(false);
        center.add(buildHeader(), BorderLayout.NORTH);

        messages.setOpaque(false);
        messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
        messages.setBorder(JBUI.Borders.empty(8, 0, 12, 0));

        messagesScroll.setBorder(JBUI.Borders.empty());
        center.add(messagesScroll, BorderLayout.CENTER);

        root.add(center, BorderLayout.CENTER);
        root.add(buildComposer(), BorderLayout.SOUTH);

        appendAssistant("可以直接提问，也可以点击 + 附加代码、控制台错误或图片。需要我直接改项目时，切换到 `可改代码`。");
    }

    private JComponent buildHeader() {
        RoundPanel header = new RoundPanel(new BorderLayout(), new JBColor(new Color(0xFFFFFF, true), new Color(0x101318, true)), new JBColor(new Color(0x00000000, true), new Color(0x00000000, true)), JBUI.scale(18));
        header.setBorder(JBUI.Borders.empty(6, 8, 4, 8));

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Codex");
        title.setForeground(TEXT);
        title.setFont(labelFont(Font.BOLD, 15f));
        titleStack.add(title);

        subtitleLabel = new JLabel("IDEA 内对话 · 代码与控制台上下文");
        subtitleLabel.setForeground(MUTED);
        subtitleLabel.setFont(labelFont(Font.PLAIN, 12f));
        titleStack.add(subtitleLabel);

        RoundPanel status = new RoundPanel(new FlowLayout(FlowLayout.CENTER, JBUI.scale(6), 0), ACCENT_SOFT, new JBColor(new Color(0xC9DFFF), new Color(0x21425E)), JBUI.scale(18));
        status.setBorder(JBUI.Borders.empty(6, 10));
        JLabel dot = new JLabel("●");
        dot.setForeground(OPENAI);
        dot.setFont(labelFont(Font.PLAIN, 10f));
        JLabel ready = new JLabel("本地 Codex 已连接");
        ready.setForeground(TEXT);
        ready.setFont(labelFont(Font.PLAIN, 12f));
        status.add(dot);
        status.add(ready);
        statusPill = status;

        header.add(titleStack, BorderLayout.WEST);
        header.add(status, BorderLayout.EAST);
        return header;
    }

    private JComponent buildComposer() {
        JPanel wrapper = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        wrapper.setOpaque(false);

        contextChips.setOpaque(false);
        contextChips.setVisible(false);
        wrapper.add(contextChips, BorderLayout.NORTH);

        RoundPanel composer = new RoundPanel(new BorderLayout(0, JBUI.scale(9)), SURFACE, BORDER_STRONG, JBUI.scale(22));
        composer.setBorder(JBUI.Borders.empty(11, 12, 10, 12));

        input.setRows(3);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setOpaque(false);
        input.setForeground(TEXT);
        input.setCaretColor(TEXT);
        input.setFont(textFont(Font.PLAIN, 14f));
        input.setBorder(JBUI.Borders.empty(2, 4));
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendMessage");
        input.getActionMap().put("sendMessage", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                send();
            }
        });
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insert-break");

        JBScrollPane inputScroll = scroll(input);
        inputScroll.setPreferredSize(new Dimension(0, JBUI.scale(82)));
        composer.add(inputScroll, BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(JBUI.scale(10), 0));
        controls.setOpaque(false);

        WrapPanel leftControls = new WrapPanel(JBUI.scale(8), JBUI.scale(7));
        leftControls.setOpaque(false);
        leftControls.add(plusButton);
        leftControls.add(reviewButton);
        leftControls.add(providerButton);
        leftControls.add(modelButton);

        JPanel sendSlot = new JPanel(new BorderLayout());
        sendSlot.setOpaque(false);
        sendSlot.add(sendButton, BorderLayout.EAST);

        controls.add(leftControls, BorderLayout.CENTER);
        controls.add(sendSlot, BorderLayout.EAST);

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modeRow.setOpaque(false);
        modeRow.add(writeModeButton);

        JPanel bottom = new JPanel(new BorderLayout(0, JBUI.scale(5)));
        bottom.setOpaque(false);
        bottom.add(controls, BorderLayout.NORTH);
        bottom.add(modeRow, BorderLayout.SOUTH);

        composer.add(bottom, BorderLayout.SOUTH);
        wrapper.add(composer, BorderLayout.CENTER);
        return wrapper;
    }

    private JBScrollPane scroll(JComponent component) {
        JBScrollPane scrollPane = new JBScrollPane(component);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(JBUI.Borders.empty());
        return scrollPane;
    }

    private void bindActions() {
        plusButton.addActionListener(event -> showAttachMenu(plusButton));
        reviewButton.addActionListener(event -> showAttachMenu(reviewButton));
        providerButton.addActionListener(event -> appendSystem("当前使用本机 Codex CLI，账号和模型配置跟随本机 codex。"));
        modelButton.addActionListener(event -> showModelMenu(modelButton));
        writeModeButton.addActionListener(event -> toggleWriteMode());
        sendButton.addActionListener(event -> send());
    }

    private void bindResponsiveLayout() {
        ComponentAdapter resizeListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                refreshResponsiveLayout();
            }
        };
        root.addComponentListener(resizeListener);
        messagesScroll.getViewport().addComponentListener(resizeListener);
        SwingUtilities.invokeLater(this::refreshResponsiveLayout);
    }

    private void refreshResponsiveLayout() {
        int width = root.getWidth();
        if (width <= 0) {
            width = messagesScroll.getViewport().getWidth();
        }
        if (width <= 0) {
            return;
        }

        boolean compact = width < JBUI.scale(430);
        boolean tiny = width < JBUI.scale(340);
        subtitleLabel.setText(compact ? "IDEA 内对话" : "IDEA 内对话 · 代码与控制台上下文");
        statusPill.setVisible(!compact);

        reviewButton.setText(tiny ? "自动 ▾" : compact ? "上下文 ▾" : "自动上下文 ▾");
        reviewButton.resizeTo(tiny ? 76 : compact ? 94 : 128, 34);
        providerButton.setText(compact ? "AI" : "OpenAI");
        providerButton.resizeTo(compact ? 48 : 76, 34);
        modelButton.resizeTo(compact ? 88 : 104, 34);
        writeModeButton.setText(allowCodeChanges ? (compact ? "可改 ▾" : "可改代码 ▾") : (compact ? "只答 ▾" : "只回答 ▾"));
        writeModeButton.resizeTo(compact ? 72 : 96, 28);

        int viewportWidth = messagesScroll.getViewport().getWidth();
        if (viewportWidth <= 0) {
            viewportWidth = width;
        }
        for (Component component : messages.getComponents()) {
            if (component instanceof MessageBubble) {
                ((MessageBubble) component).updateWidth(viewportWidth);
            }
        }

        root.revalidate();
        root.repaint();
    }

    private void showAttachMenu(JComponent invoker) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem auto = new JMenuItem("发送时自动识别代码和控制台报错");
        auto.setEnabled(false);
        menu.add(auto);
        menu.addSeparator();

        JMenuItem code = new JMenuItem("附加选中代码");
        code.addActionListener(event -> attachEditorSelection());
        menu.add(code);

        JMenuItem console = new JMenuItem("附加控制台错误");
        console.addActionListener(event -> attachConsoleText());
        menu.add(console);

        JMenuItem image = new JMenuItem("附加图片文件");
        image.addActionListener(event -> attachImageFile());
        menu.add(image);

        JMenuItem clipboardImage = new JMenuItem("附加剪贴板截图");
        clipboardImage.addActionListener(event -> attachClipboardImage());
        menu.add(clipboardImage);

        menu.addSeparator();
        JMenuItem clear = new JMenuItem("清空上下文");
        clear.addActionListener(event -> {
            contexts.clear();
            updateContextPreview();
        });
        menu.add(clear);

        menu.show(invoker, 0, invoker.getHeight() + JBUI.scale(5));
    }

    private void showModelMenu(JComponent invoker) {
        JPopupMenu menu = new JPopupMenu();

        menu.add(disabledHeader("智能"));
        menu.add(effortItem("低", "low"));
        menu.add(effortItem("中", "medium"));
        menu.add(effortItem("高", "high"));
        menu.add(effortItem("超高", "xhigh"));
        menu.addSeparator();

        menu.add(disabledHeader("模型"));
        List<CodexCliService.ModelInfo> models = codexCliService.listModels();
        int visibleCount = Math.min(models.size(), 8);
        for (int i = 0; i < visibleCount; i++) {
            CodexCliService.ModelInfo model = models.get(i);
            menu.add(modelItem(model.displayName(), shortModelLabel(model.displayName(), model.slug()), model.slug()));
        }
        if (models.size() > visibleCount) {
            JMenu otherModels = new JMenu("其他模型");
            for (int i = visibleCount; i < models.size(); i++) {
                CodexCliService.ModelInfo model = models.get(i);
                otherModels.add(modelItem(model.displayName(), shortModelLabel(model.displayName(), model.slug()), model.slug()));
            }
            menu.add(otherModels);
        }

        menu.show(invoker, 0, invoker.getHeight() + JBUI.scale(5));
    }

    private String shortModelLabel(String displayName, String slug) {
        String label = displayName == null || displayName.trim().isEmpty() ? slug : displayName;
        if (label.startsWith("GPT-")) {
            label = label.substring(4);
        }
        int max = 12;
        if (label.length() <= max) {
            return label;
        }
        return label.substring(0, max - 1) + "…";
    }

    private JMenuItem disabledHeader(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setEnabled(false);
        return item;
    }

    private JMenuItem effortItem(String label, String value) {
        JMenuItem item = new JMenuItem(label + (selectedEffort.equals(value) ? "    ✓" : ""));
        item.addActionListener(event -> {
            selectedEffort = value;
            selectedEffortLabel = label;
            updateModelButtonText();
        });
        return item;
    }

    private JMenuItem modelItem(String label, String shortLabel, String value) {
        JMenuItem item = new JMenuItem(label + (selectedModel.equals(value) ? "    ✓" : ""));
        item.addActionListener(event -> {
            selectedModel = value;
            selectedModelLabel = shortLabel;
            updateModelButtonText();
        });
        return item;
    }

    private void updateModelButtonText() {
        modelButton.setText(selectedModelLabel + " " + selectedEffortLabel + " ▾");
        modelButton.setToolTipText("模型: " + selectedModel + "，推理强度: " + selectedEffort);
    }

    private void toggleWriteMode() {
        allowCodeChanges = !allowCodeChanges;
        writeModeButton.setText(allowCodeChanges ? "可改代码 ▾" : "只回答 ▾");
        writeModeButton.setToolTipText(allowCodeChanges
                ? "Codex 可直接修改当前 IDEA 项目文件"
                : "Codex 只回答，不修改文件");
        refreshResponsiveLayout();
    }

    private void attachEditorSelection() {
        PromptContext context = EditorContextCollector.collect(project);
        if (context == null) {
            appendSystem("没有找到当前编辑器选中的代码。");
            return;
        }
        contexts.add(context);
        updateContextPreview();
    }

    private void attachConsoleText() {
        PromptContext context = ConsoleContextCollector.collect(project);
        if (context == null) {
            appendSystem("没有找到 Run/Debug Console 的选中文本或末尾日志。");
            return;
        }
        contexts.add(context);
        updateContextPreview();
    }

    private void attachImageFile() {
        PromptContext context = ImageContextCollector.chooseImage(project);
        if (context == null) {
            return;
        }
        if (context.imagePath() == null) {
            appendSystem(context.body());
            return;
        }
        contexts.add(context);
        updateContextPreview();
    }

    private void attachClipboardImage() {
        PromptContext context = ImageContextCollector.fromClipboard();
        if (context == null) {
            appendSystem("剪贴板里没有可用图片。");
            return;
        }
        contexts.add(context);
        updateContextPreview();
    }

    private void send() {
        if (requestRunning) {
            appendSystem("Codex 正在回复，请稍后再发送。");
            return;
        }

        String message = input.getText().trim();
        if (message.isEmpty() && contexts.isEmpty()) {
            appendSystem("请输入问题，或先通过 + 附加代码、控制台上下文、图片。");
            return;
        }

        List<PromptContext> promptContexts = buildPromptContexts();
        String prompt = PromptBuilder.build(message, promptContexts, allowCodeChanges);
        List<String> imagePaths = imagePaths(promptContexts);
        input.setText("");
        appendUser(message.isEmpty() ? "仅发送上下文" : message);
        setRequestRunning(true);
        String model = selectedModel;
        String effort = selectedEffort;
        boolean writeMode = allowCodeChanges;
        thinkingBubble = appendThinking("正在思考");
        startThinkingAnimation();

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ProjectChangeSnapshot beforeSnapshot = null;
            String snapshotError = null;
            if (writeMode) {
                try {
                    beforeSnapshot = ProjectChangeSnapshot.capture(project);
                } catch (Exception exception) {
                    snapshotError = exception.getMessage();
                }
            }
            CodexCliService.Result result = codexCliService.ask(prompt, model, effort, imagePaths, writeMode);
            ProjectChangeSnapshot.ChangeSet changeSet = null;
            if (writeMode && result.isSuccess() && beforeSnapshot != null) {
                try {
                    ProjectChangeSnapshot afterSnapshot = ProjectChangeSnapshot.capture(project);
                    changeSet = beforeSnapshot.diff(afterSnapshot);
                } catch (Exception ignored) {
                    // Snapshot diff is best effort; the Codex result still remains valid.
                }
            }
            ProjectChangeSnapshot.ChangeSet finalChangeSet = changeSet;
            String finalSnapshotError = snapshotError;
            SwingUtilities.invokeLater(() -> {
                stopThinkingAnimation();
                removeThinkingBubble();
                if (result.isSuccess()) {
                    appendAssistant(result.output());
                    contexts.clear();
                    updateContextPreview();
                    if (writeMode) {
                        refreshProjectFiles();
                        if (finalChangeSet != null && !finalChangeSet.isEmpty()) {
                            appendChangeActions(finalChangeSet);
                        } else if (finalSnapshotError != null) {
                            appendSystem("本次未生成可撤销快照：" + finalSnapshotError);
                        }
                    }
                } else {
                    appendSystem(result.output());
                }
                setRequestRunning(false);
            });
        });
    }

    private void refreshProjectFiles() {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return;
        }
        LocalFileSystem.getInstance().refreshIoFiles(java.util.Collections.singletonList(new File(basePath)), true, true, null);
    }

    private List<PromptContext> buildPromptContexts() {
        List<PromptContext> promptContexts = new ArrayList<>(contexts);
        boolean hasManualCode = containsType(promptContexts, PromptContext.Type.CODE);
        boolean hasManualConsole = containsType(promptContexts, PromptContext.Type.CONSOLE);

        if (!hasManualCode) {
            PromptContext editorContext = EditorContextCollector.collectAuto(project);
            if (editorContext != null) {
                promptContexts.add(editorContext);
            }
        }

        if (!hasManualConsole) {
            PromptContext consoleContext = ConsoleContextCollector.collectAutoErrors(project);
            if (consoleContext != null) {
                promptContexts.add(consoleContext);
            }
        }

        return promptContexts;
    }

    private boolean containsType(List<PromptContext> promptContexts, PromptContext.Type type) {
        for (PromptContext context : promptContexts) {
            if (context.type() == type) {
                return true;
            }
        }
        return false;
    }

    private List<String> imagePaths(List<PromptContext> promptContexts) {
        List<String> paths = new ArrayList<>();
        for (PromptContext context : promptContexts) {
            if (context.type() == PromptContext.Type.IMAGE && context.imagePath() != null) {
                paths.add(context.imagePath());
            }
        }
        return paths;
    }

    private void setRequestRunning(boolean running) {
        requestRunning = running;
        sendButton.setEnabled(!running);
        plusButton.setEnabled(!running);
        reviewButton.setEnabled(!running);
        sendButton.setText(running ? "…" : "↑");
    }

    private void updateContextPreview() {
        contextChips.removeAll();
        if (contexts.isEmpty()) {
            contextChips.setVisible(false);
        } else {
            contextChips.setVisible(true);
            for (int i = 0; i < contexts.size(); i++) {
                PromptContext context = contexts.get(i);
                int index = i;
                RoundButton chip = RoundButton.chip(shortTitle(context.title()) + "  ×");
                chip.setToolTipText(context.preview(1000));
                chip.addActionListener(event -> {
                    if (index < contexts.size()) {
                        contexts.remove(index);
                        updateContextPreview();
                    }
                });
                contextChips.add(chip);
            }
        }
        contextChips.revalidate();
        contextChips.repaint();
    }

    private String shortTitle(String value) {
        int max = 42;
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    private void appendUser(String text) {
        appendMessage("你", text, true, false, ACCENT);
    }

    private void appendAssistant(String text) {
        appendMessage("Codex", text, false, false, TEXT);
    }

    private void appendSystem(String text) {
        appendMessage("System", text, false, true, SYSTEM);
    }

    private void appendChangeActions(ProjectChangeSnapshot.ChangeSet changeSet) {
        addBubble(new ChangeActionsPanel(changeSet));
    }

    private ThinkingBubble appendThinking(String text) {
        ThinkingBubble bubble = new ThinkingBubble(text);
        addBubble(bubble);
        return bubble;
    }

    private void appendMessage(String author, String text, boolean user, boolean system, Color authorColor) {
        addBubble(new MessageBubble(author, text, user, system, authorColor));
    }

    private void addBubble(JComponent bubble) {
        if (bubble instanceof MessageBubble) {
            int width = messagesScroll.getViewport().getWidth();
            if (width <= 0) {
                width = root.getWidth();
            }
            ((MessageBubble) bubble).updateWidth(width);
        }
        messages.add(bubble);
        messages.add(Box.createVerticalStrut(JBUI.scale(14)));
        messages.revalidate();
        messages.repaint();
        SwingUtilities.invokeLater(() -> {
            javax.swing.JScrollBar bar = messagesScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private void removeThinkingBubble() {
        if (thinkingBubble == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < messages.getComponentCount(); i++) {
            if (messages.getComponent(i) == thinkingBubble) {
                index = i;
                break;
            }
        }
        messages.remove(thinkingBubble);
        if (index >= 0 && index < messages.getComponentCount()) {
            messages.remove(index);
        }
        thinkingBubble = null;
        messages.revalidate();
        messages.repaint();
    }

    private void startThinkingAnimation() {
        final int[] tick = {0};
        thinkingTimer = new javax.swing.Timer(420, event -> {
            if (thinkingBubble != null) {
                tick[0] = (tick[0] + 1) % 4;
                thinkingBubble.setThinkingText("正在思考" + ".".repeat(tick[0]));
            }
        });
        thinkingTimer.start();
    }

    private void stopThinkingAnimation() {
        if (thinkingTimer != null) {
            thinkingTimer.stop();
            thinkingTimer = null;
        }
    }

    private static final class WrapPanel extends JPanel {
        private final int hgap;
        private final int vgap;

        private WrapPanel(int hgap, int vgap) {
            super(null);
            this.hgap = hgap;
            this.vgap = vgap;
            setOpaque(false);
        }

        @Override
        public void doLayout() {
            Insets insets = getInsets();
            int maxWidth = Math.max(1, getWidth() - insets.left - insets.right);
            int x = insets.left;
            int y = insets.top;
            int rowHeight = 0;

            for (Component component : getComponents()) {
                if (!component.isVisible()) {
                    continue;
                }
                Dimension size = component.getPreferredSize();
                if (x > insets.left && x + size.width > insets.left + maxWidth) {
                    x = insets.left;
                    y += rowHeight + vgap;
                    rowHeight = 0;
                }
                component.setBounds(x, y, size.width, size.height);
                x += size.width + hgap;
                rowHeight = Math.max(rowHeight, size.height);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return layoutSize(true);
        }

        @Override
        public Dimension getMinimumSize() {
            return layoutSize(false);
        }

        private Dimension layoutSize(boolean preferred) {
            Insets insets = getInsets();
            Container parent = getParent();
            int parentWidth = parent == null ? JBUI.scale(360) : parent.getWidth();
            if (parentWidth <= 0) {
                parentWidth = JBUI.scale(360);
            }
            int maxWidth = Math.max(1, parentWidth - insets.left - insets.right);
            int x = 0;
            int rowHeight = 0;
            int width = 0;
            int height = 0;

            for (Component component : getComponents()) {
                if (!component.isVisible()) {
                    continue;
                }
                Dimension size = preferred ? component.getPreferredSize() : component.getMinimumSize();
                if (x > 0 && x + size.width > maxWidth) {
                    width = Math.max(width, x - hgap);
                    height += rowHeight + vgap;
                    x = 0;
                    rowHeight = 0;
                }
                x += size.width + hgap;
                rowHeight = Math.max(rowHeight, size.height);
            }

            width = Math.max(width, Math.max(0, x - hgap));
            height += rowHeight;
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }
    }

    private static Font labelFont(int style, float size) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font("Dialog", Font.PLAIN, 12);
        }
        return base.deriveFont(style, JBUI.scale(size));
    }

    private static Font textFont(int style, float size) {
        Font base = UIManager.getFont("TextArea.font");
        if (base == null) {
            base = new Font("Dialog", Font.PLAIN, 12);
        }
        return base.deriveFont(style, JBUI.scale(size));
    }

    private static final class GradientPanel extends JPanel {
        private GradientPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, Math.max(getHeight(), 1), BG_BOTTOM));
                g.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static final class RoundPanel extends JPanel {
        private final Color fill;
        private final Color border;
        private final int arc;

        private RoundPanel(LayoutManager layout, Color fill, Color border, int arc) {
            super(layout);
            this.fill = fill;
            this.border = border;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth() - 1;
                int height = getHeight() - 1;
                g.setColor(fill);
                g.fillRoundRect(0, 0, width, height, arc, arc);
                g.setColor(border);
                g.drawRoundRect(0, 0, width, height, arc, arc);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static final class RoundButton extends JButton {
        private final Color fill;
        private final Color hoverFill;
        private final Color border;
        private final int arc;
        private boolean hover;

        private RoundButton(String text, Color fill, Color hoverFill, Color border, Color foreground, int width, int height, int arc) {
            super(text);
            this.fill = fill;
            this.hoverFill = hoverFill;
            this.border = border;
            this.arc = arc;
            setForeground(foreground);
            setFont(labelFont(Font.PLAIN, 13f));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(JBUI.Borders.empty(0, 10));
            setPreferredSize(new Dimension(JBUI.scale(width), JBUI.scale(height)));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hover = false;
                    repaint();
                }
            });
        }

        static RoundButton secondary(String text, int width, int height) {
            return new RoundButton(text, SURFACE_RAISED, SURFACE_HOVER, BORDER, TEXT, width, height, JBUI.scale(12));
        }

        static RoundButton primary(String text, int width, int height) {
            return new RoundButton(text, OPENAI, new JBColor(new Color(0x0E83B2), new Color(0x38B4E4)), OPENAI, Color.WHITE, width, height, JBUI.scale(12));
        }

        static RoundButton ghost(String text, int width, int height) {
            return new RoundButton(text, new JBColor(new Color(0x00000000, true), new Color(0x00000000, true)), SURFACE_RAISED, new JBColor(new Color(0x00000000, true), new Color(0x00000000, true)), MUTED, width, height, JBUI.scale(8));
        }

        static RoundButton circle(String text, int size) {
            return new RoundButton(text, new JBColor(new Color(0xCED5DF), new Color(0xE5E7EB)), new JBColor(new Color(0xBBC6D3), Color.WHITE), new JBColor(new Color(0xCED5DF), new Color(0xE5E7EB)), new Color(0x111315), size, size, JBUI.scale(size));
        }

        static RoundButton chip(String text) {
            return new RoundButton(text, ACCENT_SOFT, SURFACE_HOVER, BORDER, TEXT, 190, 30, JBUI.scale(14));
        }

        void resizeTo(int width, int height) {
            Dimension size = new Dimension(JBUI.scale(width), JBUI.scale(height));
            setPreferredSize(size);
            setMinimumSize(size);
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth() - 1;
                int height = getHeight() - 1;
                g.setColor(isEnabled() ? (hover ? hoverFill : fill) : SURFACE_RAISED);
                g.fillRoundRect(0, 0, width, height, arc, arc);
                g.setColor(border);
                g.drawRoundRect(0, 0, width, height, arc, arc);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static final class PlaceholderTextArea extends JTextArea {
        private final String placeholder;

        private PlaceholderTextArea(String placeholder) {
            this.placeholder = placeholder;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!getText().isEmpty()) {
                return;
            }

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(SUBTLE);
                g.setFont(getFont());
                g.drawString(placeholder, JBUI.scale(5), JBUI.scale(21));
            } finally {
                g.dispose();
            }
        }
    }

    private static class MessageBubble extends JPanel {
        private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

        private final String text;
        private final boolean user;
        private final boolean system;
        private final boolean plainAssistant;
        private final JPanel row;
        private final JPanel stack;
        private final JComponent body;
        private final JTextArea textBody;
        private final MarkdownBodyPanel markdownBody;
        private final BubblePanel bubble;
        private final JComponent content;

        private MessageBubble(String author, String text, boolean user, boolean system, Color authorColor) {
            super(new BorderLayout());
            this.text = text == null ? "" : text;
            this.user = user;
            this.system = system;
            this.plainAssistant = !user && !system;
            setOpaque(false);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

            row = new JPanel(new BorderLayout());
            row.setOpaque(false);

            stack = new JPanel();
            stack.setOpaque(false);
            stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

            if (!user) {
                JLabel authorLabel = new JLabel(author);
                authorLabel.setForeground(authorColor);
                authorLabel.setFont(labelFont(Font.BOLD, 13f));
                authorLabel.setBorder(JBUI.Borders.empty(0, 2, 4, 0));
                stack.add(authorLabel);
            }

            if (user) {
                textBody = new JTextArea(this.text);
                textBody.setEditable(false);
                textBody.setLineWrap(true);
                textBody.setWrapStyleWord(true);
                textBody.setOpaque(false);
                textBody.setForeground(TEXT);
                textBody.setFont(textFont(Font.PLAIN, 14f));
                textBody.setBorder(JBUI.Borders.empty(10, 13, 10, 13));
                markdownBody = null;
                body = textBody;
            } else {
                markdownBody = new MarkdownBodyPanel(
                        this.text,
                        system ? SYSTEM : TEXT,
                        plainAssistant ? JBUI.Borders.empty(1, 2, 1, 2) : JBUI.Borders.empty(10, 13, 10, 13)
                );
                textBody = null;
                body = markdownBody;
            }

            Color bubbleFill = system ? ACCENT_SOFT : (user ? USER_BUBBLE : ASSISTANT_BUBBLE);
            Color bubbleBorder = system ? new JBColor(new Color(0xD8C08A), new Color(0x3C3324)) : (user ? new JBColor(new Color(0xC9DFFF), new Color(0x2B394A)) : BORDER);
            if (plainAssistant) {
                bubble = null;
                content = body;
            } else {
                bubble = new BubblePanel(body, bubbleFill, bubbleBorder, JBUI.scale(17));
                content = bubble;
            }
            content.setAlignmentX(user ? RIGHT_ALIGNMENT : LEFT_ALIGNMENT);
            stack.add(content);

            JPanel meta = new JPanel(new FlowLayout(user ? FlowLayout.RIGHT : FlowLayout.LEFT, JBUI.scale(8), 0));
            meta.setOpaque(false);
            meta.setBorder(JBUI.Borders.empty(5, 2, 0, 2));
            JLabel time = new JLabel(LocalTime.now().format(TIME_FORMAT));
            time.setForeground(MUTED);
            time.setFont(labelFont(Font.PLAIN, 12f));
            meta.add(time);
            RoundButton copy = RoundButton.ghost("复制", 46, 24);
            copy.setToolTipText("复制消息");
            copy.addActionListener(event -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(this.text), null);
                copy.setText("已复制");
                javax.swing.Timer timer = new javax.swing.Timer(900, e -> copy.setText("复制"));
                timer.setRepeats(false);
                timer.start();
            });
            meta.add(copy);
            meta.setAlignmentX(user ? RIGHT_ALIGNMENT : LEFT_ALIGNMENT);
            stack.add(meta);

            row.add(stack, user ? BorderLayout.EAST : BorderLayout.WEST);
            add(row, BorderLayout.CENTER);
        }

        private void updateWidth(int viewportWidth) {
            if (viewportWidth <= 0) {
                return;
            }

            int outerInset = viewportWidth < JBUI.scale(360) ? JBUI.scale(8) : JBUI.scale(42);
            row.setBorder(JBUI.Borders.empty(0, user ? outerInset : 0, 0, user ? 0 : outerInset));

            int available = viewportWidth - outerInset - JBUI.scale(18);
            int maxWidth = Math.max(JBUI.scale(150), Math.min(JBUI.scale(620), available));
            int contentWidth = plainAssistant ? maxWidth : desiredBubbleWidth(maxWidth);
            int contentHeight = user ? preferredTextHeight(contentWidth) : preferredMarkdownHeight(contentWidth);

            body.setPreferredSize(new Dimension(contentWidth, contentHeight));
            body.setMaximumSize(new Dimension(contentWidth, contentHeight));
            if (bubble != null) {
                bubble.setPreferredSize(new Dimension(contentWidth, contentHeight));
                bubble.setMaximumSize(new Dimension(contentWidth, Integer.MAX_VALUE));
            }
            stack.setMaximumSize(new Dimension(contentWidth, Integer.MAX_VALUE));
            stack.revalidate();
            revalidate();
        }

        private int desiredBubbleWidth(int maxWidth) {
            if (system || text.length() > 120) {
                return maxWidth;
            }

            FontMetrics metrics = body.getFontMetrics(body.getFont());
            int longest = 0;
            for (String line : text.split("\n", -1)) {
                longest = Math.max(longest, metrics.stringWidth(line));
            }

            if (longest > maxWidth - JBUI.scale(34)) {
                return maxWidth;
            }

            int minWidth = user ? JBUI.scale(74) : JBUI.scale(180);
            int padded = longest + JBUI.scale(36);
            return Math.max(minWidth, Math.min(maxWidth, padded));
        }

        private int preferredMarkdownHeight(int width) {
            markdownBody.setContentWidth(width);
            return Math.max(JBUI.scale(24), markdownBody.getPreferredSize().height);
        }

        private int preferredTextHeight(int width) {
            Insets insets = textBody.getInsets();
            int textWidth = Math.max(JBUI.scale(40), width - insets.left - insets.right - JBUI.scale(4));
            FontMetrics metrics = textBody.getFontMetrics(textBody.getFont());
            int lines = 0;
            for (String paragraph : text.split("\n", -1)) {
                lines += estimateWrappedLines(paragraph, textWidth, metrics);
            }
            int minLines = text.isEmpty() ? 1 : Math.max(1, lines);
            return minLines * metrics.getHeight() + insets.top + insets.bottom + JBUI.scale(4);
        }

        private int estimateWrappedLines(String paragraph, int textWidth, FontMetrics metrics) {
            if (paragraph.isEmpty()) {
                return 1;
            }

            int lines = 1;
            int lineWidth = 0;
            for (int i = 0; i < paragraph.length(); i++) {
                int charWidth = metrics.charWidth(paragraph.charAt(i));
                if (lineWidth > 0 && lineWidth + charWidth > textWidth) {
                    lines++;
                    lineWidth = charWidth;
                } else {
                    lineWidth += charWidth;
                }
            }
            return lines;
        }
    }

    private static final class MarkdownBodyPanel extends JPanel {
        private final List<JComponent> blocks = new ArrayList<>();

        private MarkdownBodyPanel(String markdown, Color textColor, javax.swing.border.Border border) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(border);
            parse(markdown == null ? "" : markdown, textColor);
        }

        private void parse(String markdown, Color textColor) {
            String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
            StringBuilder text = new StringBuilder();
            StringBuilder code = new StringBuilder();
            String language = "";
            boolean inCode = false;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("```")) {
                    if (inCode) {
                        addCodeBlock(code.toString(), language);
                        code.setLength(0);
                        language = "";
                        inCode = false;
                    } else {
                        addTextBlock(text.toString(), textColor);
                        text.setLength(0);
                        language = trimmed.length() > 3 ? trimmed.substring(3).trim() : "";
                        inCode = true;
                    }
                    continue;
                }

                if (inCode) {
                    code.append(line).append('\n');
                } else {
                    text.append(line).append('\n');
                }
            }

            if (inCode) {
                addCodeBlock(code.toString(), language);
            }
            addTextBlock(text.toString(), textColor);
        }

        private void addTextBlock(String markdown, Color textColor) {
            if (markdown == null || markdown.trim().isEmpty()) {
                return;
            }

            JEditorPane pane = new JEditorPane();
            pane.setContentType("text/html");
            pane.setEditable(false);
            pane.setOpaque(false);
            pane.setForeground(textColor);
            pane.setFont(textFont(Font.PLAIN, 14f));
            pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            pane.setText(MarkdownRenderer.toHtml(markdown.trim(), textColor, MUTED, SURFACE_RAISED, BORDER, ACCENT));
            pane.setAlignmentX(LEFT_ALIGNMENT);
            blocks.add(pane);
            add(pane);
        }

        private void addCodeBlock(String code, String language) {
            CodeBlockPanel panel = new CodeBlockPanel(code == null ? "" : trimTrailingNewline(code), language);
            panel.setAlignmentX(LEFT_ALIGNMENT);
            blocks.add(panel);
            add(panel);
        }

        private void setContentWidth(int width) {
            Insets insets = getInsets();
            int innerWidth = Math.max(JBUI.scale(80), width - insets.left - insets.right);
            int totalHeight = insets.top + insets.bottom;

            for (JComponent block : blocks) {
                if (block instanceof CodeBlockPanel) {
                    ((CodeBlockPanel) block).setContentWidth(innerWidth);
                } else {
                    block.setSize(new Dimension(innerWidth, Integer.MAX_VALUE));
                    Dimension preferred = block.getPreferredSize();
                    block.setPreferredSize(new Dimension(innerWidth, preferred.height));
                    block.setMaximumSize(new Dimension(innerWidth, preferred.height));
                }
                totalHeight += block.getPreferredSize().height;
            }

            Dimension size = new Dimension(width, Math.max(JBUI.scale(24), totalHeight));
            setPreferredSize(size);
            setMaximumSize(new Dimension(width, Integer.MAX_VALUE));
            revalidate();
        }

        private static String trimTrailingNewline(String value) {
            String result = value;
            while (result.endsWith("\n")) {
                result = result.substring(0, result.length() - 1);
            }
            return result;
        }
    }

    private static final class CodeBlockPanel extends JPanel {
        private final JTextArea codeArea;

        private CodeBlockPanel(String code, String language) {
            super(new BorderLayout(0, JBUI.scale(6)));
            setOpaque(false);
            setBorder(JBUI.Borders.empty(8, 8, 8, 8));

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel languageLabel = new JLabel(language == null || language.isEmpty() ? "code" : language);
            languageLabel.setForeground(MUTED);
            languageLabel.setFont(labelFont(Font.PLAIN, 12f));
            RoundButton copyButton = RoundButton.ghost("复制代码", 72, 24);
            copyButton.addActionListener(event -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
                copyButton.setText("已复制");
                javax.swing.Timer timer = new javax.swing.Timer(900, e -> copyButton.setText("复制代码"));
                timer.setRepeats(false);
                timer.start();
            });
            header.add(languageLabel, BorderLayout.WEST);
            header.add(copyButton, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            codeArea = new JTextArea(code);
            codeArea.setEditable(false);
            codeArea.setLineWrap(true);
            codeArea.setWrapStyleWord(false);
            codeArea.setOpaque(false);
            codeArea.setForeground(TEXT);
            codeArea.setCaretColor(TEXT);
            codeArea.setFont(new Font("JetBrains Mono", Font.PLAIN, JBUI.scale(13)));
            codeArea.setBorder(JBUI.Borders.empty(2, 2, 2, 2));
            add(codeArea, BorderLayout.CENTER);
        }

        private void setContentWidth(int width) {
            int innerWidth = Math.max(JBUI.scale(80), width - JBUI.scale(16));
            codeArea.setSize(new Dimension(innerWidth, Integer.MAX_VALUE));
            Dimension codeSize = codeArea.getPreferredSize();
            int height = JBUI.scale(40) + codeSize.height + JBUI.scale(18);
            Dimension size = new Dimension(width, height);
            setPreferredSize(size);
            setMaximumSize(new Dimension(width, Integer.MAX_VALUE));
            revalidate();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth() - 1;
                int height = getHeight() - 1;
                int arc = JBUI.scale(12);
                g.setColor(SURFACE_RAISED);
                g.fillRoundRect(0, 0, width, height, arc, arc);
                g.setColor(BORDER);
                g.drawRoundRect(0, 0, width, height, arc, arc);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private final class ChangeActionsPanel extends JPanel {
        private final ProjectChangeSnapshot.ChangeSet changeSet;

        private ChangeActionsPanel(ProjectChangeSnapshot.ChangeSet changeSet) {
            super(new BorderLayout(0, JBUI.scale(8)));
            this.changeSet = changeSet;
            setOpaque(false);
            setBorder(JBUI.Borders.empty(0, 2, 0, 0));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(72)));

            JLabel label = new JLabel("已记录本次变更：" + changeSet.summary());
            label.setForeground(MUTED);
            label.setFont(labelFont(Font.PLAIN, 12f));
            add(label, BorderLayout.NORTH);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
            actions.setOpaque(false);
            RoundButton view = RoundButton.secondary("查看变更", 86, 30);
            RoundButton undo = RoundButton.secondary("撤销本次改动", 112, 30);
            view.addActionListener(event -> appendAssistant(changeSet.toMarkdown()));
            undo.addActionListener(event -> restoreChangeSet(undo));
            actions.add(view);
            actions.add(undo);
            add(actions, BorderLayout.CENTER);
        }

        private void restoreChangeSet(RoundButton undo) {
            try {
                changeSet.restore();
                refreshProjectFiles();
                undo.setEnabled(false);
                undo.setText("已撤销");
                appendSystem("已撤销本次改动。");
            } catch (Exception exception) {
                appendSystem("撤销失败：" + exception.getMessage());
            }
        }
    }

    private static final class ThinkingBubble extends JPanel {
        private final JLabel label;

        private ThinkingBubble(String text) {
            super(new BorderLayout());
            setOpaque(false);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(42)));
            setBorder(JBUI.Borders.empty(0, 2, 0, 0));

            label = new JLabel(text);
            label.setForeground(MUTED);
            label.setFont(labelFont(Font.PLAIN, 14f));
            add(label, BorderLayout.WEST);
        }

        private void setThinkingText(String text) {
            label.setText(text);
        }
    }

    private static final class BubblePanel extends JPanel {
        private final Color fill;
        private final Color border;
        private final int arc;

        private BubblePanel(JComponent child, Color fill, Color border, int arc) {
            super(new BorderLayout());
            this.fill = fill;
            this.border = border;
            this.arc = arc;
            setOpaque(false);
            add(child, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth() - 1;
                int height = getHeight() - 1;
                g.setColor(fill);
                g.fillRoundRect(0, 0, width, height, arc, arc);
                g.setColor(border);
                g.drawRoundRect(0, 0, width, height, arc, arc);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
