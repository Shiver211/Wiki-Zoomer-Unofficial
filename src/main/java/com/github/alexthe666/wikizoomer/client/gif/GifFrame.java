package com.github.alexthe666.wikizoomer.client.gif;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.image.BufferedImage;

@SideOnly(Side.CLIENT)
public class GifFrame {
    public final BufferedImage image;
    public int delayCentiseconds;

    public GifFrame(BufferedImage image, int delayCentiseconds) {
        this.image = image;
        this.delayCentiseconds = delayCentiseconds;
    }
}

