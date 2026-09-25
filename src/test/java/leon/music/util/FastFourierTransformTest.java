package leon.music.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

@DisplayName("FastFourierTransform Unit Tests")
class FastFourierTransformTest {

    @Test
    @DisplayName("100 Hz bass tone concentrates energy strictly in lowest frequency bands")
    void shouldConcentrateEnergyInLowestBandsForLowFrequencySineWave() {
        int sampleRate = 44100;
        int n = 1024;
        int numBands = 32;
        float[] samples = generateSineWave(100.0, sampleRate, n, 1.0f);

        float[] spectrum = FastFourierTransform.computePowerSpectrum(samples, sampleRate, numBands);

        assertThat(spectrum).hasSize(numBands);

        // Find the band with the maximum energy
        int peakBand = findPeakBand(spectrum);
        float peakValue = spectrum[peakBand];

        // 100 Hz at 44.1kHz with 32 bands should peak in the Sub-Bass / Bass bands (bands 6-8)
        assertThat(peakBand)
                .as("100 Hz tone should peak in the lowest bands (bass range)")
                .isLessThanOrEqualTo(8);
        assertThat(peakValue)
                .as("Peak magnitude should be strong")
                .isGreaterThan(0.8f);

        // High frequency bands (top half and especially top quarter: treble) must have near-zero energy
        float trebleEnergySum = 0.0f;
        for (int i = numBands / 2; i < numBands; i++) {
            assertThat(spectrum[i])
                    .as("Treble band " + i + " should have negligible energy for 100 Hz tone")
                    .isLessThan(0.01f);
            trebleEnergySum += spectrum[i];
        }
        assertThat(trebleEnergySum).isLessThan(0.02f);

        // Energy must be predominantly in the lower half of bands
        float lowEnergySum = 0.0f;
        float totalEnergy = 0.0f;
        for (int i = 0; i < numBands; i++) {
            if (i < numBands / 2) {
                lowEnergySum += spectrum[i];
            }
            totalEnergy += spectrum[i];
        }
        assertThat(lowEnergySum / totalEnergy)
                .as("Energy in the lower bands should exceed 98% of total energy")
                .isGreaterThan(0.98f);
    }

    @Test
    @DisplayName("10 kHz treble tone concentrates energy strictly in highest frequency bands")
    void shouldConcentrateEnergyInHighestBandsForHighFrequencySineWave() {
        int sampleRate = 44100;
        int n = 1024;
        int numBands = 32;
        float[] samples = generateSineWave(10000.0, sampleRate, n, 1.0f);

        float[] spectrum = FastFourierTransform.computePowerSpectrum(samples, sampleRate, numBands);

        assertThat(spectrum).hasSize(numBands);

        int peakBand = findPeakBand(spectrum);
        float peakValue = spectrum[peakBand];

        // 10 kHz with 32 bands should peak in the high treble bands (bands 27-29)
        assertThat(peakBand)
                .as("10 kHz tone should peak in the highest bands (treble range)")
                .isGreaterThanOrEqualTo(26);
        assertThat(peakValue)
                .as("Peak magnitude should be strong")
                .isGreaterThan(0.8f);

        // Low frequency bands (bottom half: bass, sub-bass, low-mids) must have near-zero energy
        float bassEnergySum = 0.0f;
        for (int i = 0; i < numBands / 2; i++) {
            assertThat(spectrum[i])
                    .as("Bass band " + i + " should have negligible energy for 10 kHz tone")
                    .isLessThan(0.01f);
            bassEnergySum += spectrum[i];
        }
        assertThat(bassEnergySum).isLessThan(0.01f);

        // Energy must be predominantly in the upper half of bands
        float highEnergySum = 0.0f;
        float totalEnergy = 0.0f;
        for (int i = 0; i < numBands; i++) {
            if (i >= numBands / 2) {
                highEnergySum += spectrum[i];
            }
            totalEnergy += spectrum[i];
        }
        assertThat(highEnergySum / totalEnergy)
                .as("Energy in the upper bands should exceed 98% of total energy")
                .isGreaterThan(0.98f);
    }

