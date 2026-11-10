package dev.orbit.client.clips;

import dev.orbit.client.OrbitClient;
import dev.orbit.client.compat.Compat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Plays an Orbit clip (MJPEG .avi) inside Minecraft: a background thread decodes the frames on time
 * (Minecraft's own fast JPEG decoder), the render thread just copies the newest one into a texture.
 */
public final class ClipPlayer implements AutoCloseable {
    /** what's inside a clip: size, frame rate and where every frame is in the file */
    public record Info(int width, int height, double fps, long[] offsets, int[] lengths) {
        public int frames() { return offsets.length; }
        public double seconds() { return frames() / fps; }
    }

    public final File file;
    public final Info info;
    private final RandomAccessFile raf;
    private final NativeImageBackedTexture texture;
    public final Identifier id;
    private final Thread thread;
    private volatile NativeImage pending;
    private volatile boolean running = true;
    public volatile boolean playing = true;
    /** current frame index (the decoder moves it forward; seek() jumps) */
    private volatile int frame, seekTo = -1;

    private static int counter;

    public ClipPlayer(File file) throws IOException {
        this.file = file;
        this.info = read(file);
        if (info.frames() == 0) throw new IOException("Empty clip");
        this.raf = new RandomAccessFile(file, "r");
        NativeImage first = decodeNow(0);
        this.id = Compat.id("clip/player" + (counter++));
        this.texture = Compat.texture("orbit-clip-player", first, true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        this.thread = new Thread(this::loop, "Orbit-ClipPlayer");
        thread.setDaemon(true);
        thread.start();
    }

    // several threads decode the next frames ahead of time, so 60 fps clips play smoothly even at 1080p
    private static final int DECODERS = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() / 2));
    private static final int AHEAD = 24;
    private final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(DECODERS, r -> { Thread t = new Thread(r, "Orbit-ClipDecode"); t.setDaemon(true); return t; });
    private final java.util.concurrent.ConcurrentHashMap<Integer, NativeImage> ready = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<Integer> queued = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** frames actually shown per second (to tell the viewer if their PC can't keep up) */
    public volatile float shownFps;

    /** playback follows the clock: if decoding can't keep up, frames are skipped instead of playing in slow motion */
    private void loop() {
        double fps = info.fps();
        long start = System.nanoTime(), statAt = start;
        int startFrame = 0, shown = 0, shownCount = 0;
        while (running) {
            try {
                int s = seekTo;
                if (s >= 0) { seekTo = -1; dropAll(); startFrame = s; start = System.nanoTime(); show(s, decodeNow(s)); shown = s; }
                if (!playing) { Thread.sleep(15); start = System.nanoTime(); startFrame = shown; statAt = start; shownCount = 0; continue; }
                int due = startFrame + (int) ((System.nanoTime() - start) / 1e9 * fps);
                if (due >= info.frames()) { start = System.nanoTime(); startFrame = 0; due = 0; dropAll(); } // loop
                prefetch(due);
                if (due != shown) {
                    // the newest frame that's ready and not late (a frame that's a moment late beats a skipped one)
                    int best = -1;
                    for (Integer k : ready.keySet()) if (k > shown && k <= due && k > best) best = k;
                    if (best >= 0) {
                        NativeImage img = ready.remove(best);
                        if (img != null) { show(best, img); shown = best; shownCount++; }
                    }
                    // frames we've passed: throw away
                    for (Integer k : ready.keySet()) if (k <= shown) { NativeImage o = ready.remove(k); if (o != null) o.close(); }
                }
                long now = System.nanoTime();
                if (now - statAt > 1_000_000_000L) { shownFps = shownCount * 1e9f / (now - statAt); statAt = now; shownCount = 0; }
                java.util.concurrent.locks.LockSupport.parkNanos(400_000); // short, precise wait (sleep(1) can be 2 ms on Windows)
                if (Thread.interrupted()) return;
            } catch (InterruptedException e) { return; }
            catch (Exception e) { OrbitClient.LOG.debug("Clip playback", e); }
        }
    }
    private void prefetch(int from) {
        for (int k = from; k < Math.min(info.frames(), from + AHEAD); k++) {
            final int i = k;
            if (ready.containsKey(i) || !queued.add(i)) continue;
            pool.execute(() -> {
                try { if (running) { NativeImage img = decodeNow(i); if (running) ready.put(i, img); else img.close(); } }
                catch (Exception e) { OrbitClient.LOG.debug("Clip frame {}", i, e); }
                finally { queued.remove(i); }
            });
        }
    }
    private void dropAll() { for (Integer k : ready.keySet()) { NativeImage o = ready.remove(k); if (o != null) o.close(); } }
    private void show(int i, NativeImage img) {
        NativeImage old = pending;
        pending = img;
        if (old != null) old.close();
        frame = i;
    }

    private byte[] readFrame(int i) throws IOException {
        byte[] jpeg = new byte[info.lengths()[i]];
        synchronized (raf) { raf.seek(info.offsets()[i]); raf.readFully(jpeg); }
        return jpeg;
    }
    private NativeImage decodeNow(int i) throws IOException {
        // Minecraft's NativeImage.read only takes PNG: decode the JPEG with Java, then copy all pixels in one go
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(readFrame(i)));
        if (src == null) throw new IOException("Unreadable frame " + i);
        int w = src.getWidth(), h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        for (int k = 0; k < px.length; k++) { int c = px[k]; px[k] = 0xFF000000 | (c & 0x00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16); } // ARGB -> ABGR
        NativeImage img = new NativeImage(w, h, false);
        org.lwjgl.system.MemoryUtil.memIntBuffer(img.imageId(), w * h).put(px);
        return img;
    }

    /** call every render frame: puts the newest decoded frame on the texture */
    public void update() {
        NativeImage img = pending;
        if (img == null) return;
        pending = null;
        try {
            NativeImage target = texture.getImage();
            if (target != null && target.getWidth() == img.getWidth() && target.getHeight() == img.getHeight()) { target.copyFrom(img); texture.upload(); }
        } finally { img.close(); }
    }

    public int frame() { return frame; }
    public double position() { return frame / info.fps(); }
    public void seek(double fraction) { seekTo = Math.max(0, Math.min(info.frames() - 1, (int) (fraction * info.frames()))); }
    public void toggle() { playing = !playing; }

    @Override public void close() {
        running = false;
        thread.interrupt();
        try { thread.join(500); } catch (InterruptedException ignored) { }
        pool.shutdownNow();
        try { pool.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { }
        try { synchronized (raf) { raf.close(); } } catch (IOException ignored) { }
        dropAll();
        NativeImage p = pending; pending = null;
        if (p != null) p.close();
        MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
    }

    // ------------------------------------------------------------------ reading the file (clips written by AviWriter)
    public static Info read(File file) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            byte[] head = new byte[224];
            f.readFully(head);
            ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
            if (!new String(head, 0, 4).equals("RIFF") || !new String(head, 8, 4).equals("AVI ")) throw new IOException("Not an AVI clip");
            long usPerFrame = b.getInt(32) & 0xFFFFFFFFL;
            int width = b.getInt(64), height = b.getInt(68);
            long moviSize = b.getInt(216) & 0xFFFFFFFFL;
            long moviTag = 220;
            f.seek(moviTag + moviSize);
            byte[] idxHead = new byte[8];
            f.readFully(idxHead);
            if (!new String(idxHead, 0, 4).equals("idx1")) throw new IOException("Clip has no index");
            int n = (int) ((ByteBuffer.wrap(idxHead).order(ByteOrder.LITTLE_ENDIAN).getInt(4) & 0xFFFFFFFFL) / 16);
            byte[] idx = new byte[n * 16];
            f.readFully(idx);
            ByteBuffer ib = ByteBuffer.wrap(idx).order(ByteOrder.LITTLE_ENDIAN);
            long[] off = new long[n];
            int[] len = new int[n];
            for (int i = 0; i < n; i++) {
                off[i] = moviTag + (ib.getInt(i * 16 + 8) & 0xFFFFFFFFL) + 8;
                len[i] = ib.getInt(i * 16 + 12);
            }
            return new Info(width, height, usPerFrame > 0 ? 1_000_000.0 / usPerFrame : 30, off, len);
        }
    }

    /** the JPEG bytes of one frame (for thumbnails) */
    public static byte[] frameJpeg(File file, int index) throws IOException {
        Info info = read(file);
        int i = Math.max(0, Math.min(info.frames() - 1, index));
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            byte[] j = new byte[info.lengths()[i]];
            f.seek(info.offsets()[i]);
            f.readFully(j);
            return j;
        }
    }
}
