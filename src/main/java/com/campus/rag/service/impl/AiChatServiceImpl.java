package com.campus.rag.service.impl;

import com.campus.rag.service.AiChatService;
import com.campus.rag.service.RagService;
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

@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    // 引入由 LangChain4j Starter 自动装配好的通义千问模型
    // 同步模型
    private final ChatLanguageModel chatLanguageModel;
    // 新增：LangChain4j 自动装配的流式模型
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final RagService ragService; // 【新增注入】

    // 构造器注入两个模型
    public AiChatServiceImpl(ChatLanguageModel chatLanguageModel, StreamingChatLanguageModel streamingChatLanguageModel, RagService ragService) {
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;//新增流式模型
        this.ragService = ragService;
    }

    //同步聊天方式
    @Override
    public String chatWithAi(String userMessage) {
        // 调用通义千问大模型进行同步对话生成
        return chatLanguageModel.generate(userMessage);
    }
    // 新增 完整的流式输出处理逻辑
    @Override
    public SseEmitter streamChatWithAi(String userMessage) {
        // 【Step 8】: 将超时时间设为 0L（永不超时），防止长回答被 Spring Boot 强行截断
        SseEmitter emitter = new SseEmitter(0L);

        // 【Step 7】: 调用 RAG 大脑，把简短的用户问题变成带有上下文的开卷超级 Prompt
        String augmentedPrompt = ragService.buildRagPrompt(userMessage);
        log.info("【流式对话】开卷考试试卷已下发大模型，准备生成回答...");

        // 将包装好的超级 Prompt 喂给千问
        streamingChatLanguageModel.generate(augmentedPrompt, new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
                try {
                    emitter.send(token);
                } catch (Exception e) {
                    // 【优化 2】：前端主动断开连接（如关闭网页），静默关闭即可，不惊动全局异常处理
                    emitter.complete();
                }
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    // AI 回答完毕，主动向前端发送 [DONE] 信号
                    emitter.send("[DONE]");
                } catch (Exception e) {
                    log.debug("发送 [DONE] 信号时前端已断开");
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
                }

                // 【优化 1】：既然已经优雅通知前端并 [DONE]，这里正常关闭即可，避免触发 Spring 的 Response committed 异常
                emitter.complete();
            }
        });

        // 3. 建立通道后立刻返回 emitter 给 Controller
        return emitter;
    }
}
