package com.xhhao.insight.config;

@lombok.Getter
@lombok.Setter
public class AlertSettings {

    private double gcWarningPercent = 10;
    private boolean advancedEnabled = true;

    private boolean enabled = true;
    private int durationSeconds = 60;
    private int cooldownMinutes = 30;
    private double cpuWarningPercent = 85;
    private double heapWarningPercent = 85;
    private double diskWarningPercent = 90;
    private double errorRatePercent = 5;
    private long p95WarningMillis = 1_500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getCooldownMinutes() {
        return cooldownMinutes;
    }

    public void setCooldownMinutes(int cooldownMinutes) {
        this.cooldownMinutes = cooldownMinutes;
    }

    public double getCpuWarningPercent() {
        return cpuWarningPercent;
    }

    public void setCpuWarningPercent(double cpuWarningPercent) {
        this.cpuWarningPercent = cpuWarningPercent;
    }

    public double getHeapWarningPercent() {
        return heapWarningPercent;
    }

    public void setHeapWarningPercent(double heapWarningPercent) {
        this.heapWarningPercent = heapWarningPercent;
    }

    public double getDiskWarningPercent() {
        return diskWarningPercent;
    }

    public void setDiskWarningPercent(double diskWarningPercent) {
        this.diskWarningPercent = diskWarningPercent;
    }

    public double getErrorRatePercent() {
        return errorRatePercent;
    }

    public void setErrorRatePercent(double errorRatePercent) {
        this.errorRatePercent = errorRatePercent;
    }

    public long getP95WarningMillis() {
        return p95WarningMillis;
    }

    public void setP95WarningMillis(long p95WarningMillis) {
        this.p95WarningMillis = p95WarningMillis;
    }
}
