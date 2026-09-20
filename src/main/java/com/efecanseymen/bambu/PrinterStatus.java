package com.efecanseymen.bambu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Yazicinin o anki durumu.
 *
 * JSON'daki "print" nesnesinin icerigine karsilik gelir.
 * Yazici ~99 alan gonderiyor, burada sadece ihtiyac duyulanlar var;
 * geri kalanini @JsonIgnoreProperties yok sayiyor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrinterStatus {

    // ---- Baski ----

    @JsonProperty("gcode_state")
    private String gcodeState;

    @JsonProperty("mc_percent")
    private Integer mcPercent;

    @JsonProperty("mc_remaining_time")
    private Integer mcRemainingTime;

    @JsonProperty("subtask_name")
    private String subtaskName;

    @JsonProperty("layer_num")
    private Integer layerNum;

    @JsonProperty("total_layer_num")
    private Integer totalLayerNum;

    // ---- Sicaklik ----

    @JsonProperty("nozzle_temper")
    private Double nozzleTemper;

    @JsonProperty("nozzle_target_temper")
    private Double nozzleTargetTemper;

    @JsonProperty("bed_temper")
    private Double bedTemper;

    @JsonProperty("bed_target_temper")
    private Double bedTargetTemper;

    // ---- Hata ----

    @JsonProperty("hms")
    private List<Object> hms;

    // ---- Sistem ----

    @JsonProperty("wifi_signal")
    private String wifiSignal;

    @JsonProperty("nozzle_diameter")
    private String nozzleDiameter;

    // ---- Getter'lar ----

    public String getGcodeState() {
        return gcodeState;
    }

    public Integer getMcPercent() {
        return mcPercent;
    }

    public Integer getMcRemainingTime() {
        return mcRemainingTime;
    }

    public String getSubtaskName() {
        return subtaskName;
    }

    public Integer getLayerNum() {
        return layerNum;
    }

    public Integer getTotalLayerNum() {
        return totalLayerNum;
    }

    public Double getNozzleTemper() {
        return nozzleTemper;
    }

    public Double getNozzleTargetTemper() {
        return nozzleTargetTemper;
    }

    public Double getBedTemper() {
        return bedTemper;
    }

    public Double getBedTargetTemper() {
        return bedTargetTemper;
    }

    public List<Object> getHms() {
        return hms;
    }

    public String getWifiSignal() {
        return wifiSignal;
    }

    public String getNozzleDiameter() {
        return nozzleDiameter;
    }

    /**
     * Yazici yeni bir is alabilir mi?
     *
     * DIKKAT: gcode_state "yapiskan" bir alandir - baski bitince FINISH'te kalir,
     * IDLE'a donmez. Bu yuzden "== IDLE" kontrolu yanlis sonuc verir.
     */
    public boolean isAvailable() {
        return "IDLE".equals(gcodeState)
                || "FINISH".equals(gcodeState)
                || "FAILED".equals(gcodeState);
    }

    public boolean isPrinting() {
        return "RUNNING".equals(gcodeState) || "PREPARE".equals(gcodeState);
    }

    public boolean hasWarning() {
        return hms != null && !hms.isEmpty();
    }
}
