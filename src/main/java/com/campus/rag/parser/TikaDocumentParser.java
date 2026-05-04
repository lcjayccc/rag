package com.campus.rag.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;

import java.io.InputStream;

/**
 * Apache Tika 文档解析器，作为 PDFBox/POI 解析失败时的备选。
 *
 * <p>Tika 可自动检测文件类型，对损坏/旧版/乱码文档更鲁棒，并可从文档属性中
 * 提取标题、页数等元数据。
 */
@Slf4j
public class TikaDocumentParser implements DocumentParser {

    @Override
    public Document parse(InputStream inputStream) {
        BodyContentHandler handler = new BodyContentHandler(-1);
        org.apache.tika.metadata.Metadata tikaMeta = new org.apache.tika.metadata.Metadata();
        AutoDetectParser parser = new AutoDetectParser();

        try {
            parser.parse(inputStream, handler, tikaMeta, new org.apache.tika.parser.ParseContext());
        } catch (Exception e) {
            throw new RuntimeException("Tika 文档解析失败", e);
        }

        Metadata lcMeta = new Metadata();
        String title = tikaMeta.get("dc:title");
        if (title != null && !title.isEmpty()) {
            lcMeta.put("title", title);
        }
        String pageCount = tikaMeta.get("xmpTPg:NPages");
        if (pageCount != null) {
            lcMeta.put("pageCount", pageCount);
        }

        log.debug("Tika 解析完成，文本长度: {}，元数据: title={}, pages={}",
                handler.toString().length(), title, pageCount);
        return Document.from(handler.toString(), lcMeta);
    }
}
