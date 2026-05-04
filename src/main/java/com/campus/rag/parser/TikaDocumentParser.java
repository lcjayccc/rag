package com.campus.rag.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;

import java.io.InputStream;

/**
 * Apache Tika 文档解析器，作为 PDFBox/POI 解析失败时的备选。
 *
 * <p>Tika 可自动检测文件类型，对损坏/旧版/乱码文档更鲁棒，
 * 并可从文档属性中提取标题、页数等元数据。
 */
@Slf4j
public class TikaDocumentParser implements DocumentParser {

    private final Tika tika = new Tika();

    @Override
    public Document parse(InputStream inputStream) {
        org.apache.tika.metadata.Metadata tikaMeta = new org.apache.tika.metadata.Metadata();
        String text;
        try {
            text = tika.parseToString(inputStream, tikaMeta);
        } catch (Exception e) {
            throw new RuntimeException("Tika 文档解析失败", e);
        }

        Metadata lcMeta = new Metadata();
        String title = firstNonBlank(
                tikaMeta.get("title"),
                tikaMeta.get("dc:title"));
        if (title != null) {
            lcMeta.put("title", title);
        }
        String pageCount = tikaMeta.get("xmpTPg:NPages");
        if (pageCount != null && !pageCount.isEmpty()) {
            lcMeta.put("pageCount", pageCount);
        }

        log.debug("Tika 解析完成，文本长度: {}，title={}, pages={}",
                text.length(), title, pageCount);
        return Document.from(text, lcMeta);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
