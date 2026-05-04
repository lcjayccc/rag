package com.campus.rag.service.impl;

import com.campus.rag.auth.AuthContext;
import com.campus.rag.auth.AuthPrincipal;
import com.campus.rag.dto.RagPromptResult;
import com.campus.rag.intent.IntentClassifier;
import com.campus.rag.intent.IntentResult;
import com.campus.rag.intent.IntentType;
import com.campus.rag.service.AiChatService;
import com.campus.rag.service.RagQueryLogService;
import com.campus.rag.service.RagService;
import com.campus.rag.service.SessionHistoryService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
//新增5个是流式输出必须导入的包
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    // 引入由 LangChain4j Starter 自动装配好的通义千问模型
    // 同步模型
    private final ChatLanguageModel chatLanguageModel;
    // 新增：LangChain4j 自动装配的流式模型
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final RagService ragService; // 【新增注入】
    private final RagQueryLogService ragQueryLogService;
    private final SessionHistoryService sessionHistoryService;
    private final IntentClassifier intentClassifier;

    // 构造器注入
    public AiChatServiceImpl(ChatLanguageModel chatLanguageModel,
                             StreamingChatLanguageModel streamingChatLanguageModel,
                             RagService ragService,
                             RagQueryLogService ragQueryLogService,
                             SessionHistoryService sessionHistoryService,
                             IntentClassifier intentClassifier) {
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;//新增流式模型
        this.ragService = ragService;
        this.ragQueryLogService = ragQueryLogService;
        this.sessionHistoryService = sessionHistoryService;
        this.intentClassifier = intentClassifier;
    }

    //同步聊天方式
    @Override
    public String chatWithAi(String userMessage) {
        // 调用通义千问大模型进行同步对话生成
        return chatLanguageModel.generate(userMessage);
    }
    // 新增 完整的流式输出处理逻辑
    @Override
    public SseEmitter streamChatWithAi(String userMessage, Long categoryId, String sessionId) {
        AuthPrincipal principal = AuthContext.requireLogin();
        long requestStart = System.currentTimeMillis();
        // 【Step 8】: 将超时时间设为 0L（永不超时），防止长回答被 Spring Boot 强行截断
        SseEmitter emitter = new SseEmitter(0L);

        // Phase 3: 意图分类 — 闲聊直接拒答，不走 RAG 管道
        IntentResult intentResult = intentClassifier.classify(userMessage);
        if (intentResult.getIntent() == IntentType.CHITCHAT) {
            return handleChitchat(emitter, userMessage, sessionId, principal, requestStart);
        }

        // 多轮对话 Query Rewrite：用历史上下文将追问改写成完整问题。
        String history = sessionHistoryService.getHistory(sessionId);
        // Phase 3: 意图分类已拆分则直接用拆分结果，否则走单问题改写
        List<String> searchQueries = ragService.rewriteAndSplit(userMessage, history,
                intentResult.getSubQuestions());

        // Phase 3: 多子问题检索 — 每个子问题独立检索后合并上下文
        RagPromptResult ragResult = ragService.buildRagPrompt(searchQueries, categoryId,
                intentResult.getIntent());
        log.info("【流式对话】意图={}，子问题数={}，开卷考试试卷已下发大模型",
                intentResult.getIntent(), searchQueries.size());

        // 用于存储 AI 完整回答文本，写入会话历史。
        StringBuilder fullAnswer = new StringBuilder();

        // 将包装好的超级 Prompt 喂给千问
        streamingChatLanguageModel.generate(ragResult.getPrompt(), new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
                try {
                    fullAnswer.append(token);
                    emitter.send(token);
                } catch (Exception e) {
                    // 【优化 2】：前端主动断开连接（如关闭网页），静默关闭即可，不惊动全局异常处理
                    emitter.complete();
                }
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    sendCitationIfPresent(emitter, ragResult);
                    // AI 回答完毕，主动向前端发送 [DONE] 信号
                    emitter.send("[DONE]");
                } catch (Exception e) {
                    log.debug("发送 [DONE] 信号时前端已断开");
                } finally {
                    ragQueryLogService.record(
                            principal.getUserId(),
                            userMessage,
                            ragResult,
                            System.currentTimeMillis() - requestStart
                    );
                    sessionHistoryService.addTurn(sessionId, userMessage, fullAnswer.toString());
                }
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                // 判断是否是正常的客户端断开连接
                String errorMsg = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
                if (errorMsg.contains("broken pipe") || errorMsg.contains("clientabortexception")) {
                    log.info("【流式对话】用户主动终止了对话接收");
                    emitter.complete();
                    return;
                }

                log.error("【流式对话】大模型生成异常", error);
                try {
                    // 给前端发送友好的错误提示（支持 Markdown 渲染加粗）
                    emitter.send("\n\n**（系统异常：AI 思考被意外中断，请稍后重试）**");
                    emitter.send("[DONE]");
                } catch (Exception e) {
                    // 此时前端可能已断开，忽略发送失败
                } finally {
                    ragQueryLogService.record(
                            principal.getUserId(),
                            userMessage,
                            ragResult,
                            System.currentTimeMillis() - requestStart
                    );
                    sessionHistoryService.addTurn(sessionId, userMessage, fullAnswer.toString());
                }

                // 【优化 1】：既然已经优雅通知前端并 [DONE]，这里正常关闭即可，避免触发 Spring 的 Response committed 异常
                emitter.complete();
            }
        });

        // 3. 建立通道后立刻返回 emitter 给 Controller
        return emitter;
    }

    private void sendCitationIfPresent(SseEmitter emitter, RagPromptResult ragResult) throws IOException {
        String citation = ragResult.citationMarkdown();
        if (!citation.isBlank()) {
            emitter.send(citation);
        }
    }

    /**
     * 处理闲聊/无关问题：直接返回校园助手兜底回答，不走 RAG 管道。
     */
    private SseEmitter handleChitchat(SseEmitter emitter, String userMessage, String sessionId,
                                       AuthPrincipal principal, long requestStart) {
        log.info("意图为闲聊，直接拒答: \"{}\"", userMessage);
        String rejectMsg = "抱歉，我是河南工业大学校园智能助手，"
                + "只能回答与校园制度、通知、表格、办事流程等相关的问题。"
                + "如果您有校园相关的疑问，欢迎随时提问！";
        try {
            emitter.send(rejectMsg);
            emitter.send("[DONE]");
        } catch (Exception e) {
            log.debug("发送闲聊拒答时前端已断开");
        }
        emitter.complete();
        // 记录到查询日志
        RagPromptResult dummyResult = new RagPromptResult();
        dummyResult.setRejected(true);
        dummyResult.setRagHit(false);
        dummyResult.setRetrievedCount(0);
        dummyResult.setMinScoreUsed(0);
        dummyResult.setRetrievalLatencyMs(0);
        ragQueryLogService.record(principal.getUserId(), userMessage, dummyResult,
                System.currentTimeMillis() - requestStart);
        sessionHistoryService.addTurn(sessionId, userMessage, rejectMsg);
        return emitter;
    }
}
