package com.example.rhythmmini;

import android.graphics.Canvas;
import android.graphics.Paint;

public class Note {

    public enum Type {
        NORMAL,
        CRITICAL,
        FLICK
    }

    public final int lane;     // 0..3
    public float y;
    public float speed;
    public final Type type;

    private final float w, h;
    private final int color;   // ← @ColorInt を外して素の int に

    public Note(int lane, float startY, float w, float h, float speed, Type type, int color) {
        this.lane = lane;
        this.y = startY;
        this.w = w;
        this.h = h;
        this.speed = speed;
        this.type = type;
        this.color = color;
    }

    public void update() {
        y += speed;
    }

    public void draw(Canvas c, float laneLeftX, float laneWidth, Paint p) {
        p.setColor(color);
        float left = laneLeftX + lane * laneWidth + (laneWidth - w) / 2f;
        float top = y;
        c.drawRoundRect(left, top, left + w, top + h, 16f, 16f, p);
    }

    public int scoreValue() {
        return (type == Type.CRITICAL) ? 2000 : 1000;
    }

    public boolean isHittable(float hitLineY, float windowPx) {
        return Math.abs((y + h/2f) - hitLineY) <= windowPx;
    }
}
