package com.example.rhythmmini;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Note> notes = new ArrayList<>();
    private final Random rnd = new Random();

    private int score = 0;
    private int miss = 0;

    // レーン/ノーツ寸法
    private int laneCount = 4;
    private float laneWidth;
    private float noteW, noteH;
    private float hitLineY; // 画面下から少し上

    // 判定
    private float hitWindowPx;
    private float flickDistancePx;
    private long flickTimeMs;

    // 入力追跡
    private float downX, downY;
    private long downTime;
    private boolean isDown;

    public GameView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        setFocusable(true);
        setClickable(true);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        hitWindowPx = dp(42, dm);          // 判定許容
        flickDistancePx = dp(80, dm);      // フリック閾値
        flickTimeMs = 220;                 // フリックは素早く

        // 初期ノーツ少量
        post(this::seedNotes);
        // ゲームループ
        post(frameRunnable);
    }

    private float dp(float v, DisplayMetrics dm) { return v * dm.density; }

    private final Runnable frameRunnable = new Runnable() {
        @Override public void run() {
            update();
            invalidate();
            postDelayed(this, 16); // ~60fps
        }
    };

    private void seedNotes() {
        // ある程度の数を上の方にばら撒く
        for (int i = 0; i < 24; i++) {
            spawnNote(-dp(80, getResources().getDisplayMetrics()) * i);
        }
    }

    private void spawnNote(float startY) {
        int lane = rnd.nextInt(laneCount);

        // タイプ分布：Normal 70% / Critical 20% / Flick 10%
        int r = rnd.nextInt(100);
        Note.Type type;
        int color;
        if (r < 10) { // FLICK
            type = Note.Type.FLICK;
            color = Color.rgb(255, 140, 0); // オレンジ
        } else if (r < 30) { // CRITICAL
            type = Note.Type.CRITICAL;
            color = Color.YELLOW;
        } else {
            type = Note.Type.NORMAL;
            color = Color.rgb(90, 160, 255); // ブルー
        }

        float speed = dp(3.5f, getResources().getDisplayMetrics()) + rnd.nextFloat() * dp(1.5f, getResources().getDisplayMetrics());
        notes.add(new Note(lane, startY, noteW, noteH, speed, type, color));
    }

    private void update() {
        // 寸法が未算出なら算出
        if (laneWidth == 0) {
            laneWidth = getWidth() / (float) laneCount;
            noteW = laneWidth * 0.65f;
            noteH = dp(26, getResources().getDisplayMetrics());
            hitLineY = getHeight() - dp(120, getResources().getDisplayMetrics());
        }

        // 落下＆ロスト
        Iterator<Note> it = notes.iterator();
        while (it.hasNext()) {
            Note n = it.next();
            n.update();
            if (n.y > getHeight() + noteH) {
                it.remove();
                miss++;
            }
        }

        // 足りなければ追加
        while (notes.size() < 24) {
            float top = -dp(200, getResources().getDisplayMetrics()) - rnd.nextFloat() * dp(500, getResources().getDisplayMetrics());
            spawnNote(top);
        }
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        // 背景
        c.drawColor(Color.BLACK);

        // レーン線
        paint.setColor(Color.DKGRAY);
        paint.setStrokeWidth(dp(1, getResources().getDisplayMetrics()));
        for (int i = 1; i < laneCount; i++) {
            float x = i * laneWidth;
            c.drawLine(x, 0, x, getHeight(), paint);
        }
        // ヒットライン
        paint.setColor(Color.GRAY);
        c.drawLine(0, hitLineY, getWidth(), hitLineY, paint);

        // ノーツ描画
        for (Note n : notes) {
            n.draw(c, 0, laneWidth, paint);
        }

        // スコア表示
        paint.setColor(Color.WHITE);
        paint.setTextSize(dp(18, getResources().getDisplayMetrics()));
        c.drawText("Score:" + score + "  Miss:" + miss, dp(12, getResources().getDisplayMetrics()), dp(28, getResources().getDisplayMetrics()), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isDown = true;
                downX = e.getX();
                downY = e.getY();
                downTime = System.currentTimeMillis();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDown) {
                    float upX = e.getX();
                    float upY = e.getY();
                    long dt = System.currentTimeMillis() - downTime;
                    float dx = upX - downX;
                    float dy = upY - downY;
                    float dist = (float)Math.hypot(dx, dy);
                    boolean isFlickGesture = (dt <= flickTimeMs) && (dist >= flickDistancePx);

                    int lane = (int) Math.floor(upX / laneWidth);
                    if (lane < 0) lane = 0;
                    if (lane >= laneCount) lane = laneCount - 1;

                    handleHit(lane, isFlickGesture);
                }
                isDown = false;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void handleHit(int lane, boolean isFlickGesture) {
        // lane内でヒットウィンドウにいる最も近いノーツを探す
        Note target = null;
        float bestDist = Float.MAX_VALUE;

        for (Note n : notes) {
            if (n.lane != lane) continue;
            if (!n.isHittable(hitLineY, hitWindowPx)) continue;

            float d = Math.abs((n.y + noteH/2f) - hitLineY);
            if (d < bestDist) {
                bestDist = d;
                target = n;
            }
        }

        if (target == null) {
            // 近くに無い → ミスとして扱うか無視するか。ここは無視にしてもOK
            miss++;
            return;
        }

        // 種類別の入力要求
        if (target.type == Note.Type.FLICK) {
            if (!isFlickGesture) {
                // フリックじゃなかった → ミスにして消さない（再挑戦可）
                miss++;
                return;
            }
        } else {
            // NORMAL/CRITICAL はタップ想定：フリックでもOKにするなら何もしない
        }

        // ヒット成功
        score += target.scoreValue();
        notes.remove(target);
    }
}
