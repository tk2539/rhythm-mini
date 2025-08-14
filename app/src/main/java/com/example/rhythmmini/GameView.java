package com.example.rhythmmini;
import android.content.Context; import android.graphics.*; import android.view.*;
import java.util.*; public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable{
  private Thread th; private boolean run=false; private final SurfaceHolder h; private final List<Note> notes=new ArrayList<>();
  private final Paint lane=new Paint(), note=new Paint(), txt=new Paint(); private final Random rng=new Random();
  private long last=0; private int score=0, miss=0; private float spd; private long intervalMs;
  public GameView(Context c){ super(c); h=getHolder(); h.addCallback(this);
    lane.setStyle(Paint.Style.STROKE); lane.setStrokeWidth(4);
    note.setStyle(Paint.Style.FILL); txt.setTextSize(48f); txt.setAntiAlias(true); txt.setColor(Color.WHITE); setFocusable(true);}
  @Override public void surfaceCreated(SurfaceHolder s){ int ph=getHeight(); spd=ph*0.6f; intervalMs=700; run=true; th=new Thread(this,"GameLoop"); th.start();}
  @Override public void surfaceChanged(SurfaceHolder s,int f,int w,int he){} @Override public void surfaceDestroyed(SurfaceHolder s){ run=false; try{ if(th!=null) th.join(); }catch(Exception ignored){}}
  @Override public void run(){ long p=System.nanoTime(); while(run){ long n=System.nanoTime(); float dt=(n-p)/1_000_000_000f; p=n; upd(dt); drawF(); try{Thread.sleep(16);}catch(Exception ignored){} } }
  private void upd(float dt){ long ms=System.currentTimeMillis(); if(ms-last>=intervalMs){ notes.add(new Note(rng.nextInt(4), -dp(32), spd)); last=ms; }
    int h=getHeight(); for(Iterator<Note> it=notes.iterator(); it.hasNext();){ Note x=it.next(); x.y+=x.speed*dt; if(x.y>h){ it.remove(); miss++; } } }
  private void drawF(){ Canvas c=h.lockCanvas(); if(c==null) return; try{
      int w=c.getWidth(), hh=c.getHeight(); c.drawColor(Color.BLACK);
      float laneW=w/4f; lane.setColor(Color.DKGRAY); for(int i=1;i<4;i++) c.drawLine(i*laneW,0,i*laneW,hh,lane);
      Paint j=new Paint(); j.setColor(Color.GRAY); j.setStrokeWidth(6); float jy=hh*0.85f; c.drawLine(0,jy,w,jy,j);
      float nh=dp(24); for(Note x:notes){ float L=x.lane*laneW+dp(8), R=(x.lane+1)*laneW-dp(8); RectF r=new RectF(L,x.y,R,x.y+nh);
        note.setColor(Color.rgb(80+x.lane*40,160,240)); c.drawRoundRect(r, dp(6), dp(6), note); }
      c.drawText("Score:"+score+"  Miss:"+miss, dp(12), dp(40), txt);
    } finally { h.unlockCanvasAndPost(c);} }
  @Override public boolean onTouchEvent(MotionEvent e){ if(e.getAction()!=MotionEvent.ACTION_DOWN) return true;
    float x=e.getX(), w=getWidth(), jy=getHeight()*0.85f, win=dp(36); int lane=(int)Math.min(3, Math.max(0, (int)(x/(w/4f))));
    Note best=null; float bd=Float.MAX_VALUE; for(Note n:notes){ if(n.lane!=lane) continue; float d=Math.abs(n.y-(jy-dp(24))); if(d<bd){bd=d; best=n;} }
    if(best!=null && Math.abs(best.y-(jy-dp(24)))<=win){ notes.remove(best); score+=100; } return true; }
  private float dp(float v){ return v*getResources().getDisplayMetrics().density; }
}
