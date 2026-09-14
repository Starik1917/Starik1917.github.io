package com.starik.flydrive;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Base64;
import java.util.Random;

final class BrainModel {
    static final float DT = 0.020f;
    private static final float THRESHOLD_MV = 18.0f;
    private static final float DECAY = 0.82f;
    private static final float MV_PER_SYNAPSE = 0.275f;
    private static final int MAX_SPIKES = 7000;

    final int n;
    final int m;
    final int nTypes;
    final int minSyn;

    private final IntBuffer indptr;
    private final IntBuffer posts;
    private final ShortBuffer synCounts;
    private final ShortBuffer typeCodes;
    private final ByteBuffer signs;

    private final float[] membrane;
    private final byte[] refractory;
    private final int[] spikeStamp;
    private int tick = 1;
    private final int[] spikeList;
    private int spikeCount;
    private final Random rng = new Random(1917L);

    final int[] motionA;
    final int[] motionB;
    final int[] motionC;
    final int[] motionD;
    final int[] looming;
    final int[] visualColor;
    final int[] dna01L;
    final int[] dna01R;
    final int[] dna02L;
    final int[] dna02R;
    final int[] dnp01;
    final int[] dnp09;
    final int[] trainableTypes;
    final String[] trainableNames;

    private final float[] gains;
    private final float[] bestGains;
    private final Context context;

    private float rateDna01L, rateDna01R, rateDna02L, rateDna02R, rateDnp01, rateDnp09;
    private float netHz;

    static final class Output {
        float steer;
        float throttle;
        float brake;
        float dNaLeft;
        float dNaRight;
        float freeze;
        float escape;
        float netHz;
    }

    BrainModel(Context ctx) throws Exception {
        context = ctx.getApplicationContext();
        AssetFileDescriptor afd = ctx.getAssets().openFd("malecns_graph.bin");
        FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
        FileChannel ch = fis.getChannel();
        MappedByteBuffer map = ch.map(FileChannel.MapMode.READ_ONLY,
                afd.getStartOffset(), afd.getLength());
        map.order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[8];
        map.get(magic);
        String ms = new String(magic, "US-ASCII");
        if (!"FLYCNS10".equals(ms)) throw new IllegalStateException("Bad MaleCNS asset");
        n = map.getInt();
        m = map.getInt();
        nTypes = map.getInt();
        minSyn = map.getInt();

        indptr = intSlice(map, n + 1);
        posts = intSlice(map, m);
        synCounts = shortSlice(map, m);
        typeCodes = shortSlice(map, n);
        signs = byteSlice(map, n);

        JSONObject groups = new JSONObject(readText(ctx, "malecns_groups.json"));
        motionA = ints(groups.optJSONArray("motion_a"));
        motionB = ints(groups.optJSONArray("motion_b"));
        motionC = ints(groups.optJSONArray("motion_c"));
        motionD = ints(groups.optJSONArray("motion_d"));
        looming = ints(groups.optJSONArray("looming"));
        visualColor = ints(groups.optJSONArray("visual_color"));
        dna01L = ints(groups.optJSONArray("DNa01_L"));
        dna01R = ints(groups.optJSONArray("DNa01_R"));
        dna02L = ints(groups.optJSONArray("DNa02_L"));
        dna02R = ints(groups.optJSONArray("DNa02_R"));
        dnp01 = ints(groups.optJSONArray("DNp01"));
        dnp09 = ints(groups.optJSONArray("DNp09"));
        trainableTypes = ints(groups.optJSONArray("trainable_type_codes"));
        trainableNames = strings(groups.optJSONArray("trainable_type_names"));

        gains = new float[nTypes];
        bestGains = new float[nTypes];
        for (int i = 0; i < nTypes; i++) gains[i] = bestGains[i] = 1f;
        loadSavedGains();

        membrane = new float[n];
        refractory = new byte[n];
        spikeStamp = new int[n];
        spikeList = new int[Math.min(n, MAX_SPIKES)];
    }

