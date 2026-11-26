package com.realclimate.client;

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
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
        if (cachedLevel == mc.level && gameTime - lastLinkScan < LINK_SCAN_INTERVAL_TICKS) return;
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
            ribbon(buf, m, cam, ax, ay, az, bx, by, bz, 0.03f, link.r, link.g, link.b, 170);
            marker(buf, m, cam, ax, ay, az, 0.08f, link.r, link.g, link.b, 220);
            marker(buf, m, cam, bx, by, bz, 0.12f, link.r, link.g, link.b, 230);
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
                x - rx + ux, y - ry + uy, z - rz + uz,
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

        // HUD показывает серверные значения (приходят каждые 2 с), сглаженные
        // на клиенте — цифры живые, без ступенек и без расхождения с датчиками.
        double body = BodyTemperature.bodyDisplay(mc.player);
        double ambient = BodyTemperature.ambientDisplay(mc.player);
        double insulation = BodyTemperature.insulationDisplay(mc.player);

        int h = e.getGuiGraphics().guiHeight();
        int w = e.getGuiGraphics().guiWidth();
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

        String keyHint = w < 320
                ? "Shift+ПКМ: разорвать"
                : "ПКМ: связать; Shift+ПКМ: разорвать";
        e.getGuiGraphics().drawString(mc.font, Component.literal(keyHint),
                6, w < 320 ? h - 94 : h - 78, 0xFFFFFFFF, true);
    }

    private static boolean isHoldingInstallationKey(Minecraft mc) {
        var held = mc.player.getMainHandItem();
        return !held.isEmpty() && "real_climate:installation_key".equals(
                BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
    }
}
