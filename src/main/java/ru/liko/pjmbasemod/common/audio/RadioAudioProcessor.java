package ru.liko.pjmbasemod.common.audio;

public class RadioAudioProcessor {

    private static final int SAMPLE_RATE = 48000;
    private static final float HP_CUTOFF = 500f;
    private static final float LP_CUTOFF = 2500f;
    private static final int DOWNSAMPLE_FACTOR = 4;

    private final float hpAlpha;
    private final float lpAlpha;

    private float hpPrev = 0f;
    private float hpPrevIn = 0f;
    private float lpPrev = 0f;
    private float holdSample = 0f;
    private int holdCounter = 0;

    public RadioAudioProcessor() {
        float dt = 1.0f / SAMPLE_RATE;
        float hpRC = 1.0f / (2.0f * (float) Math.PI * HP_CUTOFF);
        float lpRC = 1.0f / (2.0f * (float) Math.PI * LP_CUTOFF);
        hpAlpha = hpRC / (hpRC + dt);
        lpAlpha = dt / (lpRC + dt);
    }

    public short[] process(short[] input) {
        short[] output = new short[input.length];

        for (int i = 0; i < input.length; i++) {
            float sample = input[i] / 32768.0f;

            float hp = hpAlpha * (hpPrev + sample - hpPrevIn);
            hpPrevIn = sample;
            hpPrev = hp;

            lpPrev = lpPrev + lpAlpha * (hp - lpPrev);
            float filtered = lpPrev;

            if (holdCounter <= 0) {
                holdSample = filtered;
                holdCounter = DOWNSAMPLE_FACTOR;
            }
            holdCounter--;
            filtered = holdSample;

            filtered *= 3.5f;
            if (filtered > 0.6f) filtered = 0.6f;
            if (filtered < -0.6f) filtered = -0.6f;
            filtered = (float) Math.tanh(filtered * 2.5) * 0.85f;

            output[i] = (short) Math.max(-32768, Math.min(32767, filtered * 32768.0f));
        }

        return output;
    }

    public void reset() {
        hpPrev = 0f;
        hpPrevIn = 0f;
        lpPrev = 0f;
        holdSample = 0f;
        holdCounter = 0;
    }
}
