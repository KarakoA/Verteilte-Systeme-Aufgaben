package aufgabed;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Ignore;
import org.junit.Test;

import de.sb.toolbox.math.ComplexMath;
import de.sb.toolbox.math.DoubleMath;
import de.sb.toolbox.math.FastFourierTestbed;
import de.sb.toolbox.math.FastFourierTransform;
import de.sb.toolbox.math.FloatCartesianComplex;
import de.sb.toolbox.math.FloatMath;
import de.sb.toolbox.math.PrimitiveCartesian;
import de.sb.toolbox.util.ArraySupport;


public class FastFourierTest1 {
	static private final byte MAGNITUDE = 18;
	static private final double DOUBLE_PRECISION = 1E-11f;
	static private final float SINGLE_PRECISION = 0;

	@Ignore
	@Test
	public void testFloatReference() {
		final float[] values = randomFloatVector(MAGNITUDE + 1), real = new float[1 << MAGNITUDE], imag = new float[1 << MAGNITUDE];
		ArraySupport.unbraid(values, real, imag);
		final FloatCartesianComplex[] vector = new FloatCartesianComplex[1 << MAGNITUDE];
		for (int index = 0; index < vector.length; ++index) {
			vector[index] = new FloatCartesianComplex(real[index], imag[index]);
		}

		for (final boolean inverse : new boolean[] { false, true }) {
			final FloatCartesianComplex[] fft1 = ComplexMath.clone(vector);
			final float[] fft2 = values.clone(), fft3r = real.clone(), fft3i = imag.clone();
			FastFourierTransform.transform(inverse, fft1);
			FastFourierTransform.transform(inverse, fft2);
			FastFourierTransform.transform(inverse ? fft3i : fft3r, inverse ? fft3r : fft3i);

			if (inverse) {
				final float norm = 1f / fft3r.length;
				FloatMath.mul(fft3r, norm);
				FloatMath.mul(fft3i, norm);
			}
			
			for (int index = 0; index < vector.length; ++index) {
				assertEquals(fft1[index].re(), fft2[index << 1], SINGLE_PRECISION);
				assertEquals(fft1[index].im(), fft2[(index << 1) + 1], SINGLE_PRECISION);
				assertEquals(fft1[index].re(), fft3r[index], SINGLE_PRECISION);
				assertEquals(fft1[index].im(), fft3i[index], SINGLE_PRECISION);
			}
		}
	}


	@Test
	public void testDoubleReference() {
		final double[] values = randomDoubleVector(MAGNITUDE + 1), real = new double[1 << MAGNITUDE], imag = new double[1 << MAGNITUDE];
		ArraySupport.unbraid(values, real, imag);
		final DoubleCartesianComplex1[] vector = new DoubleCartesianComplex1[1 << MAGNITUDE];
		for (int index = 0; index < vector.length; ++index) {
			vector[index] = new DoubleCartesianComplex1(real[index], imag[index]);
		}

		for (final boolean inverse : new boolean[] { false, true }) {
			final DoubleCartesianComplex1[] fft1 = ComplexMath.clone(vector);
			final double[] fft2 = values.clone(), fft3r = real.clone(), fft3i = imag.clone();
			FastFourierTransform.transform(inverse, fft1);
			FastFourierTransform.transform(inverse, fft2);
			FastFourierTransform.transform(inverse ? fft3i : fft3r, inverse ? fft3r : fft3i);

			if (inverse) {
				final double norm = 1d / fft3r.length;
				DoubleMath.mul(fft3r, norm);
				DoubleMath.mul(fft3i, norm);
			}
			
			for (int index = 0; index < vector.length; ++index) {
				assertEquals(fft1[index].re(), fft2[index << 1], DOUBLE_PRECISION);
				assertEquals(fft1[index].im(), fft2[(index << 1) + 1], DOUBLE_PRECISION);
				assertEquals(fft1[index].re(), fft3r[index], DOUBLE_PRECISION);
				assertEquals(fft1[index].im(), fft3i[index], DOUBLE_PRECISION);
			}
		}
	}

	@Ignore
	@Test
	public void testFloatVectorMath() {
		final float[] values = randomFloatVector(MAGNITUDE + 1);
		for (final boolean inverse : new boolean[] { false, true }) {
			final float[] fft1 = values.clone(), fft2 = values.clone();
			FastFourierTransform.transform(inverse, fft1);
			FloatMath.fft(inverse, false, fft2);
			assertArrayEquals(fft1, fft2, SINGLE_PRECISION);
		}
	}

