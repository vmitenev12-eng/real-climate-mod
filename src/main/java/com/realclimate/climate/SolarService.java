package com.realclimate.climate;

import java.time.LocalDate;

/**
 * Солнце по-настоящему: склонение, уравнение времени, восход/закат и длина
 * светового дня для широты и даты, пересчёт местного времени в «небесные
 * тики» клиента. Всё автономно, интернет не нужен; точность ~±5–15 минут
 * на высоких широтах — для неба над миром хватает, а при доступном
 * open-meteo значения подменяются точными (WeatherService).
 *
 * Контроль на 27 ноября (расчёт -> справочник):
 *   Москва         55.75N  37.62E  день 7:41 -> 7:37, восход 8:28 -> 8:27
 *   Архангельск    64.54N  40.54E  день 5:17 -> 5:33, восход 9:28 -> 9:19
 *   Гвинейский з.   0.04N   0.01E  день 12:07 -> 12:07, восход 5:45 -> 5:44
 */
public final class SolarService {
    private SolarService() {}

    /** Часовой пояс по долготе, секунд от UTC: 15° = 1 час, округлённо
     * (Москва 37.6° -> +3 ч, Гвинейский залив 0° -> +0). Настоящие границы
     * поясов знает open-meteo; этот расчёт — офлайн-запасной. */
    public static int zoneOffsetSeconds(double lon) {
        return (int) Math.round(lon / 15.0) * 3600;
    }

    /** Местное гражданское время в точке, час 0..24: UTC + настоящий пояс
     * из сети, а без сети — пояс долготы. */
    public static double civilHours(double lat, double lon) {
        Integer tz = WeatherService.utcOffsetSeconds(lat, lon);
        int offsetSec = tz != null ? tz : zoneOffsetSeconds(lon);
        long sec = Math.floorMod(System.currentTimeMillis() / 1000L + offsetSec, 86_400L);
        return sec / 3600.0;
    }

    /** Солнечное склонение, градусы (ряд Спенсера по дню года). */
    public static double declination(LocalDate date) {
        double g = 2.0 * Math.PI * (date.getDayOfYear() - 1) / 365.0;
        return Math.toDegrees(0.006918
                - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g)
                - 0.006758 * Math.cos(2.0 * g) + 0.000907 * Math.sin(2.0 * g)
                - 0.002697 * Math.cos(3.0 * g) + 0.00148 * Math.sin(3.0 * g));
    }

    /** Уравнение времени, минуты: поправка солнечного полдня к среднему. */
    public static double equationOfTime(LocalDate date) {
        double b = Math.toRadians(360.0 / 365.0 * (date.getDayOfYear() - 81));
        return 9.87 * Math.sin(2.0 * b) - 7.53 * Math.cos(b) - 1.5 * Math.sin(b);
    }

    /** Часовой угол восхода/заката, градусы (рефракция -0.833°).
     * NaN — полярный день или полярная ночь. */
    public static double hourAngle(double lat, LocalDate date) {
        double phi = Math.toRadians(lat);
        double d = Math.toRadians(declination(date));
        double cosW = (Math.sin(Math.toRadians(-0.833)) - Math.sin(phi) * Math.sin(d))
                / (Math.cos(phi) * Math.cos(d));
        if (cosW < -1.0 || cosW > 1.0) return Double.NaN;
        return Math.toDegrees(Math.acos(cosW));
    }

    /** Длина светового дня, часов: 0 — полярная ночь, 24 — полярный день. */
    public static double dayLength(double lat, LocalDate date) {
        double w = hourAngle(lat, date);
        if (Double.isNaN(w)) {
            double phi = Math.toRadians(lat);
            double d = Math.toRadians(declination(date));
            return Math.sin(phi) * Math.sin(d) > 0.0 ? 24.0 : 0.0;
        }
        return 2.0 * w / 15.0;
    }

    /** Солнечный полдень по гражданскому времени точки, час. */
    public static double solarNoonCivil(double lat, double lon, LocalDate date) {
        return 12.0 - lon / 15.0 - equationOfTime(date) / 60.0
                + zoneOffsetSeconds(lon) / 3600.0;
    }

    /** Восход и закат сегодня по местному времени {час, час}: точные из сети,
     * а без неё — из формулы. null — полярный день/полярная ночь. */
    public static double[] sunriseSunset(double lat, double lon) {
        double[] rs = WeatherService.sunriseSunset(lat, lon);
        if (rs != null) return rs;
        LocalDate date = LocalDate.now();
        double len = dayLength(lat, date);
        if (len <= 0.005 || len >= 23.995) return null;
        double noon = solarNoonCivil(lat, lon, date);
        return new double[]{noon - len / 2.0, noon + len / 2.0};
    }

    /** Сила солнца 0..1 в эту минуту: 0 ночью, ~1 в солнечный полдень. */
    public static double sunFactor(double lat, double lon) {
        double[] rs = sunriseSunset(lat, lon);
        if (rs == null) {
            // За полярным кругом: кромешная ночь либо низкое круговое солнце.
            double len = dayLength(lat, LocalDate.now());
            return len > 12.0 ? 0.35 : 0.0;
        }
        double civil = civilHours(lat, lon);
        if (civil <= rs[0] || civil >= rs[1]) return 0.0;
        return Math.sin(Math.PI * (civil - rs[0]) / (rs[1] - rs[0]));
    }

    /**
     * Местное время точки -> «небесные тики» клиента (0..24000): отрезок
     * восход->закат растягивается на 0..12000, закат->восход — на 12000..24000.
     * Поэтому зимой в Архангельске день и правда длится ~5.5 часа, а в
     * Гвинейском заливе ~12: солнце встаёт и садится в свои часы, просто
     * днём ползёт быстрее, а ночью медленнее обычного.
     */
    public static long skyTicks(double lat, double lon) {
        double[] rs = sunriseSunset(lat, lon);
        if (rs == null) {
            double len = dayLength(lat, LocalDate.now());
            return len > 12.0 ? 6000L : 18000L; // полярный день / полярная ночь
        }
        double civil = civilHours(lat, lon);
        double len = rs[1] - rs[0];
        double ticks;
        if (civil >= rs[0] && civil <= rs[1]) {
            ticks = (civil - rs[0]) / len * 12000.0;
        } else {
            double night = 24.0 - len;
            double sinceSunset = civil > rs[1] ? civil - rs[1] : civil + 24.0 - rs[1];
            ticks = 12000.0 + sinceSunset / night * 12000.0;
        }
        return Math.floorMod((long) Math.round(ticks), 24000L);
    }
}
