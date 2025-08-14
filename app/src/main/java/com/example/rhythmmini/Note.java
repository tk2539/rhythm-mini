package com.example.rhythmmini;

import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.ColorInt;

public class Note {

    public enum Type {
        NORMAL,     // 既存（青/紫など）
        CRITICAL,   // クリティカル（黄色・得点2倍）
        FLICK       // フリック必須（オレンジ）
    }

    public final int lane;     // 0..3
    public float y;            // 上からの位置(px)
    public float speed;        // 落下速度(px/frame)
    public final Type type;

    private final float w, h;
    @ColorInt private final int color;

    public Note(int lane, float startY, float w, float h, float speed, Type type, @ColorInt int color) {
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

    /** ベース1000点。CRITICALは2倍。 */
    public int scoreValue() {
        return (type == Type.CRITICAL) ? 2000 : 1000;
    }

    /** 判定ライン付近かどうか（単純な距離判定） */
    public boolean isHittable(float hitLineY, float windowPx) {
        return Math.abs((y + h/2f) - hitLineY) <= windowPx;
    }
}
