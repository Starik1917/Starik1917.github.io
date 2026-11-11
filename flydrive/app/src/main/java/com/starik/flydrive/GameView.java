package com.starik.flydrive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private final SurfaceHolder holder;
    private Thread thread;
    private volatile boolean running;
    private volatile boolean paused;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private BrainModel brain;
    private World world;
    private Trainer trainer;
    private String loadError;
    private String metaLine = "MaleCNS v1.0";
    private int simSpeed = 1;
    private BrainModel.Output brainOut = new BrainModel.Output();
    private float brainAccumulator;
    private float fps;
    private long lastFrameNs;

    GameView(Context c) {
        super(c);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        new Thread(() -> {
            try {
                JSONObject meta = new JSONObject(readAsset("malecns_meta.json"));
                metaLine = meta.optInt("neurons", 0) + " neurons • " + meta.optInt("edges", 0) + " signed edges • MaleCNS v1.0";
                brain = new BrainModel(getContext());
                world = new World();
                trainer = new Trainer(brain);
            } catch (Throwable t) {
                loadError = t.getClass().getSimpleName() + ": " + t.getMessage();
            }
        }, "brain-loader").start();
    }

    private String readAsset(String name) throws Exception {
        InputStream in = getContext().getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192]; int r;
        while ((r = in.read(b)) > 0) out.write(b, 0, r);
        in.close(); return out.toString("UTF-8");
    }

    @Override public void surfaceCreated(SurfaceHolder h) { resumeLoop(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int he) {}
    @Override public void surfaceDestroyed(SurfaceHolder h) { pauseLoop(); }

    void resumeLoop() {
        if (running) return;
        running = true;
        thread = new Thread(this, "flydrive-render");
        thread.start();
    }
    void pauseLoop() {
        running = false;
        Thread t = thread;
        if (t != null) try { t.join(300); } catch (InterruptedException ignored) {}
        thread = null;
    }

    @Override public void run() {
        lastFrameNs = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float realDt = MathUtil.clamp((now - lastFrameNs) / 1_000_000_000f, 0.001f, 0.050f);
            lastFrameNs = now;
            fps = fps * 0.90f + (1f / realDt) * 0.10f;
            if (!paused && brain != null && world != null && trainer != null) {
                float remaining = realDt * simSpeed;
                int steps = 0;
                while (remaining > 0f && steps < 32) {
                    float dt = Math.min(remaining, 1f / 60f);
                    simulate(dt);
                    remaining -= dt;
                    steps++;
                }
            }
            Canvas c = holder.lockCanvas();
            if (c != null) {
                try { drawAll(c); } finally { holder.unlockCanvasAndPost(c); }
            }
            if (simSpeed == 1) SystemClock.sleep(5);
        }
    }

    private void simulate(float dt) {
        brainAccumulator += dt;
        if (brainAccumulator >= BrainModel.DT) {
            World.Sensors s = world.sensors();
            brainOut = brain.step(s.laneError, s.headingError, s.threat, s.red, s.green, s.speedNorm);
            brainAccumulator -= BrainModel.DT;
        }
        float reward = world.step(dt, brainOut);
        boolean episodeEnd = trainer.addReward(reward, dt);
        if (episodeEnd) world.reset();
    }

    private void drawAll(Canvas c) {
        c.drawColor(Color.rgb(9, 12, 15));
        int w = c.getWidth(), h = c.getHeight();
        if (brain == null || world == null || trainer == null) {
            p.setColor(Color.WHITE); p.setTextSize(28f);
            c.drawText(loadError == null ? "Preparing MaleCNS…" : "Load failed: " + loadError, 36, 58, p);
            p.setTextSize(17f); p.setColor(Color.LTGRAY);
            c.drawText("This APK contains a sparse graph built from the official MaleCNS v1.0 connectome.", 36, 92, p);
            return;
        }

        float scale = Math.min(1.45f, Math.max(0.72f, h / 760f));
        float camY = world.y - h / scale * 0.68f;
        drawTerrain(c, w, h, scale, camY);
        drawRoad(c, w, h, scale, camY);
        drawIntersections(c, w, h, scale, camY);
        drawTraffic(c, w, h, scale, camY);
        drawPlayer(c, w, h, scale, camY);
        drawHud(c, w, h);
    }

    private float sx(float worldX, int w, float scale) { return w * 0.52f + worldX * scale; }
    private float sy(float worldY, int h, float scale, float camY) { return h - (worldY - camY) * scale; }

    private void drawTerrain(Canvas c, int w, int h, float scale, float camY) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(21, 29, 24));
        c.drawRect(0, 0, w, h, p);
        int k0 = (int)Math.floor(camY / 260f) - 2;
        int k1 = k0 + 10;
        for (int k = k0; k <= k1; k++) {
            float yy = k * 260f + 90f;
            float center = world.roadCenter(yy);
            for (int side = -1; side <= 1; side += 2) {
                float bw = 90f + MathUtil.hash01(k*91L + side*17L) * 110f;
                float bh = 65f + MathUtil.hash01(k*101L + side*31L) * 120f;
                float bx = center + side * (World.ROAD_W*0.5f + 65f + bw*0.5f);
                float x = sx(bx, w, scale), y = sy(yy, h, scale, camY);
                p.setColor(Color.rgb(34 + (k&7), 39 + (k&5), 43 + (k&3)));
                c.drawRect(x-bw*scale/2, y-bh*scale/2, x+bw*scale/2, y+bh*scale/2, p);
            }
        }
    }

    private void drawRoad(Canvas c, int w, int h, float scale, float camY) {
        float y0 = camY - 120f;
        float y1 = camY + h / scale + 160f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(World.ROAD_W * scale);
        p.setColor(Color.rgb(48, 51, 54));
        path.reset();
        boolean first = true;
        for (float yy = y0; yy <= y1; yy += 20f) {
            float x = sx(world.roadCenter(yy), w, scale), y = sy(yy, h, scale, camY);
            if (first) { path.moveTo(x,y); first=false; } else path.lineTo(x,y);
        }
        c.drawPath(path, p);
        p.setStrokeWidth(2.2f * scale);
        p.setColor(Color.rgb(230, 230, 220));
        path.reset(); first=true;
        for (float yy = y0; yy <= y1; yy += 20f) {
            float x = sx(world.roadCenter(yy), w, scale), y = sy(yy, h, scale, camY);
            if (first) { path.moveTo(x,y); first=false; } else path.lineTo(x,y);
        }
        c.drawPath(path,p);

        p.setColor(Color.rgb(202, 202, 185));
        p.setStrokeWidth(1.4f*scale);
        for (int li : new int[]{-2,-1,1,2}) {
            float off = li * World.LANE;
            path.reset(); first=true;
            int dash = 0;
            for (float yy = y0; yy <= y1; yy += 18f) {
                dash++;
                if ((dash/3)%2==1) { first=true; continue; }
                float x = sx(world.roadCenter(yy)+off, w, scale), y = sy(yy, h, scale, camY);
                if (first) { path.moveTo(x,y); first=false; } else path.lineTo(x,y);
            }
            c.drawPath(path,p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawIntersections(Canvas c, int w, int h, float scale, float camY) {
        int k = world.nearestIntersectionIndex(camY);
        for (int i = k-2; i <= k+4; i++) {
            float iy = world.intersectionY(i);
            float py = sy(iy, h, scale, camY);
            if (py < -200 || py > h+200) continue;
            float cx = sx(world.roadCenter(iy), w, scale);
            p.setColor(Color.rgb(48, 51, 54));
            c.drawRect(0, py-World.ROAD_W*0.46f*scale, w, py+World.ROAD_W*0.46f*scale, p);
            boolean red = world.isRed(i);
            p.setColor(red ? Color.rgb(244,72,72) : Color.rgb(74,224,121));
            c.drawCircle(cx + World.ROAD_W*0.56f*scale, py + 34f*scale, 10f*scale, p);
            p.setColor(Color.WHITE);
            c.drawRect(cx-World.ROAD_W*0.45f*scale, py+42f*scale, cx+World.ROAD_W*0.45f*scale, py+46f*scale, p);
        }
    }

    private void drawTraffic(Canvas c, int w, int h, float scale, float camY) {
        for (World.TrafficCar car : world.traffic()) {
            float x = sx(car.x,w,scale), y = sy(car.y,h,scale,camY);
            if (x<-60||x>w+60||y<-80||y>h+80) continue;
            p.setColor(car.dir>0 ? Color.rgb(84,145,234) : Color.rgb(226,141,72));
            c.save(); c.rotate(car.dir>0 ? 0 : 180, x,y);
            c.drawRoundRect(new RectF(x-14*scale,y-25*scale,x+14*scale,y+25*scale),5*scale,5*scale,p);
            p.setColor(Color.rgb(190,220,240));
            c.drawRect(x-9*scale,y-13*scale,x+9*scale,y-5*scale,p);
            c.restore();
        }
    }

    private void drawPlayer(Canvas c, int w, int h, float scale, float camY) {
        float x=sx(world.x,w,scale), y=sy(world.y,h,scale,camY);
        c.save(); c.rotate((float)Math.toDegrees(-world.angle),x,y);
        p.setColor(Color.rgb(74,239,180));
        c.drawRoundRect(new RectF(x-16*scale,y-28*scale,x+16*scale,y+28*scale),6*scale,6*scale,p);
        p.setColor(Color.rgb(8,34,28));
        c.drawRect(x-10*scale,y-12*scale,x+10*scale,y-2*scale,p);
        c.restore();
    }

    private void drawHud(Canvas c, int w, int h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(205, 9,12,15));
        c.drawRoundRect(new RectF(18,18,490,172),18,18,p);
        p.setColor(Color.WHITE); p.setTextSize(22);
        c.drawText("FLYDRIVE / MaleCNS",34,48,p);
        p.setColor(Color.rgb(170,183,190)); p.setTextSize(14);
        c.drawText(metaLine,34,70,p);
        p.setColor(Color.rgb(225,230,232)); p.setTextSize(16);
        c.drawText(String.format("gen %d  candidate %d/%d   fitness %.1f   best %.1f",
                trainer.generation, trainer.candidateNumber(), trainer.populationSize,
                trainer.lastFitness, trainer.bestFitness),34,99,p);
        c.drawText(String.format("speed %.0f  progress %.0f  crash %d  red %d  offroad %d",
                world.speed, world.totalProgress, world.collisions, world.redViolations, world.offroadEvents),34,124,p);
        c.drawText(String.format("DNa L %.1f  R %.1f   DNp09 %.1f  DNp01 %.1f   net %.2f Hz",
                brainOut.dNaLeft,brainOut.dNaRight,brainOut.freeze,brainOut.escape,brainOut.netHz),34,149,p);

        float bx=w-390;
        drawButton(c,bx,22,105,50, paused?"RESUME":"PAUSE");
        drawButton(c,bx+116,22,110,50,simSpeed+"×");
        drawButton(c,bx+237,22,135,50,"RESET WORLD");
        p.setColor(Color.argb(180,9,12,15));
        c.drawRoundRect(new RectF(w-390,84,w-18,150),16,16,p);
        p.setColor(Color.rgb(160,170,178));p.setTextSize(13);
        c.drawText("steer",w-374,105,p); c.drawText("throttle",w-374,128,p); c.drawText("brake",w-374,147,p);
        bar(c,w-310,94,260,10,(brainOut.steer+1f)/2f,Color.rgb(74,239,180));
        bar(c,w-310,117,260,10,brainOut.throttle,Color.rgb(86,155,245));
        bar(c,w-310,139,260,10,brainOut.brake,Color.rgb(245,92,92));

        p.setColor(Color.rgb(130,140,145));p.setTextSize(12);
        c.drawText("Connectome wiring fixed; evolution changes only synaptic gains. Sensory mapping is an engineered interface, not a claim that flies understand traffic lights.",22,h-18,p);
    }

    private void drawButton(Canvas c,float x,float y,float ww,float hh,String text){
        p.setColor(Color.rgb(35,42,48));c.drawRoundRect(new RectF(x,y,x+ww,y+hh),13,13,p);
        p.setColor(Color.WHITE);p.setTextSize(14);p.setTextAlign(Paint.Align.CENTER);
        c.drawText(text,x+ww/2,y+31,p);p.setTextAlign(Paint.Align.LEFT);
    }
    private void bar(Canvas c,float x,float y,float ww,float hh,float v,int color){
        p.setColor(Color.rgb(34,39,43));c.drawRoundRect(new RectF(x,y,x+ww,y+hh),hh/2,hh/2,p);
        p.setColor(color);c.drawRoundRect(new RectF(x,y,x+ww*MathUtil.clamp(v,0,1),y+hh),hh/2,hh/2,p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction()!=MotionEvent.ACTION_UP) return true;
        float x=e.getX(), y=e.getY(); int w=getWidth();
        if (y<85 && x>w-410) {
            float bx=w-390;
            if (x<bx+110) paused=!paused;
            else if (x<bx+232) simSpeed = simSpeed==1?4:simSpeed==4?16:1;
            else if (world!=null) world.reset();
            return true;
        }
        return true;
    }
}
