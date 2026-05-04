package com.campus.rag.search;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量中文分词器，优先使用 HanLP，不可用时降级为 Bigram。
 */
public class Bm25Tokenizer {

    private final boolean hanlpAvailable;

    public Bm25Tokenizer() {
        boolean available;
        try {
            Class.forName("com.hankcs.hanlp.HanLP");
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        this.hanlpAvailable = available;
    }

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (hanlpAvailable) {
            return hanlpTokenize(text);
        }
        return bigramTokenize(text);
    }

    private List<String> hanlpTokenize(String text) {
        try {
            @SuppressWarnings("unchecked")
            List<com.hankcs.hanlp.seg.common.Term> terms =
                    com.hankcs.hanlp.HanLP.segment(text);
            List<String> tokens = new ArrayList<>(terms.size());
            for (com.hankcs.hanlp.seg.common.Term term : terms) {
                String word = term.word.trim();
                if (!word.isEmpty() && word.length() > 1) {
                    tokens.add(word);
                }
            }
            return tokens;
        } catch (Exception e) {
            return bigramTokenize(text);
        }
    }

    /**
     * Bigram 分词作为 fallback：把连续两个字符组成词。
     * 例如 "三好学生" → ["三好", "好学", "学生"]
     */
    List<String> bigramTokenize(String text) {
        String cleaned = text.replaceAll("\\s+", "");
        List<String> tokens = new ArrayList<>(cleaned.length());
        for (int i = 0; i < cleaned.length() - 1; i++) {
            tokens.add(cleaned.substring(i, i + 2));
        }
        return tokens;
    }
}