    @ParameterizedTest
    @ValueSource(ints = {512, 1024, 2048})
    @DisplayName("Hann window guarantees zero boundaries, symmetry, and bell curve")
    void shouldSatisfyHannWindowProperties(int size) {
        float[] samples = new float[size];
        Arrays.fill(samples, 1.0f);

        float[] windowed = FastFourierTransform.applyHannWindow(samples);

        // Window modifies in-place and returns the same array
        assertThat(windowed).isSameAs(samples);

        // 1. Zero at boundaries
        assertThat(windowed[0])
                .as("First sample must be exactly zero")
                .isEqualTo(0.0f);
        assertThat(windowed[size - 1])
                .as("Last sample must be exactly zero")
                .isEqualTo(0.0f);

        // 2. Perfect symmetry
        for (int i = 0; i < size / 2; i++) {
            int opposite = size - 1 - i;
            assertThat(windowed[i])
                    .as("Hann window must be symmetric: index " + i + " vs " + opposite)
                    .isCloseTo(windowed[opposite], offset(1e-6f));
        }

        // 3. Peak in the middle is close to 1.0
        assertThat(windowed[size / 2])
                .as("Midpoint of Hann window should approach 1.0")
                .isCloseTo(1.0f, offset(0.01f));
    }

    @Test
    @DisplayName("Exponential decay smoothing correctly tracks target and decays smoothly")
    void shouldApplyExponentialDecaySmoothingCorrectly() {
        float[] current = new float[]{1.0f, 0.5f};
        float[] targetZero = new float[]{0.0f, 0.0f};
        float decayFactor = 0.8f;

        // Step 1: decay towards zero
        FastFourierTransform.smoothBands(current, targetZero, decayFactor);
        assertThat(current[0]).isCloseTo(0.8f, offset(1e-5f));
        assertThat(current[1]).isCloseTo(0.4f, offset(1e-5f));

        // Step 2: continue decaying towards zero
        FastFourierTransform.smoothBands(current, targetZero, decayFactor);
        assertThat(current[0]).isCloseTo(0.64f, offset(1e-5f));
        assertThat(current[1]).isCloseTo(0.32f, offset(1e-5f));

        // Step 3: smooth rise towards target
        float[] targetHigh = new float[]{1.0f, 1.0f};
        FastFourierTransform.smoothBands(current, targetHigh, decayFactor);
        // current = current * 0.8 + 1.0 * 0.2
        assertThat(current[0]).isCloseTo(0.64f * 0.8f + 0.2f, offset(1e-5f));
        assertThat(current[1]).isCloseTo(0.32f * 0.8f + 0.2f, offset(1e-5f));
    }

    @Test
    @DisplayName("Exponential smoothing handles extreme decay factors 0.0 and 1.0")
    void shouldHandleBoundaryDecayFactors() {
        float[] current = new float[]{0.2f, 0.4f};
        float[] target = new float[]{0.9f, 0.7f};

        // decayFactor = 0.0: instant jump to target
        FastFourierTransform.smoothBands(current, target, 0.0f);
        assertThat(current[0]).isEqualTo(0.9f);
        assertThat(current[1]).isEqualTo(0.7f);

        // decayFactor = 1.0: frozen at current values
        FastFourierTransform.smoothBands(current, new float[]{0.0f, 0.0f}, 1.0f);
        assertThat(current[0]).isEqualTo(0.9f);
        assertThat(current[1]).isEqualTo(0.7f);
    }

