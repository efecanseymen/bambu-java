package com.efecanseymen.bambu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Komut metinleri dogru kuruluyor mu?
 * Bu testler yazici olmadan calisir - Commands sinifi sadece metin uretiyor.
 */
class CommandsTest {

    @Test
    void pushAll_dogru_komutu_uretir() {
        String json = Commands.pushAll(0);
        assertTrue(json.contains("\"command\":\"pushall\""), json);
        assertTrue(json.contains("\"pushing\""), json);
    }

    @Test
    void isik_acma_ve_kapatma_farkli_led_mode_uretir() {
        assertTrue(Commands.light(1, true).contains("\"led_mode\":\"on\""));
        assertTrue(Commands.light(1, false).contains("\"led_mode\":\"off\""));
        assertTrue(Commands.light(1, true).contains("\"led_node\":\"chamber_light\""));
    }

    @Test
    void baski_komutlari_print_altinda_gonderilir() {
        assertTrue(Commands.pause(2).contains("\"print\""));
        assertTrue(Commands.pause(2).contains("\"command\":\"pause\""));
        assertTrue(Commands.resume(3).contains("\"command\":\"resume\""));
        assertTrue(Commands.stop(4).contains("\"command\":\"stop\""));
    }

    @Test
    void sequence_id_metne_yazilir() {
        // Yazicinin cevabini komutla eslestirebilmek icin numara JSON'da olmali.
        assertTrue(Commands.pause(42).contains("\"sequence_id\":\"42\""), Commands.pause(42));
    }
}
