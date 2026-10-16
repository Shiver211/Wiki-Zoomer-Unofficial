package com.github.alexthe666.wikizoomer.client.gif;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class AnimatedGifEncoder {

    protected int width;
    protected int height;
    protected boolean transparent = false;
    protected int transIndex = 0;
    protected int repeat = 0; // 0 = repeat forever, -1 = no repeat
    protected int delay = 5;  // centiseconds
    protected boolean started = false;
    protected OutputStream out;
    protected byte[] indexedPixels;
    protected byte[] colorTable;
    protected boolean firstFrame = true;
    protected int sampleQuality = 10;

    public void setSize(int w, int h) {
        this.width = w;
        this.height = h;
    }

    public void setDelay(int ms) {
        this.delay = Math.max(1, Math.round(ms / 10.0f));
    }

    public void setDelayCentiseconds(int cs) {
        this.delay = Math.max(1, cs);
    }

    public void setRepeat(int iter) {
        this.repeat = iter;
    }

    public void setTransparent(boolean trans) {
        this.transparent = trans;
    }

    public void setQuality(int quality) {
        this.sampleQuality = Math.max(1, quality);
    }

    public boolean start(OutputStream os) {
        if (os == null) return false;
        boolean ok = true;
        this.out = os;
        try {
            writeString("GIF89a");
        } catch (IOException e) {
            ok = false;
        }
        return started = ok;
    }

    public boolean start(String file) {
        try {
            return start(new BufferedOutputStream(new FileOutputStream(file)));
        } catch (IOException e) {
            return false;
        }
    }

    public boolean addFrame(BufferedImage im, int frameDelayCs) {
        if (im == null || !started) return false;
        boolean ok = true;
        try {
            if (width == 0 || height == 0) {
                setSize(im.getWidth(), im.getHeight());
            }
            this.delay = Math.max(1, frameDelayCs);
            analyzePixels(im);
            if (firstFrame) {
                writeLSD();
                writeNetscapeExt();
            }
            writeGraphicCtrlExt();
            writeImageDesc();
            writePixels();
            firstFrame = false;
        } catch (IOException e) {
            ok = false;
        }
        return ok;
    }

    public boolean finish() {
        if (!started) return false;
        boolean ok = true;
        started = false;
        try {
            out.write(0x3B); // GIF trailer
            out.flush();
            out.close();
        } catch (IOException e) {
            ok = false;
        }
        transIndex = 0;
        out = null;
        indexedPixels = null;
        colorTable = null;
        firstFrame = true;
        return ok;
    }

    private void analyzePixels(BufferedImage im) {
        int w = im.getWidth();
        int h = im.getHeight();
        int[] rgb = new int[w * h];
        im.getRGB(0, 0, w, h, rgb, 0, w);
        indexedPixels = new byte[w * h];

        if (transparent) {
            Map<Integer, Integer> exactMap = new HashMap<>();
            int nonTransCount = 0;
            for (int p : rgb) {
                int a = (p >> 24) & 0xFF;
                if (a >= 128) {
                    nonTransCount++;
                    int c = p & 0x00FFFFFF;
                    if (!exactMap.containsKey(c)) {
                        exactMap.put(c, exactMap.size() + 1); // 0 reserved for transparent
                    }
                }
            }

            if (exactMap.size() <= 255) {
                colorTable = new byte[256 * 3];
                for (Map.Entry<Integer, Integer> entry : exactMap.entrySet()) {
                    int c = entry.getKey();
                    int idx = entry.getValue();
                    colorTable[idx * 3] = (byte) ((c >> 16) & 0xFF);
                    colorTable[idx * 3 + 1] = (byte) ((c >> 8) & 0xFF);
                    colorTable[idx * 3 + 2] = (byte) (c & 0xFF);
                }
                for (int i = 0; i < rgb.length; i++) {
                    int a = (rgb[i] >> 24) & 0xFF;
                    if (a < 128) {
                        indexedPixels[i] = 0;
                    } else {
                        indexedPixels[i] = (byte) (int) exactMap.get(rgb[i] & 0x00FFFFFF);
                    }
                }
                transIndex = 0;
            } else {
                byte[] sampleData = new byte[nonTransCount * 3];
                int sIdx = 0;
                for (int p : rgb) {
                    int a = (p >> 24) & 0xFF;
                    if (a >= 128) {
                        sampleData[sIdx++] = (byte) (p & 0xFF);         // B
                        sampleData[sIdx++] = (byte) ((p >> 8) & 0xFF);  // G
                        sampleData[sIdx++] = (byte) ((p >> 16) & 0xFF); // R
                    }
                }
                NeuQuant nq = new NeuQuant(sampleData, sampleData.length, sampleQuality);
                byte[] nqPal = nq.process();

                colorTable = new byte[256 * 3];
                for (int i = 0; i < 255; i++) {
                    colorTable[(i + 1) * 3] = nqPal[i * 3 + 2];     // R
                    colorTable[(i + 1) * 3 + 1] = nqPal[i * 3 + 1]; // G
                    colorTable[(i + 1) * 3 + 2] = nqPal[i * 3];     // B
                }
                for (int i = 0; i < rgb.length; i++) {
                    int a = (rgb[i] >> 24) & 0xFF;
                    if (a < 128) {
                        indexedPixels[i] = 0;
                    } else {
                        int b = rgb[i] & 0xFF;
                        int g = (rgb[i] >> 8) & 0xFF;
                        int r = (rgb[i] >> 16) & 0xFF;
                        int mapped = nq.map(b, g, r);
                        if (mapped >= 255) mapped = 254;
                        indexedPixels[i] = (byte) (mapped + 1);
                    }
                }
                transIndex = 0;
            }
        } else {
            Map<Integer, Integer> exactMap = new HashMap<>();
            for (int p : rgb) {
                int c = p & 0x00FFFFFF;
                if (!exactMap.containsKey(c)) {
                    exactMap.put(c, exactMap.size());
                }
            }

            if (exactMap.size() <= 256) {
                colorTable = new byte[256 * 3];
                for (Map.Entry<Integer, Integer> entry : exactMap.entrySet()) {
                    int c = entry.getKey();
                    int idx = entry.getValue();
                    colorTable[idx * 3] = (byte) ((c >> 16) & 0xFF);
                    colorTable[idx * 3 + 1] = (byte) ((c >> 8) & 0xFF);
                    colorTable[idx * 3 + 2] = (byte) (c & 0xFF);
                }
                for (int i = 0; i < rgb.length; i++) {
                    indexedPixels[i] = (byte) (int) exactMap.get(rgb[i] & 0x00FFFFFF);
                }
            } else {
                byte[] sampleData = new byte[rgb.length * 3];
                int sIdx = 0;
                for (int p : rgb) {
                    sampleData[sIdx++] = (byte) (p & 0xFF);
                    sampleData[sIdx++] = (byte) ((p >> 8) & 0xFF);
                    sampleData[sIdx++] = (byte) ((p >> 16) & 0xFF);
                }
                NeuQuant nq = new NeuQuant(sampleData, sampleData.length, sampleQuality);
                byte[] nqPal = nq.process();
                colorTable = new byte[256 * 3];
                for (int i = 0; i < 256; i++) {
                    colorTable[i * 3] = nqPal[i * 3 + 2];
                    colorTable[i * 3 + 1] = nqPal[i * 3 + 1];
                    colorTable[i * 3 + 2] = nqPal[i * 3];
                }
                for (int i = 0; i < rgb.length; i++) {
                    int b = rgb[i] & 0xFF;
                    int g = (rgb[i] >> 8) & 0xFF;
                    int r = (rgb[i] >> 16) & 0xFF;
                    indexedPixels[i] = (byte) nq.map(b, g, r);
                }
            }
        }
    }

    private void writeGraphicCtrlExt() throws IOException {
        out.write(0x21);
        out.write(0xF9);
        out.write(4);
        int transp, disp;
        if (transparent) {
            transp = 1;
            disp = 2; // restore to background
        } else {
            transp = 0;
            disp = 1; // leave in place
        }
        out.write((disp << 2) | transp);
        writeShort(delay);
        out.write(transparent ? transIndex : 0);
        out.write(0);
    }

    private void writeImageDesc() throws IOException {
        out.write(0x2C);
        writeShort(0);
        writeShort(0);
        writeShort(width);
        writeShort(height);
        out.write(0x80 | 0x07); // Local color table flag = 1, size = 7 (256 colors)
        out.write(colorTable);
    }

    private void writeLSD() throws IOException {
        writeShort(width);
        writeShort(height);
        out.write(0x80 | 0x70 | 0x07); // Global color table flag = 1, 8 bits/pixel, 256 entries
        out.write(0); // background color index
        out.write(0); // pixel aspect ratio
        out.write(colorTable); // Global color table
    }

    private void writeNetscapeExt() throws IOException {
        out.write(0x21);
        out.write(0xFF);
        out.write(11);
        writeString("NETSCAPE2.0");
        out.write(3);
        out.write(1);
        writeShort(repeat);
        out.write(0);
    }

    private void writePixels() throws IOException {
        LZWEncoder encoder = new LZWEncoder(width, height, indexedPixels, 8);
        encoder.encode(out);
    }

    private void writeShort(int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private void writeString(String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            out.write((byte) s.charAt(i));
        }
    }
}

