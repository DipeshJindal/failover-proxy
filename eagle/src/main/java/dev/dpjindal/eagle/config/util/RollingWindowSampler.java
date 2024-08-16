package dev.dpjindal.eagle.config.util;

public class RollingWindowSampler {
    private double percentage;
    private volatile int windowMark;

    public RollingWindowSampler(int window, int percentage) {
        windowMark = window;
        this.percentage = percentage / 100.0;
    }
}
