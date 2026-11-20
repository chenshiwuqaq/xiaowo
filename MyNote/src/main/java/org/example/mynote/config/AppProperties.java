package org.example.mynote.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用自定义配置，对应 application.yml 中 mynote.* 节点。
 *
 * <p>把密钥、上传目录、AI 超时等集中到一处，避免散落在各个 Service 里的 @Value。</p>
 *
 * @author MyNote
 */
@Component
@ConfigurationProperties(prefix = "mynote")
public class AppProperties {

    private Jwt jwt = new Jwt();

    private Crypto crypto = new Crypto();

    private Upload upload = new Upload();

    private Ai ai = new Ai();

    private Weather weather = new Weather();

    private Scholar scholar = new Scholar();

    private Http http = new Http();

    public static class Jwt {
        /**
         * HS256 签名密钥，长度需不小于 32 字节
         */
        private String secret;

        /**
         * 令牌有效期（小时）
         */
        private int expireHours = 168;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getExpireHours() {
            return expireHours;
        }

        public void setExpireHours(int expireHours) {
            this.expireHours = expireHours;
        }
    }

    public static class Crypto {
        /**
         * API Key 落库加密用的主密钥，实际密钥由其 SHA-256 派生
         */
        private String aesKey;

        public String getAesKey() {
            return aesKey;
        }

        public void setAesKey(String aesKey) {
            this.aesKey = aesKey;
        }
    }

    public static class Upload {
        /**
         * 上传文件根目录，相对路径以应用工作目录为基准
         */
        private String dir = "./upload";

        /**
         * 对外访问前缀
         */
        private String urlPrefix = "/upload";

        /**
         * 允许的图片扩展名，逗号分隔
         */
        private String allowedImage = "jpg,jpeg,png,gif,webp,bmp,svg";

        /**
         * 允许的导入文档扩展名
         */
        private String allowedDoc = "md,markdown,txt";

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public String getUrlPrefix() {
            return urlPrefix;
        }

        public void setUrlPrefix(String urlPrefix) {
            this.urlPrefix = urlPrefix;
        }

        public String getAllowedImage() {
            return allowedImage;
        }

        public void setAllowedImage(String allowedImage) {
            this.allowedImage = allowedImage;
        }

        public String getAllowedDoc() {
            return allowedDoc;
        }

        public void setAllowedDoc(String allowedDoc) {
            this.allowedDoc = allowedDoc;
        }
    }

    public static class Ai {
        private int connectTimeoutSeconds = 15;

        private int readTimeoutSeconds = 120;

        /**
         * 单篇笔记送入模型的最大字符数
         */
        private int maxContextChars = 6000;

        /**
         * 同目录邻域笔记的最大条数
         */
        private int maxSiblingNotes = 6;

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }

        public void setReadTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
        }

        public int getMaxContextChars() {
            return maxContextChars;
        }

        public void setMaxContextChars(int maxContextChars) {
            this.maxContextChars = maxContextChars;
        }

        public int getMaxSiblingNotes() {
            return maxSiblingNotes;
        }

        public void setMaxSiblingNotes(int maxSiblingNotes) {
            this.maxSiblingNotes = maxSiblingNotes;
        }
    }

    public static class Weather {
        private String geocodingUrl;

        private String forecastUrl;

        private int cacheMinutes = 30;

        public String getGeocodingUrl() {
            return geocodingUrl;
        }

        public void setGeocodingUrl(String geocodingUrl) {
            this.geocodingUrl = geocodingUrl;
        }

        public String getForecastUrl() {
            return forecastUrl;
        }

        public void setForecastUrl(String forecastUrl) {
            this.forecastUrl = forecastUrl;
        }

        public int getCacheMinutes() {
            return cacheMinutes;
        }

        public void setCacheMinutes(int cacheMinutes) {
            this.cacheMinutes = cacheMinutes;
        }
    }

    public static class Scholar {
        /**
         * 是否允许联网补充最新文献
         */
        private boolean enabled = true;

        private String baseUrl = "https://api.openalex.org";

        /**
         * OpenAlex 礼貌池联系方式
         */
        private String mailto;

        private int maxResults = 8;

        private int timeoutSeconds = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getMailto() {
            return mailto;
        }

        public void setMailto(String mailto) {
            this.mailto = mailto;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Http {
        /**
         * 是否信任所有 HTTPS 证书。自签名模型端点或公司代理（MITM）场景下可临时开启，
         * 默认关闭，生产环境请优先把目标 CA 导入 JVM 信任库。
         */
        private boolean trustAllCertificates = false;

        public boolean isTrustAllCertificates() {
            return trustAllCertificates;
        }

        public void setTrustAllCertificates(boolean trustAllCertificates) {
            this.trustAllCertificates = trustAllCertificates;
        }
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Crypto getCrypto() {
        return crypto;
    }

    public void setCrypto(Crypto crypto) {
        this.crypto = crypto;
    }

    public Upload getUpload() {
        return upload;
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
    }

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai;
    }

    public Weather getWeather() {
        return weather;
    }

    public void setWeather(Weather weather) {
        this.weather = weather;
    }

    public Scholar getScholar() {
        return scholar;
    }

    public void setScholar(Scholar scholar) {
        this.scholar = scholar;
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http;
    }
}
