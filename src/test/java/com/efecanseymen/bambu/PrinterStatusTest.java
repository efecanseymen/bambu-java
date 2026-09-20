package com.efecanseymen.bambu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Yazicidan gelen JSON dogru okunuyor mu?
 *
 * NOT: Buradaki ornek JSON gercek bir dump degil, elle kisaltilmis bir ornek.
 * Gercek dump'ta yazicinin seri numarasi geciyor, o yuzden repoya konmaz.
 */
class PrinterStatusTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String ORNEK_JSON = """
            {
              "gcode_state": "RUNNING",
              "mc_percent": 42,
              "mc_remaining_time": 17,
              "subtask_name": "parca.3mf",
              "layer_num": 120,
              "total_layer_num": 300,
              "nozzle_temper": 219.5,
              "nozzle_target_temper": 220.0,
              "bed_temper": 59.8,
              "bed_target_temper": 60.0,
              "wifi_signal": "-63dBm",
              "nozzle_diameter": "0.4",
              "hms": [],
              "bilmedigimiz_alan": 123
            }
            """;

    @Test
    void temel_alanlar_okunur() throws Exception {
        PrinterStatus status = mapper.readValue(ORNEK_JSON, PrinterStatus.class);

        assertEquals("RUNNING", status.getGcodeState());
        assertEquals(42, status.getMcPercent());
        assertEquals(17, status.getMcRemainingTime());
        assertEquals(120, status.getLayerNum());
        assertEquals(300, status.getTotalLayerNum());
        assertEquals(219.5, status.getNozzleTemper());
        assertEquals(60.0, status.getBedTargetTemper());
    }

    @Test
    void bilinmeyen_alanlar_hata_vermez() throws Exception {
        // Yazici ~99 alan gonderiyor, biz 13'unu modelliyoruz.
        // @JsonIgnoreProperties olmasaydi bu test patlardi.
        PrinterStatus status = mapper.readValue(ORNEK_JSON, PrinterStatus.class);
        assertEquals("RUNNING", status.getGcodeState());
    }

    @Test
    void gelmeyen_alan_null_kalir() throws Exception {
        PrinterStatus status = mapper.readValue("{\"gcode_state\":\"IDLE\"}", PrinterStatus.class);
        assertNull(status.getMcPercent());   // int olsaydi 0 olurdu, "veri yok" ile karisirdi
    }

    @Test
    void finish_musait_sayilir() throws Exception {
        // gcode_state yapiskandir: baski bitince FINISH'te kalir, IDLE'a donmez.
        // Bu yuzden "== IDLE" kontrolu yanlis sonuc verir.
        PrinterStatus finish = mapper.readValue("{\"gcode_state\":\"FINISH\"}", PrinterStatus.class);
        assertTrue(finish.isAvailable());
        assertFalse(finish.isPrinting());

        PrinterStatus running = mapper.readValue("{\"gcode_state\":\"RUNNING\"}", PrinterStatus.class);
        assertFalse(running.isAvailable());
        assertTrue(running.isPrinting());
    }

    @Test
    void hms_dolu_ise_uyari_vardir() throws Exception {
        PrinterStatus temiz = mapper.readValue("{\"hms\":[]}", PrinterStatus.class);
        assertFalse(temiz.hasWarning());

        PrinterStatus uyarili = mapper.readValue("{\"hms\":[{\"code\":65543}]}", PrinterStatus.class);
        assertTrue(uyarili.hasWarning());
    }
}
