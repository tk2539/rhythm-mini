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

    // ========= 描画・ゲーム状態 =========
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Note> notes = new ArrayList<>();
    private final Random rnd = new Random();

    private int score = 0;
    private int miss = 0;

    // ========= レイアウト関連 =========
    private final int laneCount = 4;
    private float laneWidth = 0f;
    private float noteW = 0f, noteH = 0f;
    private float hitLineY = 0f;
    private boolean seeded = false; // 初回ノーツ投入済みフラグ

    // ========= 判定関連 =========
    private float hitWindowPx;      // 判定許容（中心距離）
    private float flickDistancePx;  // フリック最小距離
    private long  flickTimeMs;      // フリック最長時間

    // ========= 入力追跡 =========
    private float downX, downY;
    private long  downTime;
    private boolean isDown;

    // ========== コンストラクタ ==========
    // Activity から new GameView(this) で呼ばれる想定
    public GameView(Context ctx) {
        this(ctx, null);
    }

    // XML/スタイル対応の本体コンストラクタ
    public GameView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        setFocusable(true);
        setClickable(true);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        hitWindowPx     = dp(42, dm);
        flickDistancePx = dp(80, dm);
        flickTimeMs     = 220;

        // ゲームループ起動
        post(frameLoop);
    }

    // 端末サイズが確定したら寸法を計算し、初回ノーツを投入
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        laneWidth = w / (float) laneCount;
        noteW     = laneWidth * 0.65f;
        noteH     = dp(26, getResources().getDisplayMetrics());
        hitLineY  = h - dp(120, getResources().getDisplayMetrics());

        if (!seeded && w > 0 && h > 0) {
            seedNotes();
            seeded = true;
        }
    }

    // 1フレームごとに更新＆再描画
    private final Runnable frameLoop = new Runnable() {
        @Override public void run() {
            update();
            invalidate();
            postDelayed(this, 16); // 約60fps
        }
    };

    // 初期ノーツばら撒き
    private void seedNotes() {
        for (int i = 0; i < 24; i++) {
            float gap = dp(80, getResources().getDisplayMetrics());
            spawnNote(-gap * (i + 1));
        }
    }

    // ノーツ生成（タイプ分布：NORMAL 70% / CRITICAL 20% / FLICK 10%）
    private void spawnNote(float startY) {
        int lane = rnd.nextInt(laneCount);

        Note.Type type;
        int color;
        int r = rnd.nextInt(100);
        if (r < 10) { // FLICK（オレンジ）
            type = Note.Type.FLICK;
            color = Color.rgb(255, 140, 0);
        } else if (r < 30) { // CRITICAL（黄色）
            type = Note.Type.CRITICAL;
            color = Color.YELLOW;
        } else { // NORMAL（青）
            type = Note.Type.NORMAL;
            color = Color.rgb(90, 160, 255);
        }

        float spd = dp(3.5f, getResources().getDisplayMetrics())
                  + rnd.nextFloat() * dp(1.5f, getResources().getDisplayMetrics());

        notes.add(new Note(lane, startY, noteW, noteH, spd, type, color));
    }

    // 毎フレーム更新
    private void update() {
        if (laneWidth <= 0f) return; // サイズ未確定なら待機

        // 落下＆画面外ロスト
        Iterator<Note> it = notes.iterator();
        while (it.hasNext()) {
            Note n = it.next();
            n.update();
            if (n.y > getHeight() + noteH) {
                it.remove();
                miss++;
            }
        }

        // ストック補充
        while (notes.size() < 24) {
            float top = -dp(200, getResources().getDisplayMetrics())
                      - rnd.nextFloat() * dp(500, getResources().getDisplayMetrics());
            spawnNote(top);
        }
    }

    // 描画
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
        c.drawText("Score:" + score + "  Miss:" + miss,
                dp(12, getResources().getDisplayMetrics()),
                dp(28, getResources().getDisplayMetrics()),
                paint);
    }

    // 入力処理（タップ＆フリック）
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
                    float dist = (float) Math.hypot(dx, dy);
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

    // ヒット判定・スコア更新
    private void handleHit(int lane, boolean isFlickGesture) {
        Note target = null;
        float bestDist = Float.MAX_VALUE;

        for (Note n : notes) {
            if (n.lane != lane) continue;
            if (!n.isHittable(hitLineY, hitWindowPx)) continue;

            float d = Math.abs((n.y + noteH / 2f) - hitLineY);
            if (d < bestDist) {
                bestDist = d;
                target = n;
            }
        }

        if (target == null) {
            // 近くに無い → ミス（または無視でもOK）
            miss++;
            return;
        }

        // 種類別の入力要求
        if (target.type == Note.Type.FLICK) {
            if (!isFlickGesture) {
                // フリックでない → 失敗（ノーツは残す）
                miss++;
                return;
            }
        }
        // NORMAL / CRITICAL はタップでOK（フリックでも可）

        // ヒット成功
        score += target.scoreValue();
        notes.remove(target);
    }

    // dp → px
    private float dp(float v, DisplayMetrics dm) {
        return v * dm.density;
    }
}
