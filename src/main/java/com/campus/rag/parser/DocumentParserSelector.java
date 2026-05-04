package com.campus.rag.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * 解析器策略选择器：主解析器（PDFBox/POI）→ 失败回退 Tika。
 *
 * <p>回退场景：损坏的 PDF、旧版 Office 格式、解析时异常等。
 */
@Slf4j
@Component
public class DocumentParserSelector {

    private static final Set<String> OFFICE_EXTENSIONS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    private final ApachePdfBoxDocumentParser pdfBoxParser = new ApachePdfBoxDocumentParser();
    private final ApachePoiDocumentParser poiParser = new ApachePoiDocumentParser();
    private final TikaDocumentParser tikaParser = new TikaDocumentParser();

    /**
     * 按文件扩展名选择主解析器，失败时自动回退 Tika。
     *
     * @param filePath 文件路径
     * @return 解析后的 LangChain4j Document
     */
    public Document parse(Path filePath) {
        String ext = getFileExtension(filePath);

        if ("pdf".equals(ext)) {
            return parseWithFallback(filePath, pdfBoxParser);
        }
        if (OFFICE_EXTENSIONS.contains(ext)) {
            return parseWithFallback(filePath, poiParser);
        }
        // 未知类型直接走 Tika（Tika 自动检测格式）
        log.info("[解析器选择] 未知文件类型 .{}，直接使用 Tika 解析: {}", ext, filePath.getFileName());
        return FileSystemDocumentLoader.loadDocument(filePath, tikaParser);
    }

    private Document parseWithFallback(Path filePath, DocumentParser primary) {
        try {
            return FileSystemDocumentLoader.loadDocument(filePath, primary);
        } catch (Exception e) {
            log.warn("[解析器选择] 主解析器失败 ({}): {}，回退 Tika 重试",
                    filePath.getFileName(), e.getMessage());
            try {
                return FileSystemDocumentLoader.loadDocument(filePath, tikaParser);
            } catch (Exception tikaEx) {
                throw new RuntimeException(
                        "主解析器和 Tika 均失败: " + filePath.getFileName(), tikaEx);
            }
        }
    }

    /**
     * @return Tika 解析器实例，供切片元数据增强时提取文档级元信息。
     */
    public TikaDocumentParser getTikaParser() {
        return tikaParser;
    }

    private String getFileExtension(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }
}
