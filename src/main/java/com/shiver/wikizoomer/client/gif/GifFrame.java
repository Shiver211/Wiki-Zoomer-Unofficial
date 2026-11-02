package com.shiver.wikizoomer.client.gif;


import java.awt.image.BufferedImage;

public class GifFrame {
    public final BufferedImage image;
    public int delayCentiseconds;

    public GifFrame(BufferedImage image, int delayCentiseconds) {
        this.image = image;
        this.delayCentiseconds = delayCentiseconds;
    }
}
