package com.vivanetpay.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Iconos generados por código (sin archivos binarios). */
public final class AppIcons {
    private AppIcons() {
    }

    /** Icono de bandeja 32x32: círculo con "V"; gris con barra si la voz/captura está en pausa. */
    public static BufferedImage tray(boolean attention) {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(attention ? new Color(0x8A, 0x5A, 0x00) : new Color(0x0B, 0x5C, 0xAD));
            g.fillOval(1, 1, 30, 30);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.drawString("V", 9, 24);
            if (attention) {
                g.setStroke(new java.awt.BasicStroke(3f));
                g.drawLine(5, 27, 27, 5);
            }
        } finally {
            g.dispose();
        }
        return img;
    }
}
