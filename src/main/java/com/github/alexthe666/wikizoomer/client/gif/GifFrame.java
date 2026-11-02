package com.github.alexthe666.wikizoomer.client.gif;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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
