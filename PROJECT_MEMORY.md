# PROJECT_MEMORY — Real Climate Mod (Forge 1.20.1)
Проект: work/real_climate_mod (GDD: input/GDD-Real-Climate-Mod (1).txt)
Репо: github.com/vmitenev12-eng/real-climate-mod (private)

## Сделано
- Java: RealClimate, registry/ModBlocks, ModCreativeTab, block/FacingBlock, item/HammerItem
- climate/: Geo, ClimateZone, Perlin1D, ClimateModel, Forecast, ClimateService, ClimateConfig, ClimateEvents, BodyTemperature
- 14 blockstates + 14 моделей блоков + 14 item-моделей; все vent_* используют textures/block/vent_metal.png (1000388937.png)
- lang ru_ru + en_us, рецепт hammer
- loot tables (14 блоков, дроп себя) + тег mineable/pickaxe
- Текстуры vent_metal/conditioner_body/conditioner_detail/radiator_body приведены к 256x256 (оригиналы в backup_textures/), ref_sheet.png удалён из ресурсов
- Первая сборка GitHub Actions успешна (0.1.0). Локальный gradle запрещён (EXIT 137)

## Следующие шаги
1. Запушить правки, собрать, проверить jar в игре
2. Системы GDD: тепло/охлаждение зон (BlockEntity AC/radiator), BodyTemperature HUD, прогноз
