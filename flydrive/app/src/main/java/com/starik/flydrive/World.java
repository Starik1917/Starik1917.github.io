package com.starik.flydrive;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

final class World {
    static final float ROAD_W = 310f;
    static final float LANE = 58f;
    static final float PLAYER_RADIUS = 19f;
    static final float TRAFFIC_RADIUS = 18f;
    static final float MAX_SPEED = 205f;

    static final class TrafficCar {
        float x, y, speed;
        int dir; // +1 or -1
        int laneIndex;
    }
    static final class Sensors {
        float laneError;
        float headingError;
        float threat;
        float red;
        float green;
        float speedNorm;
        float distanceToLight;
    }

    float x, y, angle, speed;
    float worldTime;
    float totalProgress;
    int collisions;
    int redViolations;
    int offroadEvents;
    int passedLights;
    private float lastY;
    private float offroadTimer;
    private int lastIntersectionIndex = Integer.MIN_VALUE;
    private boolean lastIntersectionWasRed;
    private final ArrayList<TrafficCar> traffic = new ArrayList<>();
    private final Random rng = new Random(20260914L);

    World() { reset(); }

    void reset() {
        y = 0f;
        x = roadCenter(y) + LANE;
        angle = 0f;
        speed = 45f;
        worldTime = 0f;
        totalProgress = 0f;
        collisions = redViolations = offroadEvents = passedLights = 0;
        lastY = y;
        offroadTimer = 0f;
        traffic.clear();
        for (int i = 0; i < 22; i++) spawnTraffic((i - 6) * 110f + rng.nextFloat() * 80f);
    }

    float step(float dt, BrainModel.Output brain) {
        worldTime += dt;
        float accel = brain.throttle * 150f - brain.brake * 260f - 16f;
        speed = MathUtil.clamp(speed + accel * dt, 0f, MAX_SPEED);
        float steerGain = 1.25f * (0.35f + speed / MAX_SPEED);
        angle += MathUtil.clamp(brain.steer, -1f, 1f) * steerGain * dt;
        angle *= (float)Math.pow(0.996, dt * 60f);
        angle = MathUtil.clamp(angle, -0.78f, 0.78f);
        x += (float)Math.sin(angle) * speed * dt;
        y += (float)Math.cos(angle) * speed * dt;

        float reward = 0f;
        float progress = y - lastY;
        lastY = y;
        totalProgress += Math.max(0f, progress);
        reward += Math.max(-0.3f, progress * 0.020f);

        float target = targetLaneX(y);
        float laneErrAbs = Math.abs(x - target);
        reward += MathUtil.clamp(1f - laneErrAbs / 105f, -0.6f, 1f) * dt * 1.6f;
        reward -= Math.abs(angle) * dt * 0.6f;
        if (speed < 8f) reward -= dt * 0.18f;

        boolean onRoad = Math.abs(x - roadCenter(y)) < ROAD_W * 0.5f;
        if (!onRoad) {
            offroadTimer += dt;
            reward -= dt * 4f;
            speed *= (float)Math.pow(0.94, dt * 60f);
            if (offroadTimer > 0.8f) {
                offroadEvents++;
                reward -= 18f;
                softResetCar();
            }
        } else offroadTimer = Math.max(0f, offroadTimer - dt * 2f);

        int ix = nearestIntersectionIndex(y);
        float iy = intersectionY(ix);
        float d = iy - y;
        boolean red = isRed(ix);
        if (d > 0f && d < 145f && red) {
            if (speed < 18f && d < 90f) reward += dt * 0.8f;
            if (lastIntersectionIndex != ix) {
                lastIntersectionIndex = ix;
                lastIntersectionWasRed = red;
            }
        }
        if (lastIntersectionIndex == ix && y > iy + 35f) {
            if (lastIntersectionWasRed) {
                redViolations++;
                reward -= 13f;
            } else {
                passedLights++;
                reward += 5f;
            }
            lastIntersectionIndex = Integer.MIN_VALUE;
        }

        updateTraffic(dt);
        for (TrafficCar c : traffic) {
            float dx = c.x - x, dy = c.y - y;
            if (dx*dx + dy*dy < (PLAYER_RADIUS + TRAFFIC_RADIUS)*(PLAYER_RADIUS + TRAFFIC_RADIUS)) {
                collisions++;
                reward -= 30f;
                softResetCar();
                break;
            }
        }
        return reward;
    }

