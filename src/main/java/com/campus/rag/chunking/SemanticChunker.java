package com.campus.rag.chunking;

import com.campus.rag.entity.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 语义边界切片器：按段落（双换行）和问答对（Q/A/问/答）边界切分。
 *
 * <p>适用于 FAQ、办事指南等短文档，将语义完整的问答对作为一个切片单元。
 */
@Component("semanticChunker")
public class SemanticChunker extends BaseChunker implements ChunkingStrategy {

    private static final int MAX_CHUNK_CHARS = 600;

    // 问答起始标志：Q:/A:/问：/答：/问题：/回答：
    private static final Pattern QA_START = Pattern.compile(
            "^\\s*(Q|A|问|答|问题|回答)\\s*[：:]", Pattern.CASE_INSENSITIVE);

    @Override
    public List<TextSegment> chunk(String documentText, Document dbDoc) {
        String[] paragraphs = documentText.split("\\n\\s*\\n");
        List<TextSegment> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            // 遇到新的问答起始标志时，提交当前累积的切片
            if (QA_START.matcher(para).find() && current.length() > 0) {
                result.add(TextSegment.from(current.toString().trim(),
                        buildMetadata(dbDoc, result.size(), -1, null)));
                current = new StringBuilder();
            }

            // 当前切片接近上限时提交，避免单切片过大
            if (current.length() + para.length() > MAX_CHUNK_CHARS && current.length() > 0) {
                result.add(TextSegment.from(current.toString().trim(),
                        buildMetadata(dbDoc, result.size(), -1, null)));
                current = new StringBuilder();
            }

            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(para);
        }

        // 提交最后一个切片
        if (current.length() > 0) {
            result.add(TextSegment.from(current.toString().trim(),
                    buildMetadata(dbDoc, result.size(), -1, null)));
        }

        return result;
    }
}
