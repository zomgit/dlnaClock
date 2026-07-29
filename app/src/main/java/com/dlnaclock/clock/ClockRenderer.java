package com.dlnaclock.clock;

import android.graphics.Canvas;

import java.util.Calendar;

public interface ClockRenderer {
    void draw(Canvas canvas, int width, int height, Calendar time, ClockConfig config);
}
