package com.shiver.wikizoomer.client.gif;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.awt.image.BufferedImage;

@OnlyIn(Dist.CLIENT)
public class GifFrame {
    public final BufferedImage image;
    public int delayCentiseconds;

    public GifFrame(BufferedImage image, int delayCentiseconds) {
        this.image = image;
        this.delayCentiseconds = delayCentiseconds;
    }
}
