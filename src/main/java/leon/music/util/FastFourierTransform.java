package leon.music.util;

/**
 * Utility class providing Fast Fourier Transform (FFT) computations, windowing functions,
 * logarithmic frequency band grouping, and exponential smoothing for audio visualization.
 */
public final class FastFourierTransform {

    private FastFourierTransform() {
        // Prevent instantiation of utility class
    }

    /**
     * Performs an in-place Cooley-Tukey Radix-2 Decimation-In-Time (DIT) FFT on the given
     * real and imaginary components.
     *
     * @param real the real component array, length must be a power of 2
     * @param imag the imaginary component array, length must match real.length
     * @throws IllegalArgumentException if arrays are null, mismatched in length, or length is not a power of 2
     */
    public static void fft(float[] real, float[] imag) {
        if (real == null || imag == null) {
            throw new IllegalArgumentException("Real and imaginary arrays must not be null");
        }
        if (real.length != imag.length) {
            throw new IllegalArgumentException("Real and imaginary arrays must have identical length");
        }
        int n = real.length;
        if (n <= 1) {
            return;
        }
        if ((n & (n - 1)) != 0) {
            throw new IllegalArgumentException("FFT length must be a power of 2, got: " + n);
        }

        // 1. Bit-reversal permutation
        int j = 0;
        for (int i = 0; i < n - 1; i++) {
            if (i < j) {
                float tempReal = real[i];
                real[i] = real[j];
                real[j] = tempReal;

                float tempImag = imag[i];
                imag[i] = imag[j];
                imag[j] = tempImag;
            }
            int k = n >> 1;
            while (k <= j) {
                j -= k;
                k >>= 1;
            }
            j += k;
        }

        // 2. Cooley-Tukey Radix-2 DIT butterfly computations
        for (int len = 2; len <= n; len <<= 1) {
            int halfLen = len >> 1;
            double angleStep = -2.0 * Math.PI / len;
            for (int k = 0; k < halfLen; k++) {
                double angle = k * angleStep;
                float wr = (float) Math.cos(angle);
                float wi = (float) Math.sin(angle);

                for (int i = 0; i < n; i += len) {
                    int evenIdx = i + k;
                    int oddIdx = evenIdx + halfLen;

                    float uReal = real[evenIdx];
                    float uImag = imag[evenIdx];
                    float oddR = real[oddIdx];
                    float oddI = imag[oddIdx];

                    // Complex multiplication: (wr + i*wi) * (oddR + i*oddI)
                    float tReal = wr * oddR - wi * oddI;
                    float tImag = wr * oddI + wi * oddR;

                    real[evenIdx] = uReal + tReal;
                    imag[evenIdx] = uImag + tImag;
                    real[oddIdx] = uReal - tReal;
                    imag[oddIdx] = uImag - tImag;
                }
            }
        }
    }

    /**
     * Applies a Hann (Hanning) window in-place to the given samples to eliminate spectral leakage.
     * The Hann window guarantees zero amplitude at the boundaries and perfect symmetry.
     *
     * @param samples the sample array to window in-place
     * @return the same sample array after windowing
     */
    public static float[] applyHannWindow(float[] samples) {
        if (samples == null) {
            return null;
        }
        int n = samples.length;
        if (n <= 1) {
            if (n == 1) {
                samples[0] = 0.0f;
            }
            return samples;
        }

        for (int i = 0; i < n; i++) {
            if (i == 0 || i == n - 1) {
                samples[i] = 0.0f;
            } else {
                float multiplier = 0.5f * (1.0f - (float) Math.cos(2.0 * Math.PI * i / (n - 1)));
                samples[i] *= multiplier;
            }
        }
        return samples;
    }

