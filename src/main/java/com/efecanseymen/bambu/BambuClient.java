package com.efecanseymen.bambu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Bambu Lab yazicisina LAN modunda baglanir, durumunu okur ve komut gonderir.
 *
 * Kullanim:
 * <pre>
 *   BambuClient client = new BambuClient("10.10.10.183", "accessCode", "seriNo");
 *   client.connect();
 *   PrinterStatus s = client.getStatus();
 *   client.setLight(true);
 *   client.disconnect();
 * </pre>
 *
 * Sinif MqttCallbackExtended'i uyguluyor; yani Paho, bagalanti olaylarini
 * (mesaj geldi, baglanti koptu, yeniden baglandi) bu sinifin metotlarindan
 * haber veriyor.
 */
public class BambuClient implements MqttCallbackExtended {

    private static final String USERNAME = "bblp";   // Bambu'da sabit
    private static final int PORT = 8883;

    private final String ip;
    private final String accessCode;
    private final String serial;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Her komuta artan bir numara veriyoruz. Yazicinin cevabi ayni numarayi
     * tasir; hangi cevabin hangi komuta ait oldugu boyle anlasilir.
     * AtomicInteger: birden fazla thread ayni anda komut gonderirse
     * numaralar cakismasin diye.
     */
    private final AtomicInteger sequenceId = new AtomicInteger();

    private MqttClient client;

    /*
     * volatile: bu alanlari Paho'nun dinleyici thread'i yaziyor, kullanicinin
     * thread'i okuyor. volatile olmazsa okuyan thread eski degeri gorebilir.
     */
    private volatile PrinterStatus lastStatus;
    private volatile long lastUpdateMillis;
    private volatile Consumer<PrinterStatus> statusListener;

    public BambuClient(String ip, String accessCode, String serial) {
        this.ip = ip;
        this.accessCode = accessCode;
        this.serial = serial;
    }

    // ==================== BAGLANTI ====================

    /** Varsayilan ayarlarla baglanir. */
    public void connect() throws MqttException, GeneralSecurityException {
        connect(buildOptions());
    }

    /** Kendi ayarlarini vermek isteyenler icin. */
    public void connect(MqttConnectOptions options) throws MqttException {
        String serverUri = "ssl://" + ip + ":" + PORT;

        // MemoryPersistence: Paho varsayilan olarak diske "paho-..." klasoru
        // aciyor. Bize gerekmiyor, her sey bellekte dursun.
        client = new MqttClient(serverUri, MqttClient.generateClientId(), new MemoryPersistence());
        client.setCallback(this);
        client.connect(options);

        // Ilk baglantinin aboneligini burada kuruyoruz. Yeniden baglanmalarda
        // ayni isi connectComplete() yapacak (asagiya bak).
        subscribeAndRefresh();
    }

    /** Varsayilan baglanti ayarlari. */
    public MqttConnectOptions buildOptions() throws GeneralSecurityException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(USERNAME);
        options.setPassword(accessCode.toCharArray());

        // Oturum kaliciligina guvenmiyoruz: yazicinin gomulu broker'i
        // destekliyor mu bilmiyoruz. Her baglantida sifirdan basliyoruz ve
        // abonelikleri kendimiz kuruyoruz.
        options.setCleanSession(true);

        // Baglanti koparsa Paho kendisi geri baglanir.
        options.setAutomaticReconnect(true);

        // Yazicinin sertifikasi self-signed ve SAN alani bos. Java'nin iki
        // ayri kontrolunu de kapatmak gerekiyor; biri yetmiyor.
        options.setHttpsHostnameVerificationEnabled(false);   // hostname kontrolu
        options.setSocketFactory(TlsUtil.trustAllSocketFactory());   // guven zinciri

        return options;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public void disconnect() throws MqttException {
        if (client == null) {
            return;
        }
        if (client.isConnected()) {
            client.disconnect();
        }
        client.close();
    }

    // ==================== DURUM ====================

    /** Yazicidan gelen son durum. Henuz mesaj gelmediyse null. */
    public PrinterStatus getStatus() {
        return lastStatus;
    }

    /** Son mesajin geldigi an (System.currentTimeMillis). Hic gelmediyse 0. */
    public long getLastUpdateMillis() {
        return lastUpdateMillis;
    }

    /**
     * Yazicidan verilen sure boyunca hic haber gelmedi mi?
     *
     * NEDEN GEREKLI: isConnected() true olsa bile yazici susmus olabilir.
     * Panelde "canli" yazip bayat veri gostermemek icin bu kontrol kullanilir.
     */
    public boolean isStale(int seconds) {
        if (lastUpdateMillis == 0) {
            return true;
        }
        return System.currentTimeMillis() - lastUpdateMillis > seconds * 1000L;
    }

    /**
     * Her yeni durum mesajinda calisacak dinleyici.
     * Ornek: RUNNING -> FINISH gecisini yakalayip siparisi tamamlandi isaretlemek.
     *
     * DIKKAT: Dinleyici Paho'nun thread'inde calisir. Icinde uzun is yapma;
     * o sirada gelen diger mesajlar sirada bekler.
     */
    public void setStatusListener(Consumer<PrinterStatus> listener) {
        this.statusListener = listener;
    }

