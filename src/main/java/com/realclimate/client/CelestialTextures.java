package com.realclimate.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.realclimate.RealClimate;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Подмена ванильных текстур солнца и луны. Луна строится из фото полной луны (assets/real_climate/textures/moon_albedo.b64):
 * для каждой из 8 ванильных фаз рисуется терминатор по реальной геометрии освещения, неосвещённая часть чуть подсвечена
 * (пепельный свет). Солнце — белый диск с тёплым свечением и мягкими лучами. Текстуры создаются в коде,
 * ванильная отрисовка неба не меняется; при перезагрузке ресурсов (F3+T) подмена возвращается сама в течение секунды.
 * Отключение: JVM-параметр -Drealclimate.skytex=false.
 */
@Mod.EventBusSubscriber(modid = RealClimate.MODID, value = Dist.CLIENT)
public final class CelestialTextures {
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("realclimate.skytex"));
    private static final ResourceLocation SUN = new ResourceLocation("textures/environment/sun.png");
    private static final ResourceLocation MOON = new ResourceLocation("textures/environment/moon_phases.png");
    /** Доля клетки, занимаемая диском луны (остальное — запас по краям). */
    private static final double DISC = 0.94D;
    private static final String ALBEDO = "/assets/real_climate/textures/moon_albedo.b64";

    private static DynamicTexture sunTex;
    private static DynamicTexture moonTex;
    private static boolean failed;
    private static int tick;

    private CelestialTextures() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (!ENABLED || failed || e.phase != TickEvent.Phase.END || ++tick % 20 != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        try {
            if (sunTex == null || moonTex == null || sunTex.getPixels() == null || moonTex.getPixels() == null) {
                build();
            }
            TextureManager tm = mc.getTextureManager();
            if (tm.getTexture(SUN) != sunTex) {
                tm.register(SUN, sunTex);
            }
            if (tm.getTexture(MOON) != moonTex) {
                tm.register(MOON, moonTex);
            }
        } catch (Throwable t) {
            failed = true;
        }
    }

    private static void build() throws Exception {
        NativeImage albedo = loadAlbedo();
        try {
            sunTex = new DynamicTexture(makeSun(256));
            moonTex = new DynamicTexture(makeMoon(albedo, 128));
        } finally {
            albedo.close();
        }
        sunTex.setFilter(true, false);
        moonTex.setFilter(true, false);
    }

    private static NativeImage loadAlbedo() throws Exception {
        try (InputStream in = CelestialTextures.class.getResourceAsStream(ALBEDO)) {
            byte[] b64 = in.readAllBytes();
            byte[] png = Base64.getDecoder().decode(new String(b64, "US-ASCII").trim());
            return NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(png));
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double smooth(double t) {
        t = clamp(t, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private static int abgr(int a, int r, int g, int b) {
        return (a & 255) << 24 | (b & 255) << 16 | (g & 255) << 8 | (r & 255);
    }

    /** Билинейная выборка яркости альбедо (0..1) по координатам в пикселях. */
    private static double sample(NativeImage img, double x, double y) {
        int w = img.getWidth();
        int h = img.getHeight();
        x = clamp(x, 0.0D, w - 1.001D);
        y = clamp(y, 0.0D, h - 1.001D);
        int x0 = (int) x;
        int y0 = (int) y;
        double fx = x - x0;
        double fy = y - y0;
        double a = (img.getPixelRGBA(x0, y0) & 255) * (1 - fx) + (img.getPixelRGBA(x0 + 1, y0) & 255) * fx;
        double b = (img.getPixelRGBA(x0, y0 + 1) & 255) * (1 - fx) + (img.getPixelRGBA(x0 + 1, y0 + 1) & 255) * fx;
        return (a * (1 - fy) + b * fy) / 255.0D;
    }

    /** Атлас фаз 4x2 (как ванильный moon_phases.png): индекс 0 — полнолуние, 4 — новолуние. */
    private static NativeImage makeMoon(NativeImage alb, int cell) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, cell * 4, cell * 2, true);
        double acx = (alb.getWidth() - 1) / 2.0D;
        double acy = (alb.getHeight() - 1) / 2.0D;
        double ar = alb.getWidth() * 0.4812D;
        for (int idx = 0; idx < 8; idx++) {
            int col = idx % 4;
            int row = idx / 4;
            double p = ((idx + 4) % 8) / 8.0D;
            double theta = 2.0D * Math.PI * p;
            double cosT = Math.cos(theta);
            for (int py = 0; py < cell; py++) {
                for (int px = 0; px < cell; px++) {
                    double x = ((px + 0.5D) / cell * 2.0D - 1.0D) / DISC;
                    double y = ((py + 0.5D) / cell * 2.0D - 1.0D) / DISC;
                    double rr = x * x + y * y;
                    if (rr >= 1.0D) {
                        continue;
                    }
                    double edge = clamp((1.0D - Math.sqrt(rr)) / 0.03D, 0.0D, 1.0D);
                    double c = cosT * Math.sqrt(1.0D - y * y);
                    double d = p < 0.5D ? x - c : -c - x;
                    double lit = smooth(d / 0.06D + 0.5D);
                    double v = Math.pow(sample(alb, acx + x * ar, acy + y * ar), 1.1D);
                    double bright = v * (0.05D + 0.95D * lit) * edge;
                    int g = (int) Math.round(bright * 255.0D);
                    img.setPixelRGBA(col * cell + px, row * cell + py, abgr(255, g, g, g));
                }
            }
        }
        return img;
    }

    /** Солнце: белый диск, тёплое свечение и 12 мягких лучей. Прозрачность убывает к краям текстуры. */
    private static NativeImage makeSun(int n) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, n, n, true);
        double rd = 0.30D;
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                double x = (px + 0.5D) / n * 2.0D - 1.0D;
                double y = (py + 0.5D) / n * 2.0D - 1.0D;
                double r = Math.hypot(x, y);
                if (r >= 1.0D) {
                    continue;
                }
                double ang = Math.atan2(y, x);
                double rays = 1.0D + 0.45D * Math.pow(Math.abs(Math.cos(6.0D * ang)), 10.0D);
                double rg = Math.max(r, rd);
                double glow = Math.exp(-(rg - rd) / 0.14D) * 0.85D * rays * smooth((1.0D - rg) / 0.3D);
                double t = clamp((rg - rd) / 0.6D, 0.0D, 1.0D);
                double cr = 255.0D;
                double cg = 230.0D + (160.0D - 230.0D) * t;
                double cb = 140.0D + (40.0D - 140.0D) * t;
                double alpha = glow;
                if (r < rd) {
                    double w = smooth((rd - r) / 0.025D);
                    cr = cr + (255.0D - cr) * w;
                    cg = cg + (252.0D - cg) * w;
                    cb = cb + (238.0D - cb) * w;
                    alpha = glow + (1.0D - glow) * w;
                }
                img.setPixelRGBA(px, py, abgr((int) Math.round(clamp(alpha, 0.0D, 1.0D) * 255.0D),
                        (int) Math.round(cr), (int) Math.round(cg), (int) Math.round(cb)));
            }
        }
        return img;
    }
}