    private static IntBuffer intSlice(ByteBuffer src, int count) {
        ByteBuffer s = src.slice().order(ByteOrder.LITTLE_ENDIAN);
        s.limit(count * 4);
        IntBuffer out = s.asIntBuffer();
        src.position(src.position() + count * 4);
        return out;
    }
    private static ShortBuffer shortSlice(ByteBuffer src, int count) {
        ByteBuffer s = src.slice().order(ByteOrder.LITTLE_ENDIAN);
        s.limit(count * 2);
        ShortBuffer out = s.asShortBuffer();
        src.position(src.position() + count * 2);
        return out;
    }
    private static ByteBuffer byteSlice(ByteBuffer src, int count) {
        ByteBuffer s = src.slice().order(ByteOrder.LITTLE_ENDIAN);
        s.limit(count);
        src.position(src.position() + count);
        return s;
    }

    private static String readText(Context ctx, String asset) throws Exception {
        InputStream in = ctx.getAssets().open(asset);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        in.close();
        return out.toString("UTF-8");
    }
    private static int[] ints(JSONArray a) {
        if (a == null) return new int[0];
        int[] x = new int[a.length()];
        for (int i = 0; i < x.length; i++) x[i] = a.optInt(i);
        return x;
    }
    private static String[] strings(JSONArray a) {
        if (a == null) return new String[0];
        String[] x = new String[a.length()];
        for (int i = 0; i < x.length; i++) x[i] = a.optString(i, "type-" + i);
        return x;
    }

    void resetActivity() {
        java.util.Arrays.fill(membrane, 0f);
        java.util.Arrays.fill(refractory, (byte)0);
        rateDna01L = rateDna01R = rateDna02L = rateDna02R = 0f;
        rateDnp01 = rateDnp09 = netHz = 0f;
    }

    void setCandidateFrom(float[] candidate) {
        System.arraycopy(bestGains, 0, gains, 0, gains.length);
        if (candidate == null) return;
        for (int i = 0; i < trainableTypes.length && i < candidate.length; i++) {
            int t = trainableTypes[i];
            if (t >= 0 && t < gains.length) gains[t] = candidate[i];
        }
    }

    float[] bestTrainableVector() {
        float[] out = new float[trainableTypes.length];
        for (int i = 0; i < out.length; i++) out[i] = bestGains[trainableTypes[i]];
        return out;
    }

    void commitTrainableVector(float[] v) {
        for (int i = 0; i < trainableTypes.length && i < v.length; i++) {
            int t = trainableTypes[i];
            bestGains[t] = gains[t] = MathUtil.clamp(v[i], 0.15f, 6.0f);
        }
        saveGains();
    }