    // ==================== KOMUTLAR ====================

    /**
     * Yaziciya "tam durumunu gonder" der. Salt okunur; baskiya dokunmaz.
     * Bambu Studio da acilista ayni komutu gonderiyor.
     */
    public void requestFullStatus() throws MqttException {
        sendCommand(Commands.pushAll(nextSequenceId()));
    }

    /** Kabin isigini acar/kapatir. Zararsiz oldugu icin baglantiyi denemenin en iyi yolu. */
    public void setLight(boolean on) throws MqttException {
        sendCommand(Commands.light(nextSequenceId(), on));
    }

    public void pause() throws MqttException {
        sendCommand(Commands.pause(nextSequenceId()));
    }

    public void resume() throws MqttException {
        sendCommand(Commands.resume(nextSequenceId()));
    }

    /** Baskiyi tamamen durdurur. Geri alinamaz. */
    public void stop() throws MqttException {
        sendCommand(Commands.stop(nextSequenceId()));
    }

    /**
     * Hazir metotlarda olmayan bir komutu elle gondermek icin.
     * JSON'u kendin kurarsin, sequence_id'yi de kendin koyarsin.
     */
    public void sendCommand(String json) throws MqttException {
        if (client == null || !client.isConnected()) {
            throw new IllegalStateException("Yaziciya bagli degil, once connect() cagir.");
        }
        // QoS 1: komut kaybolmasin. retain=false: komut kutuda KALMAMALI,
        // yoksa yazici aylar sonra yeniden baglandiginda o eski komutu alir.
        client.publish(requestTopic(), json.getBytes(StandardCharsets.UTF_8), 1, false);
    }

    private int nextSequenceId() {
        return sequenceId.incrementAndGet();
    }

    // ==================== PAHO CALLBACK'LERI ====================

    /**
     * Baglanti kurulunca (ilk kez VEYA yeniden) Paho burayi cagirir.
     *
     * BURASI KRITIK: cleanSession=true oldugu icin baglanti koptugunda
     * abonelikler broker tarafinda silinir. Yeniden baglaninca abone olmayi
     * unutursak istemci "bagli" gorunur ama tek bir mesaj bile gelmez;
     * getStatus() sessizce eski veriyi dondurmeye devam eder.
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (!reconnect) {
            return;   // ilk baglanti: abonelik connect() icinde kuruldu
        }
        log("Yaziciya yeniden baglanildi, abonelik kuruluyor.");
        try {
            subscribeAndRefresh();
        } catch (MqttException e) {
            // Buradan exception firlatamayiz (Paho'nun thread'indeyiz),
            // o yuzden sadece bildiriyoruz.
            log("Yeniden abone olunamadi: " + e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log("Yazici baglantisi koptu: " + (cause == null ? "sebep yok" : cause.getMessage()));
    }

    /** Yazicidan her mesaj geldiginde calisir. */
    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String json = new String(message.getPayload(), StandardCharsets.UTF_8);
        PrinterStatus parsed = parse(json);
        if (parsed == null) {
            return;   // durum mesaji degildi (komut cevabi, log vs.)
        }

        lastStatus = parsed;
        lastUpdateMillis = System.currentTimeMillis();

        Consumer<PrinterStatus> listener = statusListener;
        if (listener != null) {
            try {
                listener.accept(parsed);
            } catch (Exception e) {
                // Kullanicinin dinleyicisi patlarsa kutuphane calismaya devam etsin.
                log("Durum dinleyicisi hata verdi: " + e.getMessage());
            }
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Sadece yayin yaparken gerekir, bizim icin yapacak bir sey yok.
    }

    // ==================== IC ISLER ====================

    /** Abone ol, sonra tam durumu iste. Sira onemli: once abone, sonra istek. */
    private void subscribeAndRefresh() throws MqttException {
        client.subscribe(reportTopic(), 1);
        requestFullStatus();
    }

    /**
     * Gelen JSON su sekilde:  { "print": { "gcode_state": ..., ... } }
     * PrinterStatus ic kisma karsilik geliyor, once "print" acilmali.
     * "print" yoksa (baska tur mesaj) null doner ve son durum korunur.
     */
    private PrinterStatus parse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode printNode = root.get("print");
            if (printNode == null) {
                return null;
            }
            return mapper.treeToValue(printNode, PrinterStatus.class);
        } catch (Exception e) {
            // Bozuk/beklenmedik bir mesaj butun akisi durdurmasin.
            log("Mesaj cozumlenemedi: " + e.getMessage());
            return null;
        }
    }

    private String reportTopic() {
        return "device/" + serial + "/report";
    }

    private String requestTopic() {
        return "device/" + serial + "/request";
    }

    /**
     * Basit log. Gercek bir projede SLF4J kullanilir; burada kutuphaneye
     * fazladan bagimlilik eklememek icin System.err yeterli.
     *
     * DIKKAT: Gelen JSON'u loglamiyoruz - icinde yazicinin seri numarasi var.
     */
    private void log(String message) {
        System.err.println("[bambu-java] " + message);
    }
}
