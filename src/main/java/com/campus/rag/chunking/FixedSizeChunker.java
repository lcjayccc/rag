package com.campus.rag.chunking;

import com.campus.rag.entity.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定大小切片器，保留现有 {@code recursive(500, 50)} 逻辑。
 */
@Component("fixedSizeChunker")
public class FixedSizeChunker extends BaseChunker implements ChunkingStrategy {

    private static final int CHUNK_SIZE = 500;
    private static final int OVERLAP = 50;

    @Override
    public List<TextSegment> chunk(String documentText, Document dbDoc) {
        dev.langchain4j.data.document.Document tempDoc =
                dev.langchain4j.data.document.Document.from(documentText);
        DocumentSplitter splitter = DocumentSplitters.recursive(CHUNK_SIZE, OVERLAP);
        List<TextSegment> rawChunks = splitter.split(tempDoc);

        int totalChunks = rawChunks.size();
        List<TextSegment> enriched = new ArrayList<>(totalChunks);
        for (int i = 0; i < totalChunks; i++) {
            enriched.add(TextSegment.from(rawChunks.get(i).text(),
                    buildMetadata(dbDoc, i, totalChunks, null)));
        }
        return enriched;
    }
}