	@Ignore
	@Test
	public void testDoubleVectorMath() {
		final double[] values = randomDoubleVector(MAGNITUDE + 1);
		for (final boolean inverse : new boolean[] { false, true }) {
			final double[] fft1 = values.clone(), fft2 = values.clone();
			FastFourierTransform.transform(inverse, fft1);
			DoubleMath.fft(inverse, false, fft2);
			assertArrayEquals(fft1, fft2, DOUBLE_PRECISION);
		}
	}

	@Ignore
	@Test
	public void testDoubleTestbed() {
		final double[] values = randomDoubleVector(MAGNITUDE + 1);
		for (final boolean inverse : new boolean[] { false, true }) {
			final double[] fft1 = values.clone(), fft2 = values.clone();
			FastFourierTransform.transform(inverse, fft1);
			FastFourierTestbed.fft(inverse, fft2);
			assertArrayEquals(fft1, fft2, DOUBLE_PRECISION);
		}
	}

	@Ignore
	@Test
	public void testLongCartesian() {
		final float[] values = randomFloatVector(MAGNITUDE + 1);
		final long[]  complex = new long[1 << MAGNITUDE];
		for (int index = 0; index < values.length; index += 2) {
			complex[index >> 1] = PrimitiveCartesian.fromCartesian(values[index], values[index + 1]);	
		}

		for (final boolean inverse : new boolean[] { false, true }) {
			final float[] fft1 = values.clone();
			final long[] fft2 = complex.clone();
			FastFourierTransform.transform(inverse, fft1);
			PrimitiveCartesian.fft(inverse, false, fft2);
			for (int index = 0; index < values.length; index += 2) {
				assertEquals(fft1[index],     PrimitiveCartesian.re(fft2[index >> 1]), SINGLE_PRECISION);
				assertEquals(fft1[index + 1], PrimitiveCartesian.im(fft2[index >> 1]), SINGLE_PRECISION);
			}
		}
	}

	@Ignore
	@Test
	public void testFloatCartesian() {
		final float[] values = randomFloatVector(MAGNITUDE + 1);
		final FloatCartesianComplex[] vector = new FloatCartesianComplex[values.length >> 1];
		for (int index = 0; index < vector.length; ++index) {
			vector[index] = new FloatCartesianComplex(values[index << 1], values[(index << 1) + 1]);
		}

		for (final boolean inverse : new boolean[] { false, true }) {
			final FloatCartesianComplex[] fft1 = ComplexMath.clone(vector);
			final FloatCartesianComplex[] fft2 = ComplexMath.clone(vector);
			final FloatCartesianComplex unit = new FloatCartesianComplex();

			FastFourierTransform.transform(inverse, fft1);
			unit.fft(inverse, false, fft2);
			for (int index = 0; index < vector.length; ++index) {
				assertEquals(fft1[index].re(), fft2[index].re(), SINGLE_PRECISION);
				assertEquals(fft1[index].im(), fft2[index].im(), SINGLE_PRECISION);
			}
		}
	}

	
	@Test
	public void testDoubleCartesian() {
		final double[] values = randomDoubleVector(MAGNITUDE + 1);
		final DoubleCartesianComplex1[] vector = new DoubleCartesianComplex1[values.length >> 1];
		for (int index = 0; index < vector.length; ++index) {
			vector[index] = new DoubleCartesianComplex1(values[index << 1], values[(index << 1) + 1]);
		}

		for (final boolean inverse : new boolean[] { false, true }) {
			final DoubleCartesianComplex1[] fft1 = ComplexMath.clone(vector);
			final DoubleCartesianComplex1[] fft2 = ComplexMath.clone(vector);
			final DoubleCartesianComplex1 unit = new DoubleCartesianComplex1();

			FastFourierTransform.transform(inverse, fft1);
			unit.fft(inverse, false, fft2);
			for (int index = 0; index < vector.length; ++index) {
				assertEquals(fft1[index].re(), fft2[index].re(), DOUBLE_PRECISION);
				assertEquals(fft1[index].im(), fft2[index].im(), DOUBLE_PRECISION);
			}
		}
	}


	static private double[] randomDoubleVector (final int magnitude) {
		final double[] vector = new double[1 << magnitude];
		for (int index = 0; index < vector.length; ++index) {
			vector[index] = 2 * ThreadLocalRandom.current().nextDouble() - 1;
		}
		return vector;
	}


	static private float[] randomFloatVector (final int magnitude) {
		final float[] vector = new float[1 << magnitude];
		for (int index = 0; index < vector.length; ++index) {
			vector[index] = (float) (2 * ThreadLocalRandom.current().nextDouble() - 1);
		}
		return vector;
	}
}