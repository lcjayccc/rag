package com.campus.rag.service.impl;

import com.campus.rag.service.AiChatService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Service;
//新增5个是流式输出必须导入的包
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;

@Service
public class AiChatServiceImpl implements AiChatService {

    // 引入由 LangChain4j Starter 自动装配好的通义千问模型
    // 同步模型
    private final ChatLanguageModel chatLanguageModel;
    // 新增：LangChain4j 自动装配的流式模型
    private final StreamingChatLanguageModel streamingChatLanguageModel;

    // 构造器注入两个模型
    public AiChatServiceImpl(ChatLanguageModel chatLanguageModel, StreamingChatLanguageModel streamingChatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;//新增流式模型
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
        // 1. 创建 SseEmitter 实例，60000L 表示超时时间为 60 秒
        SseEmitter emitter = new SseEmitter(60000L);

        // 2. 调用流式大模型，并传入一个处理器来监听 AI 吐出的每一个字
        streamingChatLanguageModel.generate(userMessage, new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
                // 当 AI 生成一个字（token）时，立刻通过 emitter 发送给前端浏览器
                try {
                    emitter.send(token);
                } catch (IOException e) {
                    // 发生网络异常（比如用户突然关闭浏览器网页），结束流
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                // 当整段回答全部生成完毕时，通知前端结束流
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                // 当调用大模型发生报错时（如 API Key 欠费等），抛出异常
                emitter.completeWithError(error);
            }
        });

        // 3. 建立通道后立刻返回 emitter 给 Controller
        return emitter;
    }
}
