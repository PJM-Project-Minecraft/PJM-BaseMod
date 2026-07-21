package ru.liko.pjmbasemod.client.worldmap.color;

import ru.liko.pjmbasemod.client.worldmap.data.MapConstants;

/**
 * Чистое затенение пикселя карты по высотам (портировано из Xaero MapPixel, режим slopes>=2).
 * Из высоты столбца и высот соседей N/NW строится нормаль поверхности, скалярно умножается на
 * фиксированное направление света → яркость. Плюс лёгкое затенение по абсолютной высоте.
 * Тень (shadowRGB) в Фазе 1 белая (1.0), поэтому множитель одинаков по каналам.
 */
public final class PixelShader {

    private PixelShader() {}

    /**
     * @param baseColor 0xAARRGGBB до затенения
     * @param h         высота поверхности этого пикселя
     * @param hN        высота соседа на север (z-1); HEIGHT_UNSET → без уклона
     * @param hNW       высота соседа на северо-запад (x-1,z-1); HEIGHT_UNSET → без уклона
     * @return          затенённый 0xFFRRGGBB, либо 0 (прозрачный) для неотсканированного столбца
     */
    public static int shade(int baseColor, int h, int hN, int hNW) {
        if (h == MapConstants.HEIGHT_UNSET) {
            return 0; // прозрачно — покажется фон карты
        }
        int nN = (hN == MapConstants.HEIGHT_UNSET) ? h : hN;
        int nNW = (hNW == MapConstants.HEIGHT_UNSET) ? h : hNW;
        int vSlope = clamp(h - nN, -128, 127);
        int dSlope = clamp(h - nNW, -128, 127);

        double depth = Math.max(0.9, Math.min(1.0, h / 63.0));

        double cos;
        if (vSlope == 1 && dSlope == 1) {
            cos = 1.0;
        } else {
            double crossX = vSlope - dSlope;
            double crossZ = -vSlope;
            double cast = 1.0 - crossZ;
            double mag = Math.sqrt(crossX * crossX + 1.0 + crossZ * crossZ);
            cos = cast / mag / Math.sqrt(2.0);
        }
        double direct = (cos == 1.0)
                ? MapConstants.MAX_DIRECT
                : (cos > 0.0 ? Math.ceil(cos * 10.0) / 10.0 * MapConstants.MAX_DIRECT * MapConstants.DIRECT_QUANT : 0.0);

        double f = (MapConstants.AMBIENT_COLORED + MapConstants.AMBIENT_WHITE + direct) * depth;

        int r = clampByte((int) (((baseColor >> 16) & 0xFF) * f));
        int g = clampByte((int) (((baseColor >> 8) & 0xFF) * f));
        int b = clampByte((int) ((baseColor & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** Самопроверка формулы: уклон к свету ярче ровной земли, ровная ярче уклона от света. */
    public static void main(String[] args) {
        int white = 0xFFFFFFFF;
        int flat = shade(white, 64, 64, 64);
        int up = shade(white, 64, 63, 63);
        int down = shade(white, 60, 66, 66);
        if ((up & 0xFF) < (flat & 0xFF)) throw new AssertionError("up must be >= flat");
        if ((flat & 0xFF) <= (down & 0xFF)) throw new AssertionError("flat must be brighter than down-slope");
        if (shade(white, MapConstants.HEIGHT_UNSET, 0, 0) != 0) throw new AssertionError("unset must be transparent");
        System.out.printf("PixelShader OK  flat=%08x up=%08x down=%08x%n", flat, up, down);
    }
}