    /**
     * Computes the normalized logarithmic power/magnitude spectrum from raw audio samples.
     * <ol>
     *   <li>Applies a Hann window.</li>
     *   <li>Computes the complex FFT using radix-2 Cooley-Tukey.</li>
     *   <li>Calculates the magnitude spectrum: {@code sqrt(real^2 + imag^2)}.</li>
     *   <li>Groups linear FFT bins into {@code numBands} logarithmically spaced frequency bands
     *       (from ~20 Hz to ~20,000 Hz) representing Sub-Bass, Bass, Low-Mids, High-Mids, and Treble.</li>
     *   <li>Normalizes band magnitudes to a [0.0, 1.0] scale.</li>
     * </ol>
     *
     * @param samples    raw audio samples (PCM in [-1.0, 1.0])
     * @param sampleRate sampling rate in Hz (e.g. 44100)
     * @param numBands   number of output frequency bands (e.g. 32 to 64)
     * @return normalized band magnitudes array in range [0.0, 1.0] of length {@code numBands}
     */
    public static float[] computePowerSpectrum(float[] samples, int sampleRate, int numBands) {
        if (numBands <= 0) {
            return new float[0];
        }
        if (samples == null || samples.length < 2) {
            return new float[numBands];
        }
        if (sampleRate <= 0) {
            sampleRate = 44100;
        }

        int n = Integer.highestOneBit(samples.length);
        if (n < 2) {
            return new float[numBands];
        }

        float[] real = new float[n];
        // Take the latest n samples if samples.length > n
        System.arraycopy(samples, samples.length - n, real, 0, n);
        float[] imag = new float[n];

        applyHannWindow(real);
        fft(real, imag);

        int halfN = n / 2;
        float[] magnitudes = new float[halfN];
        // Coherent gain of Hann window is 0.5, so peak magnitude for a full-scale sinusoid is N / 4.
        // Scaling by 4.0 / N normalizes full-scale sinusoidal peaks to 1.0.
        float scale = 4.0f / n;

        for (int k = 0; k < halfN; k++) {
            float r = real[k];
            float im = imag[k];
            float mag = (float) Math.sqrt(r * r + im * im) * scale;
            magnitudes[k] = Math.max(0.0f, Math.min(1.0f, mag));
        }

        float[] bands = new float[numBands];
        double minFreq = 20.0;
        double maxFreq = Math.min(20000.0, sampleRate / 2.0);
        if (maxFreq <= minFreq) {
            maxFreq = Math.max(minFreq + 1.0, sampleRate / 2.0);
        }

        for (int b = 0; b < numBands; b++) {
            double fLow = minFreq * Math.pow(maxFreq / minFreq, (double) b / numBands);
            double fHigh = minFreq * Math.pow(maxFreq / minFreq, (double) (b + 1) / numBands);

            int startBin = (int) Math.round(fLow * n / sampleRate);
            int endBin = (int) Math.round(fHigh * n / sampleRate);

            // Confine to positive frequency bins [1, halfN]
            startBin = Math.max(1, startBin);
            endBin = Math.max(startBin + 1, endBin);
            endBin = Math.min(halfN, endBin);
            startBin = Math.min(startBin, endBin - 1);

            float maxMag = 0.0f;
            for (int k = startBin; k < endBin; k++) {
                if (magnitudes[k] > maxMag) {
                    maxMag = magnitudes[k];
                }
            }
            bands[b] = maxMag;
        }

        return bands;
    }

    /**
     * Applies exponential decay smoothing between current displayed bands and incoming target bands.
     * Useful for visualizer falloff / decay physics.
     *
     * <p>Formula: {@code currentBands[i] = currentBands[i] * decayFactor + targetBands[i] * (1.0f - decayFactor)}
     *
     * @param currentBands the existing band magnitudes (updated in-place)
     * @param targetBands  the new target band magnitudes from the latest spectrum analysis
     * @param decayFactor  smoothing / retention factor in range [0.0, 1.0] (e.g. 0.8f)
     * @return the smoothed bands array (same reference as {@code currentBands} if available)
     */
    public static float[] smoothBands(float[] currentBands, float[] targetBands, float decayFactor) {
        if (currentBands == null) {
            return targetBands != null ? targetBands.clone() : null;
        }
        if (targetBands == null) {
            return currentBands;
        }

        int len = Math.min(currentBands.length, targetBands.length);
        float factor = Math.max(0.0f, Math.min(1.0f, decayFactor));
        float targetMultiplier = 1.0f - factor;

        for (int i = 0; i < len; i++) {
            currentBands[i] = currentBands[i] * factor + targetBands[i] * targetMultiplier;
        }
        return currentBands;
    }
}
