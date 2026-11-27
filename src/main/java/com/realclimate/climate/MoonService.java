package com.realclimate.climate;

/**
 * Реальные фазы Луны. Ванильный клиент берёт фазу из дня мира: (dayTime / 24000) % 8, где 0 = полнолуние,
 * 1 = убывающая полная, 2 = последняя четверть, 3 = убывающий полумесяц, 4 = новолуние,
 * 5 = растущий полумесяц, 6 = первая четверть, 7 = растущая полная. Мы шлём игроку личное
 * время неба (ClientboundSetTimePacket), поэтому можем добавить к нему «номер дня» так, чтобы ванильная фаза
 * совпала с реальной (средний синодический месяц, точность около полудня). Время суток (dayTime % 24000) при этом
 * не меняется.
 */
public final class MoonService {
    /** Средний синодический месяц, сутки. */
    public static final double SYNODIC_DAYS = 29.530588853D;
    /** Новолуние 6 января 2000, 18:14 UTC, юлианская дата. */
    private static final double NEW_MOON_JD = 2451550.26D;

    private MoonService() {
    }

    /** Фаза Луны: 0 = новолуние, 0.25 = первая четверть, 0.5 = полнолуние, 0.75 = последняя четверть. */
    public static double phase(long epochMillis) {
        double jd = epochMillis / 86_400_000.0D + 2440587.5D;
        double p = ((jd - NEW_MOON_JD) / SYNODIC_DAYS) % 1.0D;
        return p < 0.0D ? p + 1.0D : p;
    }

    /** Номер фазы в ванильной нумерации (0 = полнолуние ... 4 = новолуние ... 7). */
    public static int vanillaPhase(long epochMillis) {
        return (int) ((Math.round(phase(epochMillis) * 8.0D) + 4L) % 8L);
    }

    /** Время дня для пакета: «номер дня» реальной фазы Луны * 24000 + время суток неба. */
    public static long dayTime(long skyTicks) {
        return vanillaPhase(System.currentTimeMillis()) * 24_000L + skyTicks;
    }
}