    @Test
    @DisplayName("Cooley-Tukey Radix-2 FFT computes correct DFT for standard signals")
    void shouldComputeAccurateFftForKnownSignals() {
        // 4-point impulse response: [1, 0, 0, 0] -> all DFT bins should be 1.0
        float[] realImpulse = new float[]{1f, 0f, 0f, 0f};
        float[] imagImpulse = new float[]{0f, 0f, 0f, 0f};
        FastFourierTransform.fft(realImpulse, imagImpulse);
        for (int i = 0; i < 4; i++) {
            assertThat(realImpulse[i]).isCloseTo(1.0f, offset(1e-6f));
            assertThat(imagImpulse[i]).isCloseTo(0.0f, offset(1e-6f));
        }

        // 4-point DC signal: [1, 1, 1, 1] -> Bin 0 = 4, other bins = 0
        float[] realDc = new float[]{1f, 1f, 1f, 1f};
        float[] imagDc = new float[]{0f, 0f, 0f, 0f};
        FastFourierTransform.fft(realDc, imagDc);
        assertThat(realDc[0]).isCloseTo(4.0f, offset(1e-6f));
        assertThat(realDc[1]).isCloseTo(0.0f, offset(1e-6f));
        assertThat(realDc[2]).isCloseTo(0.0f, offset(1e-6f));
        assertThat(realDc[3]).isCloseTo(0.0f, offset(1e-6f));
    }

    @Test
    @DisplayName("FFT rejects invalid input sizes and null arrays")
    void shouldRejectInvalidFftInputs() {
        // Non-power-of-two size
        assertThatThrownBy(() -> FastFourierTransform.fft(new float[3], new float[3]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("power of 2");

        assertThatThrownBy(() -> FastFourierTransform.fft(new float[1000], new float[1000]))
                .isInstanceOf(IllegalArgumentException.class);

        // Mismatched lengths
        assertThatThrownBy(() -> FastFourierTransform.fft(new float[8], new float[4]))
                .isInstanceOf(IllegalArgumentException.class);

        // Null arrays
        assertThatThrownBy(() -> FastFourierTransform.fft(null, new float[4]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FastFourierTransform.fft(new float[4], null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Power spectrum returns strictly clamped [0.0, 1.0] values and handles edge cases")
    void shouldHandlePowerSpectrumEdgeCasesAndClamp() {
        // Silence returns all zero bands
        float[] silence = new float[1024];
        float[] silenceSpectrum = FastFourierTransform.computePowerSpectrum(silence, 44100, 32);
        for (float val : silenceSpectrum) {
            assertThat(val).isEqualTo(0.0f);
        }

        // Null or empty input
        assertThat(FastFourierTransform.computePowerSpectrum(null, 44100, 32))
                .hasSize(32)
                .containsOnly(0.0f);
        assertThat(FastFourierTransform.computePowerSpectrum(new float[0], 44100, 32))
                .hasSize(32)
                .containsOnly(0.0f);
        assertThat(FastFourierTransform.computePowerSpectrum(new float[1024], 44100, 0))
                .isEmpty();

        // Values are strictly clamped in [0.0, 1.0] even with large input
        float[] largeSignal = new float[1024];
        Arrays.fill(largeSignal, 10.0f);
        float[] largeSpectrum = FastFourierTransform.computePowerSpectrum(largeSignal, 44100, 32);
        for (float val : largeSpectrum) {
            assertThat(val).isBetween(0.0f, 1.0f);
        }
    }

    private static float[] generateSineWave(double frequency, int sampleRate, int length, float amplitude) {
        float[] samples = new float[length];
        double angularFreq = 2.0 * Math.PI * frequency / sampleRate;
        for (int i = 0; i < length; i++) {
            samples[i] = (float) (amplitude * Math.sin(angularFreq * i));
        }
        return samples;
    }

    private static int findPeakBand(float[] bands) {
        int peakIndex = 0;
        float maxVal = bands[0];
        for (int i = 1; i < bands.length; i++) {
            if (bands[i] > maxVal) {
                maxVal = bands[i];
                peakIndex = i;
            }
        }
        return peakIndex;
    }
}
