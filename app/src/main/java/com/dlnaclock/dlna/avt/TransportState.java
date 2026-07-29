package com.dlnaclock.dlna.avt;

public enum TransportState {
    NO_MEDIA_PRESENT("NO_MEDIA_PRESENT"),
    STOPPED("STOPPED"),
    PLAYING("PLAYING"),
    PAUSED_PLAYBACK("PAUSED_PLAYBACK"),
    TRANSITIONING("TRANSITIONING");

    private final String value;

    TransportState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
