package dev.orbit.client.clips;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.orbit.client.OrbitClient;
import dev.orbit.client.friends.FriendToasts;
import dev.orbit.client.module.ClipsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Clips: keeps the last ~30 seconds of the game in memory (as JPEG frames) and, when you press the clip key,
 * writes them as an MJPEG .avi into the "clips" folder. Frames are copied off the GPU without stalling the
 * game; shrinking and compressing happens on background threads. Video only (no sound).
 */
public final class ClipRecorder {
    private ClipRecorder() {}

    /** jpeg = 8 bytes (width, height) + the JPEG image */
    private record Frame(byte[] jpeg, long nanos) {}

    private static final ArrayDeque<Frame> FRAMES = new ArrayDeque<>();
    // enough encoder threads for 60 fps, but always leave cores for Minecraft itself
    private static final int THREADS = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() - 2));
    private static final ExecutorService WORK = Executors.newFixedThreadPool(THREADS, r -> { Thread t = new Thread(r, "Orbit-Clips"); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY + 1); return t; });
    private static final ExecutorService SAVE = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Orbit-Clips-Save"); t.setDaemon(true); return t; });
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    private static long nextDue;
    private static volatile boolean saving;

    private static ClipsModule mod() { return OrbitClient.modules() != null && OrbitClient.modules().get("clips") instanceof ClipsModule m ? m : null; }

    /** called once per finished frame, right before it goes to the screen */
    public static void onFrame() {
        ClipsModule m = mod();
        if (m == null || !m.isEnabled()) { if (!FRAMES.isEmpty()) synchronized (FRAMES) { FRAMES.clear(); } return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return; // only record while playing
        long now = System.nanoTime(), interval = 1_000_000_000L / m.fps();
        // a steady schedule (not "time since the last frame"), with a little slack so normal frame-time
        // jitter doesn't make us skip every other frame
        if (now < nextDue - interval / 4) return;
        if (IN_FLIGHT.get() > Math.max(6, m.fps() / 3)) { // the PC can't keep up: drop a frame instead of slowing the game
            if (now - lastDone > 2_000_000_000L) { IN_FLIGHT.set(0); lastDone = now; } // a copy never came back: don't stay stuck
            return;
        }
        nextDue = now - nextDue > interval * 2 ? now + interval : nextDue + interval;
        try { capture(mc.getFramebuffer(), now, m); }
        catch (Throwable t) { if (!warned) { warned = true; OrbitClient.LOG.warn("Clips: could not capture a frame", t); } }
    }
    private static volatile long lastDone = System.nanoTime();
    private static boolean warned;

    private static void capture(Framebuffer fb, long at, ClipsModule m) {
        GpuTexture tex = fb.getColorAttachment();
        if (tex == null) return;
        int w = fb.textureWidth, h = fb.textureHeight, px = tex.getFormat().pixelSize();
        int f = Math.max(1, Math.round(h / (float) m.height()));
        int oh = (h / f) & ~1, rowBytes = w * px;
        GpuBuffer buf = RenderSystem.getDevice().createBuffer(() -> "Orbit clip frame", 9, (long) w * h * px);
        CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
        IN_FLIGHT.incrementAndGet();
        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(tex, buf, 0L, () -> {
                // copy only the rows the video keeps (top to bottom; the GPU image is upside down)
                byte[] raw = new byte[oh * rowBytes];
                try (GpuBuffer.MappedView view = enc.mapBuffer(buf, true, false)) {
                    ByteBuffer data = view.data();
                    for (int y = 0; y < oh; y++) data.get((h - 1 - (y * f + f / 2)) * rowBytes, raw, y * rowBytes, rowBytes);
                } catch (Throwable t) {
                    IN_FLIGHT.decrementAndGet(); buf.close();
                    if (!warned) { warned = true; OrbitClient.LOG.warn("Clips: could not read a frame", t); }
                    return;
                }
                buf.close();
                lastDone = System.nanoTime();
                WORK.execute(() -> {
                    try { add(new Frame(encode(raw, w, oh, px, f), at), m.seconds()); }
                    catch (Throwable t) { if (!warned) { warned = true; OrbitClient.LOG.warn("Clips: could not encode a frame", t); } }
                    finally { IN_FLIGHT.decrementAndGet(); }
                });
            }, 0);
        } catch (Throwable t) { IN_FLIGHT.decrementAndGet(); buf.close(); throw t; }
    }

    /** rows (already top-down, every f-th) of RGBA -> JPEG, keeping every f-th column */
    private static byte[] encode(byte[] raw, int w, int oh, int px, int f) throws Exception {
        int ow = (w / f) & ~1; // even sizes: friendlier for video players
        BufferedImage img = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < oh; y++) {
            int base = y * w, out = y * ow;
            for (int x = 0; x < ow; x++) {
                int i = (base + x * f + f / 2) * px;
                pixels[out + x] = (raw[i] & 0xFF) << 16 | (raw[i + 1] & 0xFF) << 8 | (raw[i + 2] & 0xFF);
            }
        }
        ImageWriter wr = WRITER.get();
        ByteArrayOutputStream out = new ByteArrayOutputStream(96_000);
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            wr.setOutput(ios);
            ImageWriteParam p = wr.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.82f);
            wr.write(null, new IIOImage(img, null, null), p);
        } finally { wr.reset(); }
        return withSize(out.toByteArray(), ow, oh);
    }
    /** one JPEG encoder per worker thread (making a new one for every frame is slow) */
    private static final ThreadLocal<ImageWriter> WRITER = ThreadLocal.withInitial(() -> ImageIO.getImageWritersByFormatName("jpeg").next());
    private static byte[] withSize(byte[] jpeg, int w, int h) {
        byte[] b = new byte[jpeg.length + 8];
        ByteBuffer.wrap(b).putInt(w).putInt(h);
        System.arraycopy(jpeg, 0, b, 8, jpeg.length);
        return b;
    }

    private static void add(Frame f, int seconds) {
        synchronized (FRAMES) {
            FRAMES.addLast(f);
            long cut = f.nanos - seconds * 1_000_000_000L;
            while (!FRAMES.isEmpty() && FRAMES.peekFirst().nanos < cut) FRAMES.pollFirst();
        }
    }

    // ------------------------------------------------------------------ saving
    public static void saveClip() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClipsModule m = mod();
        if (m == null || !m.isEnabled()) {
            FriendToasts.show(null, "Clips are off", "Turn on Clips in the Orbit menu (Right Shift)", false);
            return;
        }
        if (saving) return;
        List<Frame> frames;
        synchronized (FRAMES) { frames = new ArrayList<>(FRAMES); }
        OrbitClient.LOG.info("Clips: saving {} frames (in flight {})", frames.size(), IN_FLIGHT.get());
        frames.sort((a, b) -> Long.compare(a.nanos, b.nanos));
        if (frames.size() < 10) { FriendToasts.show(null, "Nothing to save yet", "Clips record while you play — try again in a moment", false); return; }
        saving = true;
        File dir = new File(mc.runDirectory, "clips");
        File file = new File(dir, "clip-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".avi");
        int wanted = m.fps();
        SAVE.execute(() -> {
            try {
                dir.mkdirs();
                double secs = (frames.get(frames.size() - 1).nanos - frames.get(0).nanos) / 1e9;
                double fps = Math.max(1, (frames.size() - 1) / Math.max(0.1, secs));
                List<byte[]> jpegs = new ArrayList<>();
                int w = 0, h = 0;
                for (Frame f : frames) {
                    ByteBuffer bb = ByteBuffer.wrap(f.jpeg);
                    int fw = bb.getInt(), fh = bb.getInt();
                    if (w == 0) { w = fw; h = fh; }
                    if (fw != w || fh != h) continue; // window was resized mid-clip: keep one size
                    byte[] j = new byte[f.jpeg.length - 8];
                    System.arraycopy(f.jpeg, 8, j, 0, j.length);
                    jpegs.add(j);
                }
                AviWriter.write(file, jpegs, w, h, fps);
                int len = (int) Math.round(secs), got = (int) Math.round(fps);
                // the clip can't be smoother than the game itself was
                String hint = got < wanted * 0.85 ? " · your game ran at ~" + got + " FPS" : "";
                mc.execute(() -> {
                    if (mc.player != null) mc.player.sendMessage(Text.literal("§d[Orbit] §7Clip saved (" + len + " s, " + got + " fps): ")
                            .append(Text.literal(file.getName()).formatted(Formatting.UNDERLINE).styled(s -> s.withClickEvent(new ClickEvent.OpenFile(file.getAbsoluteFile())))), false);
                    FriendToasts.show(null, "Clip saved · " + len + " s · " + got + " fps" + hint, "Watch it in the pause menu → Gallery → Clips", true);
                    dev.orbit.client.gui.Sounds.success();
                });
            } catch (Exception e) {
                OrbitClient.LOG.warn("Could not save clip", e);
                mc.execute(() -> FriendToasts.show(null, "Clip failed", String.valueOf(e.getMessage()), false));
            } finally { saving = false; }
        });
    }

    /** frames in memory right now (for the settings screen) */
    public static int bufferedSeconds() {
        synchronized (FRAMES) { return FRAMES.size() < 2 ? 0 : (int) ((FRAMES.peekLast().nanos - FRAMES.peekFirst().nanos) / 1_000_000_000L); }
    }
}
