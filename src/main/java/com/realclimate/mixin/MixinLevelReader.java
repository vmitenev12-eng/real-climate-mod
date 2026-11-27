package com.realclimate.mixin;

/*
 * ОТКЛЮЧЕНО (не регистрируется в real_climate.mixins.json).
 *
 * Здесь был миксин на LevelReader.getMaxLocalRawBrightness (обе перегрузки), подменявший затемнение неба по местному
 * солнцу точки (спавн мобов и яркость при горении нежити). Сборка падала: Mixin не поддерживает @Inject в
 * интерфейсах ("Injector in interface is unsupported"). Исходный вариант - в коммите 3a96e30.
 *
 * Чтобы вернуть функцию, нужны инжекторы не в интерфейсе, а в классах-потребителях, например:
 * Monster.isDarkEnoughToSpawn (вызовы getMaxLocalRawBrightness) и места проверки яркости при горении нежити
 * (MixinMobSun), с проверкой на реальном клиенте и сервере: ошибка Mixin при запуске роняет игру (required=true).
 */
final class MixinLevelReader {
    private MixinLevelReader() {
    }
}