    Output step(float laneError, float headingError, float threat,
                float lightRed, float lightGreen, float speedNorm) {
        tick++;
        if (tick == Integer.MAX_VALUE) { java.util.Arrays.fill(spikeStamp, 0); tick = 1; }
        spikeCount = 0;

        for (int i = 0; i < n; i++) {
            membrane[i] *= DECAY;
            if (refractory[i] > 0) refractory[i]--;
        }

        float leftFlow = MathUtil.clamp(0.18f + Math.max(0f, laneError) + Math.max(0f, headingError), 0f, 1f);
        float rightFlow = MathUtil.clamp(0.18f + Math.max(0f, -laneError) + Math.max(0f, -headingError), 0f, 1f);
        stimulate(motionA, leftFlow * 0.55f);
        stimulate(motionB, rightFlow * 0.55f);
        stimulate(motionC, leftFlow * 0.35f + speedNorm * 0.15f);
        stimulate(motionD, rightFlow * 0.35f + speedNorm * 0.15f);
        stimulate(looming, MathUtil.clamp(threat, 0f, 1f) * 0.9f);
        stimulate(visualColor, MathUtil.clamp(lightRed * 0.55f + lightGreen * 0.18f, 0f, 0.8f));

        int spontaneous = Math.max(1, n / 9000);
        for (int k = 0; k < spontaneous; k++) {
            int i = rng.nextInt(n);
            if (refractory[i] == 0) membrane[i] += THRESHOLD_MV * (0.45f + 0.35f * rng.nextFloat());
        }

        for (int i = 0; i < n && spikeCount < MAX_SPIKES; i++) {
            if (refractory[i] == 0 && membrane[i] >= THRESHOLD_MV) fire(i);
        }

        int propagated = spikeCount;
        for (int si = 0; si < propagated; si++) {
            int pre = spikeList[si];
            int sign = signs.get(pre);
            if (sign == 0) continue;
            int type = typeCodes.get(pre) & 0xffff;
            float g = type < gains.length ? gains[type] : 1f;
            float scale = sign * g * MV_PER_SYNAPSE;
            int start = indptr.get(pre), end = indptr.get(pre + 1);
            for (int e = start; e < end; e++) {
                int post = posts.get(e);
                if (refractory[post] != 0) continue;
                int sc = synCounts.get(e) & 0xffff;
                float v = membrane[post] + sc * scale;
                membrane[post] = MathUtil.clamp(v, -40f, 45f);
            }
        }

        for (int i = 0; i < n && spikeCount < MAX_SPIKES; i++) {
            if (spikeStamp[i] != tick && refractory[i] == 0 && membrane[i] >= THRESHOLD_MV) fire(i);
        }

        float inst01L = groupHz(dna01L), inst01R = groupHz(dna01R);
        float inst02L = groupHz(dna02L), inst02R = groupHz(dna02R);
        float instP01 = groupHz(dnp01), instP09 = groupHz(dnp09);
        rateDna01L = smoothRate(rateDna01L, inst01L);
        rateDna01R = smoothRate(rateDna01R, inst01R);
        rateDna02L = smoothRate(rateDna02L, inst02L);
        rateDna02R = smoothRate(rateDna02R, inst02R);
        rateDnp01 = smoothRate(rateDnp01, instP01);
        rateDnp09 = smoothRate(rateDnp09, instP09);
        netHz = smoothRate(netHz, (spikeCount / (float)Math.max(1, n)) / DT);

        Output o = new Output();
        float right = rateDna02R + 0.45f * rateDna01R;
        float left = rateDna02L + 0.45f * rateDna01L;
        o.steer = MathUtil.tanh((right - left) / 22f);
        float locomotion = (rateDna01L + rateDna01R + rateDna02L + rateDna02R) * 0.25f;
        o.brake = MathUtil.clamp(Math.max(rateDnp09 / 55f, rateDnp01 / 80f), 0f, 1f);
        o.throttle = MathUtil.clamp(0.24f + 0.76f * MathUtil.sigmoid((locomotion - 12f) / 11f) - 0.9f * o.brake, 0f, 1f);
        o.dNaLeft = left;
        o.dNaRight = right;
        o.freeze = rateDnp09;
        o.escape = rateDnp01;
        o.netHz = netHz;
        return o;
    }

    private float smoothRate(float old, float now) { return old * 0.84f + now * 0.16f; }

    private void stimulate(int[] group, float intensity) {
        if (group.length == 0 || intensity <= 0f) return;
        float p = MathUtil.clamp(intensity * 0.48f, 0f, 0.9f);
        int stride = group.length > 1400 ? Math.max(1, group.length / 1400) : 1;
        for (int k = 0; k < group.length; k += stride) {
            int i = group[k];
            if (i < 0 || i >= n || refractory[i] != 0) continue;
            if (rng.nextFloat() < p) membrane[i] += THRESHOLD_MV * (0.78f + intensity * 0.75f);
        }
    }

    private void fire(int i) {
        if (spikeCount >= MAX_SPIKES) return;
        spikeList[spikeCount++] = i;
        spikeStamp[i] = tick;
        membrane[i] = 0f;
        refractory[i] = 2;
    }

    private float groupHz(int[] group) {
        if (group.length == 0) return 0f;
        int c = 0;
        for (int i : group) if (i >= 0 && i < n && spikeStamp[i] == tick) c++;
        return (c / (float)group.length) / DT;
    }

    private void saveGains() {
        try {
            ByteBuffer b = ByteBuffer.allocate(trainableTypes.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int t : trainableTypes) b.putFloat(bestGains[t]);
            String s = Base64.getEncoder().encodeToString(b.array());
            context.getSharedPreferences("brain", Context.MODE_PRIVATE).edit().putString("gains", s).apply();
        } catch (Throwable ignored) {}
    }

    private void loadSavedGains() {
        try {
            SharedPreferences p = context.getSharedPreferences("brain", Context.MODE_PRIVATE);
            String s = p.getString("gains", null);
            if (s == null) return;
            byte[] raw = Base64.getDecoder().decode(s);
            ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < trainableTypes.length && b.remaining() >= 4; i++) {
                float v = MathUtil.clamp(b.getFloat(), 0.15f, 6.0f);
                int t = trainableTypes[i];
                bestGains[t] = gains[t] = v;
            }
        } catch (Throwable ignored) {}
    }
}
