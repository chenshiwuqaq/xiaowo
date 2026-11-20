package org.example.mynote.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.mynote.common.BizException;
import org.example.mynote.common.ResultCode;
import org.example.mynote.config.AppProperties;
import org.example.mynote.entity.AiConfigDO;
import org.example.mynote.util.SslUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * OpenAI 兼容协议客户端。
 *
 * <p>DeepSeek、通义千问、Kimi、智谱、OpenAI 都提供 /chat/completions 这套接口，
 * 因此切模型只需要换 baseUrl + apiKey + modelName，不需要为每家写适配代码，也不需要引入
 * Spring AI 等额外依赖。用 JDK 自带 HttpClient 实现，阻塞与流式两条路径共用请求构造逻辑。</p>
 *
 * @author MyNote
 */
@Component
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private static final String CHAT_PATH = "/chat/completions";

    private static final String DATA_PREFIX = "data:";

    private static final String DONE_FLAG = "[DONE]";

    private final AppProperties appProperties;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    public OpenAiClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(appProperties.getAi().getConnectTimeoutSeconds()));
        if (appProperties.getHttp().isTrustAllCertificates()) {
            builder.sslContext(SslUtil.trustAllContext());
        }
        this.httpClient = builder.build();
    }

    /**
     * 一次问答，等模型写完再返回全文。用于润色、拟题、起名这类"要整段结果"的动作。
     *
     * @param config   模型配置（apiKey 已解密为明文）
     * @param messages 消息列表，role/content 成对
     * @return 模型输出文本
     */
    public String chatOnce(AiRuntimeConfig config, List<Message> messages) {
        return chatOnce(config, messages, Duration.ofSeconds(appProperties.getAi().getReadTimeoutSeconds()));
    }

    /**
     * 一次问答的带超时版本，供"可降级"的场景使用——例如文献推荐里让模型做二次排序，
     * 超时就退回规则打分，不该让一个慢模型把整条推荐接口拖垮。
     *
     * @param config   模型配置
     * @param messages 消息列表
     * @param timeout  本次调用的超时
     * @return 模型输出文本
     */
    public String chatOnce(AiRuntimeConfig config, List<Message> messages, Duration timeout) {
        String body = buildRequestBody(config, messages, false);
        HttpRequest request = buildRequest(config, body, timeout);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.warn("AI 调用失败 status={} body={}", response.statusCode(), response.body());
                throw new BizException(ResultCode.AI_CALL_FAILED, friendlyError(response.statusCode(), response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                throw new BizException(ResultCode.AI_CALL_FAILED, "模型返回了空内容，请重试");
            }
            return content.asText().trim();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 调用异常", e);
            throw new BizException(ResultCode.AI_CALL_FAILED, "调用模型失败：" + e.getMessage());
        }
    }

    /**
     * 流式问答。逐段回调增量文本，边生成边推给前端，长回答不至于白屏等待。
     *
     * @param config   模型配置
     * @param messages 消息列表
     * @param onDelta  增量文本回调
     * @return 完整回答文本
     */
    public String chatStream(AiRuntimeConfig config, List<Message> messages, Consumer<String> onDelta) {
        String body = buildRequestBody(config, messages, true);
        HttpRequest request = buildRequest(config, body);
        StringBuilder full = new StringBuilder(512);
        try {
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() / 100 != 2) {
                String errorBody = readAll(response);
                log.warn("AI 流式调用失败 status={} body={}", response.statusCode(), errorBody);
                throw new BizException(ResultCode.AI_CALL_FAILED, friendlyError(response.statusCode(), errorBody));
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> {
                    if (line == null || !line.startsWith(DATA_PREFIX)) {
                        return;
                    }
                    String payload = line.substring(DATA_PREFIX.length()).trim();
                    if (payload.isEmpty() || DONE_FLAG.equals(payload)) {
                        return;
                    }
                    String delta = extractDelta(payload);
                    if (delta != null && !delta.isEmpty()) {
                        full.append(delta);
                        onDelta.accept(delta);
                    }
                });
            }
            return full.toString();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 流式调用异常", e);
            throw new BizException(ResultCode.AI_CALL_FAILED, "调用模型失败：" + e.getMessage());
        }
    }

    /**
     * 抽取 SSE 帧中的增量文本。上游偶尔给出空 choices（心跳帧），返回 null 由调用方跳过。
     *
     * @param payload data: 后面那段 JSON
     * @return 增量文本
     */
    private String extractDelta(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode delta = node.path("choices").path(0).path("delta").path("content");
            return delta.isMissingNode() || delta.isNull() ? null : delta.asText();
        } catch (Exception e) {
            log.debug("跳过无法解析的流式帧: {}", payload);
            return null;
        }
    }

    /**
     * 构造请求体。max_tokens 与 temperature 由用户配置决定，便于在设置页调"创作发散度"。
     *
     * @param config   运行时配置
     * @param messages 消息
     * @param stream   是否流式
     * @return JSON 字符串
     */
    private String buildRequestBody(AiRuntimeConfig config, List<Message> messages, boolean stream) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.getModelName());
        root.put("temperature", config.getTemperature() == null ? 0.7D : config.getTemperature().doubleValue());
        root.put("max_tokens", config.getMaxTokens() == null ? 2048 : config.getMaxTokens());
        root.put("stream", stream);
        // DeepSeek V4 思考模式默认开启，思考 token 会计入 max_tokens，长提示下会把最终回答挤成空；
        // 写作助手场景要的是直接回答，统一关闭思考，既稳又快
        root.putObject("thinking").put("type", "disabled");
        ArrayNode array = root.putArray("messages");
        for (Message message : messages) {
            ObjectNode item = array.addObject();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
        }
        return root.toString();
    }

    /**
     * 构造 HTTP 请求，鉴权头按标准 Bearer 格式给出。
     *
     * @param config 运行时配置
     * @param body   请求体
     * @return 请求
     */
    private HttpRequest buildRequest(AiRuntimeConfig config, String body) {
        return buildRequest(config, body, Duration.ofSeconds(appProperties.getAi().getReadTimeoutSeconds()));
    }

    /**
     * 构造带自定义超时的 HTTP 请求。
     *
     * @param config  运行时配置
     * @param body    请求体
     * @param timeout 本次请求超时
     * @return 请求
     */
    private HttpRequest buildRequest(AiRuntimeConfig config, String body, Duration timeout) {
        return HttpRequest.newBuilder()
                .uri(URI.create(endpoint(config.getBaseUrl())))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * 拼接接口地址。用户填写的 baseUrl 可能带或不带结尾斜杠，两种都要能通。
     *
     * @param baseUrl 基础地址
     * @return 完整地址
     */
    private String endpoint(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + CHAT_PATH;
    }

    /**
     * 读尽流式响应的错误体，用于给出可诊断的提示。
     *
     * @param response 响应
     * @return 文本
     */
    private String readAll(HttpResponse<Stream<String>> response) {
        try (Stream<String> lines = response.body()) {
            return lines.limit(20).reduce("", (a, b) -> a + b);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 把上游错误码翻译成用户能看懂的话。401/403 九成是 Key 填错，直接点名原因比抛原始报文有用。
     *
     * @param status 上游状态码
     * @param body   上游响应体
     * @return 中文提示
     */
    private String friendlyError(int status, String body) {
        if (status == 401 || status == 403) {
            return "API Key 无效或没有该模型的访问权限";
        }
        if (status == 404) {
            return "接口地址或模型名不存在，请检查 Base URL 与模型名";
        }
        if (status == 429) {
            return "模型调用太频繁或额度不足，稍后再试";
        }
        if (body != null && !body.isEmpty()) {
            return "模型返回异常（" + status + "）：" + WordSnippet.cut(body);
        }
        return ResultCode.AI_CALL_FAILED.getMessage();
    }

    /**
     * 消息载体。
     */
    public static class Message {

        private final String role;

        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }

    /**
     * 上游报文截断工具。
     */
    static final class WordSnippet {

        private static final int MAX_LENGTH = 160;

        static String cut(String text) {
            String clean = text.replaceAll("\\s+", " ").trim();
            return clean.length() <= MAX_LENGTH ? clean : clean.substring(0, MAX_LENGTH) + "...";
        }
    }
}
