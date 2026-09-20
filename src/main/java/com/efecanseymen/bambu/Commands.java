package com.efecanseymen.bambu;

/**
 * Yaziciya gonderilen komutlarin JSON metinlerini uretir.
 *
 * NEDEN AYRI SINIF:
 *   Bu metotlar saf fonksiyon - ag baglantisi istemiyorlar, ayni girdiye hep
 *   ayni ciktiyi veriyorlar. Ayri durunca yazici olmadan test edilebiliyorlar.
 *
 * KOMUTLAR NEDEN BOYLE:
 *   Hepsi "su duruma gec" diyor, "su kadar degistir" demiyor. Buna idempotent
 *   denir: komut iki kez ulasirsa ikinci seferin etkisi yoktur. QoS 1'de mesaj
 *   tekrar gonderilebildigi icin bu onemli.
 */
final class Commands {

    private Commands() {
        // yardimci sinif, ornegi olusturulmaz
    }

    /** Tam durum iste. Salt okunur. */
    static String pushAll(int sequenceId) {
        return "{\"pushing\":{\"sequence_id\":\"" + sequenceId + "\",\"command\":\"pushall\"}}";
    }

    /** Kabin isigi. */
    static String light(int sequenceId, boolean on) {
        return "{\"system\":{\"sequence_id\":\"" + sequenceId + "\""
                + ",\"command\":\"ledctrl\""
                + ",\"led_node\":\"chamber_light\""
                + ",\"led_mode\":\"" + (on ? "on" : "off") + "\"}}";
    }

    static String pause(int sequenceId) {
        return printCommand(sequenceId, "pause");
    }

    static String resume(int sequenceId) {
        return printCommand(sequenceId, "resume");
    }

    static String stop(int sequenceId) {
        return printCommand(sequenceId, "stop");
    }

    /** Baski komutlarinin ortak bicimi. */
    private static String printCommand(int sequenceId, String command) {
        return "{\"print\":{\"sequence_id\":\"" + sequenceId + "\",\"command\":\"" + command + "\"}}";
    }
}
