package com.local.codexchat;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownRenderer {
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\((https?://[^\\s)]+)\\)");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)");

    private MarkdownRenderer() {
    }

    static String toHtml(String markdown, Color text, Color muted, Color codeBackground, Color border, Color accent) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body{font-family:'Dialog';font-size:13px;color:").append(hex(text)).append(";margin:0;padding:0;}");
        html.append("p{margin:0 0 8px 0;line-height:1.42;}");
        html.append("h1{font-size:20px;margin:4px 0 10px 0;}");
        html.append("h2{font-size:17px;margin:4px 0 9px 0;}");
        html.append("h3{font-size:15px;margin:4px 0 8px 0;}");
        html.append("ul,ol{margin:0 0 8px 18px;padding:0;}");
        html.append("li{margin:0 0 4px 0;}");
        html.append("blockquote{margin:0 0 8px 0;padding:6px 10px;border-left:3px solid ")
                .append(hex(border)).append(";color:").append(hex(muted)).append(";}");
        html.append("pre{font-family:'JetBrains Mono','Consolas',monospace;font-size:12px;background:")
                .append(hex(codeBackground)).append(";border:1px solid ").append(hex(border))
                .append(";padding:8px;margin:0 0 8px 0;}");
        html.append("code{font-family:'JetBrains Mono','Consolas',monospace;font-size:12px;background:")
                .append(hex(codeBackground)).append(";}");
        html.append("a{color:").append(hex(accent)).append(";text-decoration:none;}");
        html.append("</style></head><body>");
        html.append(render(markdown == null ? "" : markdown));
        html.append("</body></html>");
        return html.toString();
    }

    private static String render(String markdown) {
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder html = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();
        StringBuilder code = new StringBuilder();
        boolean inCode = false;
        boolean inUl = false;
        boolean inOl = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                flushParagraph(html, paragraph);
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                if (inCode) {
                    html.append("<pre>").append(escape(code.toString())).append("</pre>");
                    code.setLength(0);
                }
                inCode = !inCode;
                continue;
            }

            if (inCode) {
                code.append(line).append('\n');
                continue;
            }

            if (trimmed.isEmpty()) {
                flushParagraph(html, paragraph);
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                continue;
            }

            if (trimmed.startsWith("#")) {
                flushParagraph(html, paragraph);
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                int level = headingLevel(trimmed);
                String text = trimmed.substring(level).trim();
                html.append("<h").append(level).append(">")
                        .append(inline(text))
                        .append("</h").append(level).append(">");
                continue;
            }

            if (trimmed.startsWith(">")) {
                flushParagraph(html, paragraph);
                html.append("<blockquote>").append(inline(trimmed.substring(1).trim())).append("</blockquote>");
                continue;
            }

            if (isBullet(trimmed)) {
                flushParagraph(html, paragraph);
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                if (!inUl) {
                    html.append("<ul>");
                    inUl = true;
                }
                html.append("<li>").append(inline(trimmed.substring(2).trim())).append("</li>");
                continue;
            }

            int orderedTextStart = orderedTextStart(trimmed);
            if (orderedTextStart > 0) {
                flushParagraph(html, paragraph);
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (!inOl) {
                    html.append("<ol>");
                    inOl = true;
                }
                html.append("<li>").append(inline(trimmed.substring(orderedTextStart).trim())).append("</li>");
                continue;
            }

            if (inUl) {
                html.append("</ul>");
                inUl = false;
            }
            if (inOl) {
                html.append("</ol>");
                inOl = false;
            }
            if (paragraph.length() > 0) {
                paragraph.append("<br>");
            }
            paragraph.append(inline(line));
        }

        if (inCode) {
            html.append("<pre>").append(escape(code.toString())).append("</pre>");
        }
        flushParagraph(html, paragraph);
        if (inUl) {
            html.append("</ul>");
        }
        if (inOl) {
            html.append("</ol>");
        }
        return html.toString();
    }

    private static void flushParagraph(StringBuilder html, StringBuilder paragraph) {
        if (paragraph.length() == 0) {
            return;
        }
        html.append("<p>").append(paragraph).append("</p>");
        paragraph.setLength(0);
    }

    private static int headingLevel(String trimmed) {
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }
        return Math.max(1, Math.min(3, level));
    }

    private static boolean isBullet(String trimmed) {
        return trimmed.length() > 2
                && (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ "));
    }

    private static int orderedTextStart(String trimmed) {
        int dot = trimmed.indexOf(". ");
        if (dot <= 0) {
            return -1;
        }
        for (int i = 0; i < dot; i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return -1;
            }
        }
        return dot + 2;
    }

    private static String inline(String value) {
        String text = escape(value);
        text = replaceLinks(text);
        text = replace(text, INLINE_CODE_PATTERN, "<code>$1</code>");
        text = replace(text, BOLD_PATTERN, "<b>$1</b>");
        text = replace(text, ITALIC_PATTERN, "<i>$1</i>");
        return text;
    }

    private static String replaceLinks(String text) {
        Matcher matcher = LINK_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String label = matcher.group(1);
            String url = matcher.group(2);
            matcher.appendReplacement(buffer, "<a href=\"" + url + "\">" + label + "</a>");
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String replace(String text, Pattern pattern, String replacement) {
        return pattern.matcher(text).replaceAll(replacement);
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
