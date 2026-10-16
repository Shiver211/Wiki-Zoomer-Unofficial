package com.github.alexthe666.wikizoomer.client.gif;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class NeuQuant {

    protected static final int netsize = 256;
    protected static final int prime1 = 499;
    protected static final int prime2 = 491;
    protected static final int prime3 = 487;
    protected static final int prime4 = 503;
    protected static final int minpicturebytes = (3 * prime4);

    protected static final int maxnetpos = (netsize - 1);
    protected static final int netbiasshift = 4;
    protected static final int ncycles = 100;

    protected static final int intbiasshift = 16;
    protected static final int intbias = (((int) 1) << intbiasshift);
    protected static final int gammashift = 10;
    protected static final int gamma = (((int) 1) << gammashift);
    protected static final int betashift = 10;
    protected static final int beta = (intbias >> betashift);
    protected static final int betagamma = (intbias << (gammashift - betashift));

    protected static final int initrad = (netsize >> 3);
    protected static final int radiusbiasshift = 6;
    protected static final int radiusbias = (((int) 1) << radiusbiasshift);
    protected static final int initradius = (initrad * radiusbias);
    protected static final int radiusdec = 30;

    protected static final int alphabiasshift = 10;
    protected static final int initalpha = (((int) 1) << alphabiasshift);
    protected int alphadec;

    protected static final int radbiasshift = 8;
    protected static final int radbias = (((int) 1) << radbiasshift);
    protected static final int alpharadbise = ((int) 1) << (alphabiasshift + radbiasshift);

    protected byte[] thepicture;
    protected int lengthcount;
    protected int samplefac;

    protected int[][] network;
    protected int[] netindex = new int[256];
    protected int[] bias = new int[netsize];
    protected int[] freq = new int[netsize];
    protected int[] radpower = new int[initrad];

    public NeuQuant(byte[] thepic, int len, int sample) {
        thepicture = thepic;
        lengthcount = len;
        samplefac = sample;

        network = new int[netsize][];
        for (int i = 0; i < netsize; i++) {
            network[i] = new int[4];
            int[] p = network[i];
            p[0] = p[1] = p[2] = (i << (netbiasshift + 8)) / netsize;
            freq[i] = intbias / netsize;
            bias[i] = 0;
        }
    }

    public byte[] colorMap() {
        byte[] map = new byte[3 * netsize];
        int[] index = new int[netsize];
        for (int i = 0; i < netsize; i++) {
            index[network[i][3]] = i;
        }
        int k = 0;
        for (int i = 0; i < netsize; i++) {
            int j = index[i];
            map[k++] = (byte) (network[j][0]);
            map[k++] = (byte) (network[j][1]);
            map[k++] = (byte) (network[j][2]);
        }
        return map;
    }

    public void inxbuild() {
        int previouscol = 0;
        int startpos = 0;
        for (int i = 0; i < netsize; i++) {
            int[] p = network[i];
            int smallpos = i;
            int smallval = p[1];
            for (int j = i + 1; j < netsize; j++) {
                int[] q = network[j];
                if (q[1] < smallval) {
                    smallpos = j;
                    smallval = q[1];
                }
            }
            int[] q = network[smallpos];
            if (i != smallpos) {
                int j = q[0]; q[0] = p[0]; p[0] = j;
                j = q[1]; q[1] = p[1]; p[1] = j;
                j = q[2]; q[2] = p[2]; p[2] = j;
                j = q[3]; q[3] = p[3]; p[3] = j;
            }
            if (smallval != previouscol) {
                netindex[previouscol] = (startpos + i) >> 1;
                for (int j = previouscol + 1; j < smallval; j++) {
                    netindex[j] = i;
                }
                previouscol = smallval;
                startpos = i;
            }
        }
        netindex[previouscol] = (startpos + maxnetpos) >> 1;
        for (int j = previouscol + 1; j < 256; j++) {
            netindex[j] = maxnetpos;
        }
    }

    public byte[] process() {
        learn();
        unbiasnet();
        inxbuild();
        return colorMap();
    }

    public void unbiasnet() {
        for (int i = 0; i < netsize; i++) {
            network[i][0] >>= netbiasshift;
            network[i][1] >>= netbiasshift;
            network[i][2] >>= netbiasshift;
            network[i][3] = i;
        }
    }

    protected void alterneigh(int rad, int i, int b, int g, int r) {
        int lo = Math.max(-1, i - rad);
        int hi = Math.min(netsize, i + rad);

        int j = i + 1;
        int k = i - 1;
        int m = 1;
        while ((j < hi) || (k > lo)) {
            int a = radpower[m++];
            if (j < hi) {
                int[] p = network[j++];
                try {
                    p[0] -= (a * (p[0] - b)) / alpharadbise;
                    p[1] -= (a * (p[1] - g)) / alpharadbise;
                    p[2] -= (a * (p[2] - r)) / alpharadbise;
                } catch (Exception ignored) {
                }
            }
            if (k > lo) {
                int[] p = network[k--];
                try {
                    p[0] -= (a * (p[0] - b)) / alpharadbise;
                    p[1] -= (a * (p[1] - g)) / alpharadbise;
                    p[2] -= (a * (p[2] - r)) / alpharadbise;
                } catch (Exception ignored) {
                }
            }
        }
    }

    protected void altersingle(int alpha, int i, int b, int g, int r) {
        int[] n = network[i];
        n[0] -= (alpha * (n[0] - b)) / initalpha;
        n[1] -= (alpha * (n[1] - g)) / initalpha;
        n[2] -= (alpha * (n[2] - r)) / initalpha;
    }

    protected int contest(int b, int g, int r) {
        int bestd = ~(((int) 1) << 31);
        int bestbiasd = bestd;
        int bestpos = -1;
        int bestbiaspos = bestpos;

        for (int i = 0; i < netsize; i++) {
            int[] n = network[i];
            int dist = Math.abs(n[0] - b) + Math.abs(n[1] - g) + Math.abs(n[2] - r);
            if (dist < bestd) {
                bestd = dist;
                bestpos = i;
            }
            int biasdist = dist - ((bias[i]) >> (intbiasshift - netbiasshift));
            if (biasdist < bestbiasd) {
                bestbiasd = biasdist;
                bestbiaspos = i;
            }
            int betafreq = (freq[i] >> betashift);
            freq[i] -= betafreq;
            bias[i] += (betafreq << gammashift);
        }
        freq[bestpos] += beta;
        bias[bestpos] -= betagamma;
        return (bestbiaspos);
    }

    public void learn() {
        if (lengthcount < minpicturebytes) {
            samplefac = 1;
        }
        alphadec = 30 + ((samplefac - 1) / 3);
        byte[] p = thepicture;
        int pix = 0;
        int lim = lengthcount;
        int samplepixels = lengthcount / (3 * samplefac);
        int delta = samplepixels / ncycles;
        int alpha = initalpha;
        int radius = initradius;

        int rad = radius >> radiusbiasshift;
        if (rad <= 1) rad = 0;
        for (int i = 0; i < rad; i++) {
            radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad));
        }

        int step;
        if (lengthcount < minpicturebytes) {
            step = 3;
        } else if ((lengthcount % prime1) != 0) {
            step = 3 * prime1;
        } else if ((lengthcount % prime2) != 0) {
            step = 3 * prime2;
        } else if ((lengthcount % prime3) != 0) {
            step = 3 * prime3;
        } else {
            step = 3 * prime4;
        }

        int i = 0;
        while (i < samplepixels) {
            int b = (p[pix + 0] & 0xff) << netbiasshift;
            int g = (p[pix + 1] & 0xff) << netbiasshift;
            int r = (p[pix + 2] & 0xff) << netbiasshift;
            int j = contest(b, g, r);

            altersingle(alpha, j, b, g, r);
            if (rad != 0) {
                alterneigh(rad, j, b, g, r);
            }

            pix += step;
            if (pix >= lim) {
                pix -= lengthcount;
            }

            i++;
            if (delta == 0) delta = 1;
            if (i % delta == 0) {
                alpha -= alpha / alphadec;
                radius -= radius / radiusdec;
                rad = radius >> radiusbiasshift;
                if (rad <= 1) rad = 0;
                for (int k = 0; k < rad; k++) {
                    radpower[k] = alpha * (((rad * rad - k * k) * radbias) / (rad * rad));
                }
            }
        }
    }

    public int map(int b, int g, int r) {
        int bestd = 1000;
        int best = -1;
        int i = netindex[g];
        int j = i - 1;

        while ((i < netsize) || (j >= 0)) {
            if (i < netsize) {
                int[] p = network[i];
                int dist = p[1] - g;
                if (dist >= bestd) {
                    i = netsize;
                } else {
                    i++;
                    if (dist < 0) dist = -dist;
                    int a = p[0] - b;
                    if (a < 0) a = -a;
                    dist += a;
                    if (dist < bestd) {
                        a = p[2] - r;
                        if (a < 0) a = -a;
                        dist += a;
                        if (dist < bestd) {
                            bestd = dist;
                            best = p[3];
                        }
                    }
                }
            }
            if (j >= 0) {
                int[] p = network[j];
                int dist = g - p[1];
                if (dist >= bestd) {
                    j = -1;
                } else {
                    j--;
                    if (dist < 0) dist = -dist;
                    int a = p[0] - b;
                    if (a < 0) a = -a;
                    dist += a;
                    if (dist < bestd) {
                        a = p[2] - r;
                        if (a < 0) a = -a;
                        dist += a;
                        if (dist < bestd) {
                            bestd = dist;
                            best = p[3];
                        }
                    }
                }
            }
        }
        return (best);
    }
}

