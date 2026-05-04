package com.campus.rag.chunking;

import com.campus.rag.entity.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构感知切片器：检测标题边界后拆分，从标题行提取 {@code sectionTitle}。
 *
 * <p>检测模式包括：Markdown 标题、中文序号标题（一、/第一章）、数字标题（1.1）。
 * 长节在内部继续用递归切片器细分。
 */
@Component("structureAwareChunker")
public class StructureAwareChunker extends BaseChunker implements ChunkingStrategy {

    private static final int MAX_SECTION_CHARS = 500;

    // 中文标题：一、/ 二、| 第一章/第二节 | （一）/ (一) | 1.1 标题 | ## Markdown
    // 捕获完整标题行（标记 + 标题正文），仅匹配行首
    private static final Pattern HEADING = Pattern.compile(
            "^([一二三四五六七八九十]+[、，．.][^\n]*" +
            "|第[一二三四五六七八九十百千]+[章节条][^\n]*" +
            "|[（(][一二三四五六七八九十]+[)）][^\n]*" +
            "|\\d+(?:\\.\\d+)*\\s+[^\n]*" +
            "|#{1,3}\\s+[^\n]*)",
            Pattern.MULTILINE
    );

    @Override
    public List<TextSegment> chunk(String documentText, Document dbDoc) {
        List<Section> sections = splitByHeadings(documentText);
        if (sections.isEmpty()) {
            // 未检测到标题结构，回退到固定大小切片
            return new FixedSizeChunker().chunk(documentText, dbDoc);
        }

        List<TextSegment> result = new ArrayList<>();
        DocumentSplitter splitter = DocumentSplitters.recursive(MAX_SECTION_CHARS, 50);

        for (Section section : sections) {
            if (section.text.length() <= MAX_SECTION_CHARS) {
                result.add(TextSegment.from(section.text,
                        buildMetadata(dbDoc, result.size(), -1, section.title)));
            } else {
                // 长节再拆分，子块继承节标题
                dev.langchain4j.data.document.Document tempDoc =
                        dev.langchain4j.data.document.Document.from(section.text);
                List<TextSegment> subs = splitter.split(tempDoc);
                for (TextSegment sub : subs) {
                    result.add(TextSegment.from(sub.text(),
                            buildMetadata(dbDoc, result.size(), -1, section.title)));
                }
            }
        }
        return result;
    }

    private List<Section> splitByHeadings(String text) {
        List<Section> sections = new ArrayList<>();
        Matcher m = HEADING.matcher(text);
        int lastEnd = 0;
        String lastTitle = null;

        while (m.find()) {
            // 提取标题行之前的正文
            if (m.start() > lastEnd) {
                String body = text.substring(lastEnd, m.start()).trim();
                if (!body.isEmpty()) {
                    sections.add(new Section(lastTitle, body));
                }
            }
            // 新标题：去除首尾空白和尾随 # 号
            lastTitle = m.group().trim().replaceAll("[#]+$", "").trim();
            lastEnd = m.end();
        }

        // 最后一段正文
        if (lastEnd < text.length()) {
            String body = text.substring(lastEnd).trim();
            if (!body.isEmpty()) {
                sections.add(new Section(lastTitle, body));
            }
        }
        return sections;
    }

    private record Section(String title, String text) {}
}
