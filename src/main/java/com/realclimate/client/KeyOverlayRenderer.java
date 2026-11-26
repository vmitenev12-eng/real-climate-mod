package com.realclimate.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realclimate.RealClimate;
import com.realclimate.climate.BodyTemperature;
import com.realclimate.climate.VentilationService;
import com.realclimate.tileentity.ACOutdoorBlockEntity;
import com.realclimate.tileentity.ControlPanelBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = RealClimate.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class KeyOverlayRenderer {
    private static final int LINK_SCAN_INTERVAL_TICKS = 20;
    private static List<LinkLine> cachedLinkLines = List.of();
    private static net.minecraft.client.multiplayer.ClientLevel cachedLevel;
    private static long lastLinkScan = Long.MIN_VALUE;

    /** Ванильная текстура инея из сыпучего снега — для предупреждения о замерзании. */
    private static final ResourceLocation FROST_TEXTURE =
            new ResourceLocation("textures/misc/powder_snow_outline.png");

    private KeyOverlayRenderer() {}

    /** Линия связи: от блока к блоку, со своим цветом по типу связки. */
    private record LinkLine(BlockPos from, BlockPos to, int r, int g, int b) {}

    /**
     * Линии связей рисуются полупрозрачными лентами БЕЗ теста глубины:
     * связь «наружный блок → внутренний» всегда проходит сквозь стену,
     * и с обычным тестом глубины она просто тонула в геометрии.
     * На PojavLauncher толщина линий (glLineWidth) не работает,
     * поэтому вместо RenderType.LINES — ленты из квадов, повёрнутые к камере.
     */
    private static final class LinkQuads extends RenderType {
        private LinkQuads(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                          boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }

        static final RenderType INSTANCE = create("realclimate_link_quads",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (cachedLevel != mc.level) {
            if (cachedLevel != null) VentilationService.clear(cachedLevel);
            cachedLinkLines = List.of();
            lastLinkScan = Long.MIN_VALUE;
        }
        if (mc.level == null || mc.player == null || !isHoldingInstallationKey(mc)) {
            cachedLinkLines = List.of();
            cachedLevel = mc.level;
            lastLinkScan = Long.MIN_VALUE;
            return;
        }

        long gameTime = mc.level.getGameTime();
        // ВАЖНО: lastLinkScan == Long.MIN_VALUE — маркер «скана ещё не было».
        // Без этой гардии gameTime - Long.MIN_VALUE переполняет long и даёт
        // огромное отрицательное число: условие «прошло < 20 тиков» истинно
        // всегда, и после повторного взятия ключа в руку скан не запускался
        // никогда (линии показывались только при входе в мир с ключом в руке).
        if (cachedLevel == mc.level && lastLinkScan != Long.MIN_VALUE
                && gameTime - lastLinkScan < LINK_SCAN_INTERVAL_TICKS) return;
        cachedLevel = mc.level;
        lastLinkScan = gameTime;

        List<LinkLine> lines = new ArrayList<>();
        BlockPos playerPos = mc.player.blockPosition();
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;
        int scanRadiusChunks = mc.options.renderDistance().get();
        for (int chunkX = centerChunkX - scanRadiusChunks;
             chunkX <= centerChunkX + scanRadiusChunks; chunkX++) {
            for (int chunkZ = centerChunkZ - scanRadiusChunks;
                 chunkZ <= centerChunkZ + scanRadiusChunks; chunkZ++) {
                var chunk = mc.level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) continue;
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof ACOutdoorBlockEntity outdoor) {
                        BlockPos from = outdoor.getBlockPos();
                        for (BlockPos indoorPos : outdoor.getLinkedIndoors()) {
                            lines.add(new LinkLine(from, indoorPos, 90, 200, 255));   // голубая: сплит-система
                        }
                        for (BlockPos panelPos : outdoor.getLinkedPanels()) {
                            lines.add(new LinkLine(from, panelPos, 255, 190, 90));    // янтарная: пульт
                        }
                    } else if (blockEntity instanceof ControlPanelBlockEntity panel) {
                        BlockPos from = panel.getBlockPos();
                        for (BlockPos sensorPos : panel.getLinkedThermometers()) {
                            lines.add(new LinkLine(from, sensorPos, 130, 255, 150));  // зелёная: датчик
                        }
                    }
                }
            }
        }
        cachedLinkLines = List.copyOf(lines);
    }

    @SubscribeEvent
    public static void onLinkLines(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!isHoldingInstallationKey(mc)) return;
        if (cachedLinkLines.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();

        var bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buf = bufferSource.getBuffer(LinkQuads.INSTANCE);

        for (LinkLine link : cachedLinkLines) {
            float ax = link.from.getX() + 0.5f;
            float ay = link.from.getY() + 0.5f;
            float az = link.from.getZ() + 0.5f;
            float bx = link.to.getX() + 0.5f;
            float by = link.to.getY() + 0.5f;
            float bz = link.to.getZ() + 0.5f;
            // Двухслойная лента: широкое мягкое свечение + яркая белёсая жилка.
            // На маленьком экране телефона тонкая полупрозрачная линия терялась.
            int cr = link.r + (255 - link.r) * 3 / 5;
            int cg = link.g + (255 - link.g) * 3 / 5;
            int cb = link.b + (255 - link.b) * 3 / 5;
            ribbon(buf, m, cam, ax, ay, az, bx, by, bz, 0.10f, link.r, link.g, link.b, 80);
            ribbon(buf, m, cam, ax, ay, az, bx, by, bz, 0.035f, cr, cg, cb, 240);
            marker(buf, m, cam, ax, ay, az, 0.10f, link.r, link.g, link.b, 200);
            marker(buf, m, cam, bx, by, bz, 0.16f, cr, cg, cb, 230);
        }
        bufferSource.endBatch(LinkQuads.INSTANCE);
        pose.popPose();
    }

    /** Лента между двумя точками, повёрнутая плоскостью к камере. */
    private static void ribbon(VertexConsumer buf, Matrix4f m, Vec3 cam,
                               float ax, float ay, float az, float bx, float by, float bz,
                               float half, int r, int g, int b, int alpha) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4f) return;
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        float vx = (float) (cam.x - mx), vy = (float) (cam.y - my), vz = (float) (cam.z - mz);
        // side = normalize(dir × view) * half
        float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0e-6f) {
            sx = 0; sy = half; sz = 0;
        } else {
            sx = sx / sl * half; sy = sy / sl * half; sz = sz / sl * half;
        }
        quad(buf, m,
                ax - sx, ay - sy, az - sz,
                ax + sx, ay + sy, az + sz,
                bx + sx, by + sy, bz + sz,
                bx - sx, by - sy, bz - sz,
                r, g, b, alpha);
    }

    /** Квадратный маркер на конце связи, всегда повёрнут к камере. */
    private static void marker(VertexConsumer buf, Matrix4f m, Vec3 cam,
                               float x, float y, float z, float half, int r, int g, int b, int alpha) {
        float vx = (float) (cam.x - x), vy = (float) (cam.y - y), vz = (float) (cam.z - z);
        float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl < 1.0e-4f) return;
        vx /= vl; vy /= vl; vz /= vl;
        // Первый базис плоскости: up × view
        float rx = -vz, ry = 0, rz = vx;   // (0,1,0) × view
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rl < 1.0e-4f) { rx = 1; ry = 0; rz = 0; rl = 1; }
        rx = rx / rl * half; ry = ry / rl * half; rz = rz / rl * half;
        // Второй базис: view × first (уже единичной длины × half)
        float ux = vy * rz - vz * ry, uy = vz * rx - vx * rz, uz = vx * ry - vy * rx;
        quad(buf, m,
                x - rx - ux, y - ry - uy, z - rz - uz,
                x + rx - ux, y + ry - uy, z + rz - uz,
                x + rx + ux, y + ry + uy, z + rz + uz,
                x - rx - ux, y - ry - uy, z - rz - uz,
                r, g, b, alpha);
    }

    private static void quad(VertexConsumer buf, Matrix4f m,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             int r, int g, int b, int alpha) {
        buf.vertex(m, x1, y1, z1).color(r, g, b, alpha).endVertex();
        buf.vertex(m, x2, y2, z2).color(r, g, b, alpha).endVertex();
        buf.vertex(m, x3, y3, z3).color(r, g, b, alpha).endVertex();
        buf.vertex(m, x4, y4, z4).color(r, g, b, alpha).endVertex();
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // HUD показывает серверные значения (приходят каждые 0.5 с), сглаженные
        // на клиенте — цифры живые, без ступенек и без расхождения с датчиками.
        double body = BodyTemperature.bodyDisplay(mc.player);
        double ambient = BodyTemperature.ambientDisplay(mc.player);
        double insulation = BodyTemperature.insulationDisplay(mc.player);

        int h = e.getGuiGraphics().guiHeight();
        int w = e.getGuiGraphics().guiWidth();

        // Предупреждения о замерзании/перегреве — под текстом HUD, но над миром.
        if (!mc.options.hideGui) renderThermalWarnings(e.getGuiGraphics(), body, w, h);

        // Текст поднят выше панели инструментов и брони, чтобы не перекрывать их.
        if (w < 320) {
            e.getGuiGraphics().drawString(mc.font, Component.literal(String.format("Тело %.1f°C", body)),
                    6, h - 84, 0xFFFFFFFF, true);
            e.getGuiGraphics().drawString(mc.font, Component.literal(String.format("Воздух %.1f°C", ambient)),
                    6, h - 74, 0xFFFFFFFF, true);
            e.getGuiGraphics().drawString(mc.font, Component.literal(String.format("Тепло %.0f%%", insulation * 100.0)),
                    6, h - 64, 0xFFFFFFFF, true);
        } else {
            e.getGuiGraphics().drawString(mc.font,
                    Component.literal(String.format("Тело: %.1f°C  |  Воздух: %.1f°C  |  Утепление: %.0f%%",
                            body, ambient, insulation * 100.0)),
                    10, h - 66, 0xFFFFFFFF, true);
        }

        if (!isHoldingInstallationKey(mc)) return;

        // Диагностика в одну строку с подсказкой, чтобы круг джойстика
        // PojavLauncher снизу слева не закрывал число. Если связей 0 при явно
        // привязанных блоках — данные не дошли до клиента; если > 0, а линий
        // не видно — проблема в отрисовке (напишите нам с этим числом).
        String keyHint = (w < 320
                ? "Shift+ПКМ: разорвать"
                : "ПКМ: связать; Shift+ПКМ: разорвать")
                + "  |  Связей: " + cachedLinkLines.size();
        e.getGuiGraphics().drawString(mc.font, Component.literal(keyHint),
                6, w < 320 ? h - 94 : h - 78, 0xFFFFFFFF, true);
    }

    /**
     * Визуальные предупреждения ДО того, как начнутся эффекты и урон.
     * Замерзание: ванильный иней (как в сыпучем снегу) наползает с краёв
     * постепенно — от 36.3 °C тела (лёгкий намёк) до 31 °C (плотный иней,
     * дальше уже урон). Перегрев: оранжевая пелена пара от краёв экрана —
     * от 37.3 °C до 40.5 °C. В креативе тело всегда 36.6 °C, так что
     * предупреждения там не показываются — это выживальческая механика.
     */
    private static void renderThermalWarnings(GuiGraphics g, double body, int w, int h) {
        double frost = Math.max(0.0, Math.min(1.0, (36.3 - body) / 5.3));
        if (frost > 0.02) {
            float a = (float) (frost * frost * 0.95);   // квадратично: мягкое начало
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            g.setColor(1.0f, 1.0f, 1.0f, a);
            g.blit(FROST_TEXTURE, 0, 0, -90, 0.0f, 0.0f, w, h, w, h);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }

        double heat = Math.max(0.0, Math.min(1.0, (body - 37.3) / 3.2));
        if (heat <= 0.02) return;
        double base = heat * heat;
        // Лёгкая общая рыжина + градиентные полосы пара от всех четырёх краёв.
        tint(g, 0, 0, w, h, (int) (base * 0.10 * 255), 255, 100, 20);
        int strips = 7;
        int bandY = Math.max(20, h / 5);
        int bandX = Math.max(24, w / 6);
        for (int i = 0; i < strips; i++) {
            double f = 1.0 - (double) i / strips;
            int alpha = (int) (base * f * f * 0.5 * 255);
            if (alpha < 3) continue;
            int y0 = bandY * i / strips, y1 = bandY * (i + 1) / strips;
            int x0 = bandX * i / strips, x1 = bandX * (i + 1) / strips;
            tint(g, 0, y0, w, y1, alpha, 255, 110, 25);              // верх
            tint(g, 0, h - y1, w, h - y0, alpha, 255, 110, 25);      // низ
            tint(g, x0, y0, x1, h - y0, alpha, 255, 110, 25);        // лево
            tint(g, w - x1, y0, w - x0, h - y0, alpha, 255, 110, 25); // право
        }
    }

    private static void tint(GuiGraphics g, int x0, int y0, int x1, int y1, int alpha, int r, int gg, int b) {
        if (alpha <= 0 || x1 <= x0 || y1 <= y0) return;
        g.fill(x0, y0, x1, y1, (alpha << 24) | (r << 16) | (gg << 8) | b);
    }

    private static boolean isHoldingInstallationKey(Minecraft mc) {
        var held = mc.player.getMainHandItem();
        return !held.isEmpty() && "real_climate:installation_key".equals(
                BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
    }
}
