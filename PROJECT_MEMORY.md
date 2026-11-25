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
- HVAC: наружный+внутренний+пульт+ключ, вентиляционные шахты с потерями, нить связи, GUI −20..+95

## Ветка feature/room-air-climate (2026-11-26)
- climate/IndoorClimate: «воздушная масса помещения» — равновесие по объёму (BFS до 2048 клеток),
  утечки через открытые двери/щели миниблоков, сквозняк у проёмов, инерционная температура
  воздуха у каждого игрока (persistentData realclimate_air) → плавные переходы через двери
- BodyTemperature: effectiveAmbient смешивает инерционный воздух с мгновенным по shelter/draft
- Блок thermometer (+BE): измеряет температуру вокруг себя (своей инерцией), ПКМ пустой рукой — показание
- Пульт (ControlPanel): авторежим — при привязанных термодатчиках ПИ-регулятор раз в 20 тиков
  вычисляет выходную температуру и пишет её в targetTemp наружных блоков (isAutoMode)
- Ключ: ПКМ по датчику → выбор датчика; ПКМ по пульту → привязка датчика; Shift+ПКМ по пульту сбрасывает всё
- HUD поднят выше панели инструментов (было наложение на хотбар)
- Нить связи теперь рисует и пульт↔термодатчик

## Следующие шаги
1. Сборка feature/room-air-climate на GitHub Actions, тест jar в игре (Pojav + сервер)
2. Проверка: сквозняки у дверей, поведение кондиционеров в большой комнате, авторегулировка пульта
3. Термодатчику — своя текстура (сейчас vent_metal), GUI пульта: показать фактическую температуру рядом с уставкой
