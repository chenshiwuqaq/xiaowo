package org.example.mynote.util;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * HTTPS 信任相关工具。
 *
 * <p>自托管场景下模型端点可能是自签名证书，或网络里存在公司代理（MITM）导致
 * JVM 默认信任库校验不过（PKIX path building failed）。这里提供一个"信任所有证书"的
 * SSLContext，供需要时显式开启（默认关闭）。生产环境仍建议把目标 CA 导入信任库。</p>
 *
 * @author MyNote
 */
public final class SslUtil {

    private SslUtil() {
    }

    /**
     * 构建信任所有证书的 SSLContext。
     *
     * @return SSLContext
     */
    public static SSLContext trustAllContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            // 信任所有
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            // 信任所有
                        }
                    }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            return context;
        } catch (Exception e) {
            throw new IllegalStateException("构建信任所有证书的 SSLContext 失败", e);
        }
    }
}
