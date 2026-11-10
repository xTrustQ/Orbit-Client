package dev.orbit.client.clips;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Writes JPEG frames as an MJPEG .avi — plays in VLC, Windows Media Player / Films & TV and most editors,
 * and needs no extra software (no ffmpeg).
 */
final class AviWriter {
    private AviWriter() {}

    static void write(File file, List<byte[]> frames, int w, int h, double fps) throws IOException {
        int n = frames.size();
        int maxFrame = 0;
        long moviSize = 4;
        for (byte[] f : frames) { maxFrame = Math.max(maxFrame, f.length); moviSize += 8 + f.length + (f.length & 1); }
        int scale = 1000, rate = (int) Math.round(fps * 1000);
        long idxSize = 16L * n;
        long riffSize = 4 + (8 + 192) + (8 + moviSize) + (8 + idxSize);

        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(file), 1 << 20)) {
            fourcc(o, "RIFF"); u32(o, riffSize); fourcc(o, "AVI ");
            // header list
            fourcc(o, "LIST"); u32(o, 192); fourcc(o, "hdrl");
            fourcc(o, "avih"); u32(o, 56);
            u32(o, Math.round(1_000_000 / fps)); // microseconds per frame
            u32(o, (long) (maxFrame * fps));      // max bytes per second
            u32(o, 0);                            // padding granularity
            u32(o, 0x10);                         // AVIF_HASINDEX
            u32(o, n); u32(o, 0); u32(o, 1); u32(o, maxFrame); u32(o, w); u32(o, h);
            u32(o, 0); u32(o, 0); u32(o, 0); u32(o, 0);
            fourcc(o, "LIST"); u32(o, 116); fourcc(o, "strl");
            fourcc(o, "strh"); u32(o, 56);
            fourcc(o, "vids"); fourcc(o, "MJPG"); u32(o, 0); u16(o, 0); u16(o, 0);
            u32(o, 0); u32(o, scale); u32(o, rate); u32(o, 0); u32(o, n); u32(o, maxFrame); u32(o, 0xFFFFFFFFL); u32(o, 0);
            u16(o, 0); u16(o, 0); u16(o, w); u16(o, h);
            fourcc(o, "strf"); u32(o, 40);
            u32(o, 40); u32(o, w); u32(o, h); u16(o, 1); u16(o, 24); fourcc(o, "MJPG"); u32(o, (long) w * h * 3);
            u32(o, 0); u32(o, 0); u32(o, 0); u32(o, 0);
            // frames
            fourcc(o, "LIST"); u32(o, moviSize); fourcc(o, "movi");
            for (byte[] f : frames) {
                fourcc(o, "00dc"); u32(o, f.length); o.write(f);
                if ((f.length & 1) == 1) o.write(0);
            }
            // index (offsets count from the "movi" tag)
            fourcc(o, "idx1"); u32(o, idxSize);
            long off = 4;
            for (byte[] f : frames) {
                fourcc(o, "00dc"); u32(o, 0x10); u32(o, off); u32(o, f.length);
                off += 8 + f.length + (f.length & 1);
            }
        }
    }

    private static void fourcc(OutputStream o, String s) throws IOException { o.write(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); }
    private static void u32(OutputStream o, long v) throws IOException { o.write((int) v); o.write((int) (v >> 8)); o.write((int) (v >> 16)); o.write((int) (v >> 24)); }
    private static void u16(OutputStream o, int v) throws IOException { o.write(v); o.write(v >> 8); }
}
