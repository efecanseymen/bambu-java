package com.efecanseymen.bambu;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Bambu yazicilarina TLS ile baglanmak icin soket fabrikasi uretir.
 *
 * NEDEN DOGRULAMA KAPALI:
 * Yazicinin sertifikasi self-signed (CA imzasi yok) ve SAN alani bos.
 * Bu yuzden Java'nin iki kontrolu de basarisiz olur:
 *   1. Guven zinciri  -> "unable to find valid certification path"
 *   2. Hostname       -> "No subject alternative names present"
 * Ikisini de kapatmak gerekiyor. Bu sinif 1. kontrolu kapatir;
 * 2. kontrol icin MqttConnectOptions.setHttpsHostnameVerificationEnabled(false)
 * cagrilmalidir.
 *
 * GUVENLIK UYARISI: Bu ayar MITM saldirisina aciktir.
 * Sadece guvenilen yerel agda kullanilmalidir.
 */
public final class TlsUtil {

    private TlsUtil() {
    }

    public static SSLSocketFactory trustAllSocketFactory() throws GeneralSecurityException {
        // X509ExtendedTrustManager kullaniyoruz, duz X509TrustManager degil:
        // JDK duz olani sarmalayip kendi hostname kontrolunu ekliyor.
        TrustManager[] trustAll = {new X509ExtendedTrustManager() {

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};

        // "TLS" yerine "TLSv1.2": yazicinin gomulu yazilimi 1.3 desteklemeyebilir.
        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx.getSocketFactory();
    }
}