    Sensors sensors() {
        Sensors s = new Sensors();
        float target = targetLaneX(y);
        s.laneError = MathUtil.clamp((x - target) / 95f, -1f, 1f);
        float desiredHeading = roadHeading(y);
        s.headingError = MathUtil.clamp((angle - desiredHeading) / 0.55f, -1f, 1f);
        s.speedNorm = speed / MAX_SPEED;

        float min = 999f;
        for (TrafficCar c : traffic) {
            float dy = c.y - y;
            if (dy <= -20f || dy > 280f) continue;
            float dx = Math.abs(c.x - x);
            if (dx < 75f) {
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                min = Math.min(min, dist);
            }
        }
        s.threat = min >= 280f ? 0f : MathUtil.clamp(1f - min / 230f, 0f, 1f);

        int ix = nearestIntersectionIndex(y);
        float d = intersectionY(ix) - y;
        s.distanceToLight = d;
        if (d > -15f && d < 250f) {
            boolean red = isRed(ix);
            float strength = MathUtil.clamp(1f - Math.abs(d) / 250f, 0f, 1f);
            s.red = red ? strength : 0f;
            s.green = red ? 0f : strength;
        }
        return s;
    }

    float roadCenter(float yy) {
        float a = 72f * (float)Math.sin(yy / 880f);
        float b = 44f * (float)Math.sin(yy / 1730f + 1.1f);
        float c = 22f * (float)Math.sin(yy / 410f + 0.6f);
        return a + b + c;
    }
    float roadHeading(float yy) {
        float d = 2.0f;
        float dxdy = (roadCenter(yy + d) - roadCenter(yy - d)) / (2f * d);
        return (float)Math.atan(dxdy);
    }
    float targetLaneX(float yy) { return roadCenter(yy) + LANE; }

    int nearestIntersectionIndex(float yy) {
        int k = (int)Math.floor(yy / 780f);
        float best = Float.MAX_VALUE;
        int bi = k;
        for (int i = k - 1; i <= k + 2; i++) {
            float d = Math.abs(intersectionY(i) - yy);
            if (d < best) { best = d; bi = i; }
        }
        return bi;
    }
    float intersectionY(int i) {
        return i * 780f + (MathUtil.hash01(i * 93847L + 71L) - 0.5f) * 130f;
    }
    boolean isRed(int i) {
        float phase = (worldTime + MathUtil.hash01(i * 7717L) * 11f) % 14f;
        return phase >= 8f;
    }

    private void softResetCar() {
        x = targetLaneX(y) + (rng.nextFloat() - 0.5f) * 8f;
        angle = roadHeading(y);
        speed = 32f;
        offroadTimer = 0f;
    }

    private void updateTraffic(float dt) {
        for (TrafficCar c : traffic) {
            c.y += c.dir * c.speed * dt;
            c.x = roadCenter(c.y) + c.dir * (c.laneIndex == 0 ? LANE : LANE * 1.72f);
        }
        Iterator<TrafficCar> it = traffic.iterator();
        while (it.hasNext()) {
            TrafficCar c = it.next();
            if (c.y < y - 650f || c.y > y + 1450f) it.remove();
        }
        while (traffic.size() < 22) spawnTraffic(y + 350f + rng.nextFloat() * 950f);
    }

    private void spawnTraffic(float yy) {
        TrafficCar c = new TrafficCar();
        c.dir = rng.nextFloat() < 0.72f ? 1 : -1;
        c.laneIndex = rng.nextBoolean() ? 0 : 1;
        c.y = yy;
        c.speed = c.dir > 0 ? 65f + rng.nextFloat()*80f : 85f + rng.nextFloat()*75f;
        c.x = roadCenter(c.y) + c.dir * (c.laneIndex == 0 ? LANE : LANE * 1.72f);
        traffic.add(c);
    }

    ArrayList<TrafficCar> traffic() { return traffic; }
}
