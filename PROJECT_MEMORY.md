# PROJECT_MEMORY — Real Climate Mod (Forge 1.20.1)
Проект: work/real_climate_mod (GDD: input/GDD-Real-Climate-Mod (1).txt)
Репо: github.com/vmitenev12-eng/real-climate-mod (public)

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

## Солнце и живая погода (0.1.29, 2026-11-27)
- climate/SolarService: склонение (Спенсер), уравнение времени, восход/закат и длина дня
  по широте+дате; местное время -> «небесные тики» (день 0..12000, ночь 12000..24000);
  sunFactor для нагрева; пояс офлайн = round(долгота/15)
- climate/WeatherService: open-meteo без ключа, фоновые запросы (daemon-поток), кэш клеток
  0.5° на 30 мин; реальная температура, почасовой прогноз, настоящий часовой пояс,
  точные восход/закат; офлайн/ошибка — молча климатическая модель. Конфиг online_weather
- RealTimeService: персональное небо каждому игроку (ClientboundSetTimePacket раз в секунду
  после ванильной рассылки); мировое время по-прежнему ведёт якорь; API ptm2
  (isRealTime/zoneOffsetSeconds) не менялся
- ClimateService.temperature: живая погода или модель; суточный ход по местному солнечному
  часу (был сырой UTC); дождь/гроза не вычитаются дважды при живой погоде
- BodyTemperature: нагрев от солнца по реальному световому дню широты (sunFactor)
- /climate: местное время, восход/закат, длина дня, пометка источника (open-meteo/офлайн)
- Контроль формул на 27.11: Москва 7:41/восход 8:28 (справ. 7:37/8:27),
  Архангельск 5:17/9:28 (5:33/9:19), Гвинейский залив 12:07/5:45 (12:07/5:44);
  с сетью open-meteo значения точные

## Механики по местному солнцу (0.1.30, 2026-11-27)
- SolarService: isDayAt (день/ночь точки по восходу-закату), skyDarkenAt
  (затемнение 0..11 в шкале ванильного getSkyDarken, с поправкой на дождь/грозу)
- mixin/MixinLevelReader: getMaxLocalRawBrightness (обе перегрузки) вычитает затемнение
  по солнцу точки вместо глобального getSkyDarken → спавн мобов
  (Monster.isDarkEnoughToSpawn), яркость при горении нежити и любые проверки
  освещённости живут по местному времени; клиент и не-оверворлд не тронуты
- mixin/MixinMobSun: @Redirect Level.isDay() в Mob.isSunBurnTick → зомби, скелеты
  и фантомы горят по восходу/закату своей точки
- ClimateEvents: SleepingTimeCheckEvent → сон разрешён в местную ночь кровати
  (ALLOW/DENY по isDayAt), и при укладывании, и при продолжении сна
- WeatherService: +current_weather=true → weatherCode(lat,lon) (WMO-код точки)
- RealTimeService: раз в 100 тиков глобальный дождь/гроза мира = живой weathercode
  якорной точки (setWeatherParameters); офлайн — погоду не трогаем
- Осознанно оставлены якорю: расписания жителей (Brain без ссылки на сущность),
  датчики дневного света (читают свой getSkyDarken), лунный спавн слизней;
  посевы растут и ночью, как в ванили (CropBlock зовёт getRawBrightness с 0)
