package com.realclimate.client;

import com.realclimate.network.ModMessages;
import com.realclimate.network.PacketClimateSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class ClimateSettingsScreen extends Screen {
    private final BlockPos pos;
    private final double minimumTemperature;
    private final double maximumTemperature;
    private boolean enabled;
    private double targetTemperature;
    private EditBox temperatureBox;

    public ClimateSettingsScreen(BlockPos pos, boolean enabled, double targetTemperature) {
        this(pos, enabled, targetTemperature, -20.0, 95.0);
    }

    public ClimateSettingsScreen(BlockPos pos, boolean enabled, double targetTemperature,
                                 double minimumTemperature, double maximumTemperature) {
        super(Component.literal("Настройки климатического блока"));
        this.pos = pos;
        this.minimumTemperature = minimumTemperature;
        this.maximumTemperature = maximumTemperature;
        this.enabled = enabled;
        this.targetTemperature = clamp(targetTemperature);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 55;

        temperatureBox = new EditBox(this.font, centerX - 45, y + 35, 90, 20,
                Component.literal("Целевая температура"));
        temperatureBox.setValue(String.format(java.util.Locale.ROOT, "%.1f", targetTemperature));
        temperatureBox.setResponder(value -> {
            try {
                targetTemperature = clamp(Double.parseDouble(value.replace(',', '.')));
            } catch (NumberFormatException ignored) {
            }
        });
        this.addRenderableWidget(temperatureBox);

        this.addRenderableWidget(Button.builder(Component.literal(enabled ? "Включено: ВКЛ" : "Включено: ВЫКЛ"), button -> {
            enabled = !enabled;
            button.setMessage(Component.literal(enabled ? "Включено: ВКЛ" : "Включено: ВЫКЛ"));
        }).bounds(centerX - 55, y, 110, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("-1"), button -> changeTemperature(-1.0))
                .bounds(centerX - 90, y + 35, 35, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+1"), button -> changeTemperature(1.0))
                .bounds(centerX + 55, y + 35, 35, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Сохранить"), button -> save())
                .bounds(centerX - 55, y + 70, 110, 20).build());
    }

    private void changeTemperature(double delta) {
        targetTemperature = clamp(targetTemperature + delta);
        temperatureBox.setValue(String.format(java.util.Locale.ROOT, "%.1f", targetTemperature));
    }

    private void save() {
        try {
                targetTemperature = clamp(Double.parseDouble(temperatureBox.getValue().replace(',', '.')));
        } catch (NumberFormatException ignored) {
        }
        ModMessages.sendToServer(new PacketClimateSettings(pos, enabled, targetTemperature));
        this.onClose();
    }

    private double clamp(double temperature) {
        return Math.max(minimumTemperature, Math.min(maximumTemperature, temperature));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 75, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(String.format(java.util.Locale.ROOT,
                "Температура: от %.0f до +%.0f °C", minimumTemperature, maximumTemperature)),
                this.width / 2, this.height / 2 - 5, 0xCCCCCC);
    }
}
