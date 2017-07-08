package aufgabed;

import static java.lang.Math.cos;
import static java.lang.Math.scalb;
import static java.lang.Math.sin;
import static de.sb.toolbox.math.IntMath.floorLog2;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import de.sb.toolbox.Copyright;
import de.sb.toolbox.math.Complex;
import de.sb.toolbox.math.DoubleMath;
import de.sb.toolbox.math.FunctionTables;
import de.sb.toolbox.math.FunctionTables.SwapEntry;

/**
 * This class implements mutable {@code complex numbers} that store double precision {@Cartesian} coordinates.
 */
@Copyright(year = 2008, holders = "Sascha Baumeister")
public final class DoubleCartesianComplex extends Complex.AbstractDoublePrecision<DoubleCartesianComplex>
		implements Complex.MutableDoublePrecision<DoubleCartesianComplex> {
	static private final long serialVersionUID = 1;
	static private final ForkJoinPool FORK_JOIN_POOL = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
	static private final int PARALLEL_MAGNITUDE_THRESHOLD = 16;

	private double re;
	private double im;

	/**
	 * Construct a new instance with a real and an imaginary part of zero.
	 */
	public DoubleCartesianComplex() {
		this(0, 0);
	}

	/**
	 * Construct a new instance with the given real part and an imaginary part of zero.
	 * @param real the real part
	 */
	public DoubleCartesianComplex(final double re) {
		this(re, 0);
	}

	/**
	 * Construct a new instance with the given real and imaginary parts.
	 * @param re the real part
	 * @param im the imaginary part
	 */
	public DoubleCartesianComplex(final double re, final double im) {
		this.re = re;
		this.im = im;
	}

	/**
	 * Construct a new instance with the given complex value.
	 * @param value the complex value
	 * @throws NullPointerException if the given argument is {@code null}
	 */
	public DoubleCartesianComplex(final Complex.DoublePrecision<?> value) throws NullPointerException {
		this(value.re(), value.im());
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex setCartesian(final double re, final double im) {
		this.re = re;
		this.im = im;
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex setPolar(final double abs, final double arg) {
		this.re = abs * cos(arg);
		this.im = abs * sin(arg);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isReal() {
		return this.im == 0;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isImaginary() {
		return this.re == 0;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isZero() {
		return this.re == 0 & this.im == 0;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isInfinite() {
		return Double.isInfinite(this.re) | Double.isInfinite(this.im);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isNaN() {
		return Double.isNaN(this.re) | Double.isNaN(this.im);
	}

	/**
	 * {@inheritDoc}
	 */
	public double re() {
		return this.re;
	}

	/**
	 * {@inheritDoc}
	 */
	public double im() {
		return this.im;
	}

	/**
	 * {@inheritDoc}
	 */
	public double abs() {
		return DoubleMath.abs(this.re, this.im);
	}

	/**
	 * {@inheritDoc}
	 */
	public double arg() {
		return DoubleMath.arg(this.re, this.im);
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex conj() {
		this.im = -this.im;
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex neg() {
		return this.setCartesian(-this.re, -this.im);
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex imul() {
		return this.setCartesian(-this.im, +this.re);
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex idiv() {
		return this.setCartesian(+this.im, -this.re);
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex inv() {
		final double re = this.re, im = this.im, norm = 1 / (re * re + im * im);
		return this.setCartesian(+re * norm, -im * norm);
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex add(final double value) {
		this.re += value;
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex sub(final double value) {
		this.re -= value;
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex mul(final double value) {
		this.re *= value;
		this.im *= value;
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex div(final double value) {
		return this.mul(1 / value);
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex set(final DoubleCartesianComplex value) throws NullPointerException {
		return this.setCartesian(value.re, value.im);
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex add(final DoubleCartesianComplex value) throws NullPointerException {
		this.re += value.re;
		this.im += value.im;
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex sub(final DoubleCartesianComplex value) throws NullPointerException {
		this.re -= value.re;
		this.im -= value.im;
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex mul(final DoubleCartesianComplex value) throws NullPointerException {
		final double re1 = this.re, im1 = this.im, re2 = value.re, im2 = value.im;
		return this.setCartesian(re1 * re2 - im1 * im2, re1 * im2 + im1 * re2);
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex div(final DoubleCartesianComplex value) throws NullPointerException {
		final double re1 = this.re, im1 = this.im, re2 = value.re, im2 = value.im, norm = 1 / (re2 * re2 + im2 * im2);
		return this.setCartesian((re1 * re2 + im1 * im2) * norm, (im1 * re2 - re1 * im2) * norm);
	}

	/**
	 * {@inheritDoc}
	 */
	public void mux(final DoubleCartesianComplex value) throws NullPointerException {
		final double re1 = this.re, im1 = this.im, re2 = value.re, im2 = value.im;
		this.setCartesian(re1 + re2, im1 + im2);
		value.setCartesian(re1 - re2, im1 - im2);
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex sq() {
		final double re = this.re, im = this.im;
		return this.setCartesian(re * re - im * im, 2 * re * im);
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex sqrt() {
		final double re = this.re, im = this.im;
		if (re >= 0 & im == 0) {
			this.re = Math.sqrt(re);
			return this;
		}

		final double abs = this.abs(), signum = im >= 0 ? +1 : -1;
		return this.setCartesian(Math.sqrt((abs + re) / 2), Math.sqrt((abs - re) / 2) * signum);
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex cb() {
		final double re = this.re, im = this.im;
		return this.setCartesian(re * re * re - 3 * re * im * im, 3 * re * re * im - im * im * im);
	}

	/**
	 * {@inheritDoc}
	 */
	public DoubleCartesianComplex cbrt() {
		if (this.isReal()) {
			this.re = Math.cbrt(this.re);
			return this;
		}

		final double abs = Math.cbrt(this.abs());
		final double arg = this.arg() / 3;
		return this.setPolar(abs, arg);
	}

	/**
	 * {@inheritDoc}
	 * @see #fft(DoubleCartesianComplex[], int, int)
	 */
	@Override
	public void fft(final boolean inverse, final boolean separate, final DoubleCartesianComplex[] vector)
			throws NullPointerException, IllegalArgumentException {
		final int magnitude = floorLog2(vector.length);
		if (1 << magnitude != vector.length)
			throw new IllegalArgumentException();
		if (magnitude == 0)
			return;

		if (inverse) {
			for (int index = 0; index < vector.length; ++index)
				vector[index].conj();
		} else if (separate) {
			entangle(inverse, vector);
		}

		if (magnitude < PARALLEL_MAGNITUDE_THRESHOLD) {
			this.fft(magnitude, vector);
		} else {
			FORK_JOIN_POOL.invoke(new RecursiveFourierTransformer(magnitude, vector));
		}

		if (inverse) {
			final double norm = scalb(1d, -magnitude);
			for (int index = 0; index < vector.length; ++index)
				vector[index].mul(norm).conj();
			if (separate)
				entangle(inverse, vector);
		}
	}

	/**
	 * Performs an <i>in-place Fast Fourier Transform</i> of the given vector of {@code N} complex numbers. Note that an
	 * <i>inverse</i> transform can be performed in two ways:
	 * <ul>
	 * <li>by conjugating both the argument and result of this operation, and additionally norming the result by {@code 1/N}.
	 * </li>
	 * <li>by swapping the real and imaginary parts of both the argument and the result.</i>
	 * </ul>
	 * <i>Implementation notice</i>: This is an extremely optimized variant of the {@code Cooley-Tukey} algorithm, based on
	 * <i>perfect shuffle</i> and <i>sine/cosine</i> function tables for caching. Note that the JIT-compiler will inline the
	 * lookups within these tables (and any other static methods called). Logarithm-based iteration additionally eliminates
	 * expensive divisions, leaving only cheap addition, subtraction, multiplication and shift operations to be performed. The
	 * number of index variables is reduced to increase the chance that the JIT-compiler can keep them in registers (to avoid
	 * expensive cache or worse memory lookups), while derived indices are cheaply recalculated on demand taking advantage of
	 * modern super-scalar processor designs.
	 * @param magnitude the value <tt>log<sub>2</sub>(N)</tt>
	 * @param vector an array of <tt>N = 2<sup>magnitude</sup></tt> complex numbers
	 * @throws NullPointerException if the given vector is {@code null}
	 * @throws ArrayIndexOutOfBoundsException if the given vector's length is not a power of two
	 */
	private void fft(final int magnitude, final DoubleCartesianComplex[] vector) throws NullPointerException, ArrayIndexOutOfBoundsException {
		assert 1 << magnitude == vector.length;

		for (final SwapEntry entry : FunctionTables.getPerfectShuffleTable(magnitude)) {
			this.set(vector[entry.getLeft()]);
			vector[entry.getLeft()].set(vector[entry.getRight()]);
			vector[entry.getRight()].set(this);
		}
		// 3) TODO parallelise until M3 using Aparapi
		final FunctionTables.Trigonometric trigonometricTable = FunctionTables.getTrigonometricTable(magnitude);
		for (int depth = 0; depth < magnitude; ++depth) {
			// 1) TODO parallrelise until M1 using Aparapi
			for (int offset = 0; offset < 1 << depth; ++offset) {
				final int angleIndex = offset << magnitude - depth - 1;
				this.setCartesian(trigonometricTable.cos(angleIndex), trigonometricTable.sin(angleIndex));
				// 2)TODO parallelise until M2 using Aparapi
				for (int index = offset; index < 1 << magnitude; index += 2 << depth) {
					vector[index].mux(vector[index + (1 << depth)].mul(this));
				}
				// M1,M2
			}
		} // M3
	}

	/**
	 * If inverse is {@code true}, the given <i>natural spectrum</i> is detangled into a <i>two-channel spectrum</i> using the
	 * following operations for each corresponding complex spectrum entry (r,l,t &isin; &#x2102;): <br />
	 * <tt>forEach(l,r): r' = i&middot;(r<sup>*</sup>-l); l' = r<sup>*</sup>+l</tt>
	 * <p>
	 * If inverse is {@code false}, the given two-channel spectrum is entangled into a <i>natural spectrum</i> using the
	 * following operations (again r,l,t &isin; &#x2102;):<br />
	 * <tt>r,l,t &isin; &#x2102;: r' = &half;(l-i&middot;r)<sup>*</sup>; l' = &half;(l+i&middot;r)</tt>
	 * </p>
	 * <p>
	 * Additionally, the array elements at index <tt>1</tt> and <tt>&frac12;N</tt> are swapped, which implies that after
	 * detangling the
	 * <ul>
	 * <li>spectrum entries <tt>0</tt> and <tt>1</tt> both belong to the left channel, representing the signed amplitudes of the
	 * frequencies <tt>f<sub>0</sub></tt> and <tt>f<sub>Nyquist</sub></tt></li>
	 * <li>spectrum entries <tt>&frac12;N</tt> and <tt>&frac12;N+1</tt> both belong to the right channel, again representing the
	 * signed amplitudes of the frequencies <tt>f<sub>0</sub></tt> and <tt>f<sub>Nyquist</sub></tt></li>
	 * </ul>
	 * Detangling of a natural spectrum created by an iFFT operation is required in order to cleanly separate it's left and
	 * right halves; in it's detangled state, a spectrum's left half is solely influenced by the real parts of the values that
	 * went into the iFFT, and the right half is solely influenced by the corresponding imaginary parts.
	 * </p>
	 * @param inverse {@code true} for detangling, {@code false} for entangling
	 * @param spectrum the spectrum
	 * @throws NullPointerException if the given argument is {@code null}
	 * @throws IllegalArgumentException if the given spectrum's length is odd
	 */
	static private void entangle(final boolean inverse, final DoubleCartesianComplex[] spectrum)
			throws NullPointerException, IllegalArgumentException {
		if ((spectrum.length & 1) == 1)
			throw new IllegalArgumentException();

		final int halfLength = spectrum.length / 2;
		final DoubleCartesianComplex left = new DoubleCartesianComplex();
		spectrum[halfLength].set(spectrum[1]);
		spectrum[1].set(left);

		final DoubleCartesianComplex right = new DoubleCartesianComplex();
		if (inverse) {
			for (int asc = 1, desc = spectrum.length - 1; asc < desc; ++asc, --desc) {
				left.set(spectrum[asc]);
				right.set(spectrum[desc].conj());
				spectrum[desc].sub(left).imul();
				spectrum[asc].add(right);
			}
		} else {
			for (int asc = 1, desc = spectrum.length - 1; asc < desc; ++asc, --desc) {
				left.set(spectrum[asc]);
				right.set(spectrum[desc].imul());
				spectrum[desc].sub(left).conj().mul(-.5);
				spectrum[asc].add(right).mul(+.5);
			}
		}
	}

	/**
	 * Resultless {@link ForkJoinTask} modelling a parallel-recursive FFT task, based on an optimized algorithm of (Danielson &
	 * Lanczos}, 1942.
	 */
	static private class RecursiveFourierTransformer extends RecursiveAction {
		static private final long serialVersionUID = 1L;

		private final int magnitude;
		private final DoubleCartesianComplex[] vector;

		/**
		 * Creates a new instance.
		 * @param magnitude the value <tt>log<sub>2</sub>(N)</tt>
		 * @param vector an array of <tt>N = 2<sup>magnitude</sup></tt> complex numbers
		 */
		public RecursiveFourierTransformer(final int magnitude, final DoubleCartesianComplex[] vector) {
			super();
			this.vector = vector;
			this.magnitude = magnitude;
		}

		/**
		 * {@inheritDoc}
		 */
		protected void compute() {
			final DoubleCartesianComplex unit = new DoubleCartesianComplex();
			if (this.magnitude < PARALLEL_MAGNITUDE_THRESHOLD) {
				unit.fft(this.magnitude, this.vector);
				return;
			}

			// prepare stage: divide vector into even and odd indexed parts
			final int half = 1 << this.magnitude - 1;
			final DoubleCartesianComplex[] even = new DoubleCartesianComplex[half], odd = new DoubleCartesianComplex[half];
			for (int index = 0; index < half; ++index) {
				even[index] = this.vector[2 * index + 0];
				odd[index] = this.vector[2 * index + 1];
			}

			// divide stage: transform partial terms
			final RecursiveFourierTransformer evenTask = new RecursiveFourierTransformer(this.magnitude - 1, even);
			final RecursiveFourierTransformer oddTask = new RecursiveFourierTransformer(this.magnitude - 1, odd);
			oddTask.fork();
			evenTask.compute();
			oddTask.join();

			// conquer stage: recombine partial results
			final FunctionTables.Trigonometric trigonometricTable = FunctionTables.getTrigonometricTable(this.magnitude);
			for (int index = 0; index < half; ++index) {
				this.vector[index] = even[index];
				this.vector[index + half] = odd[index];
				unit.setCartesian(trigonometricTable.cos(index), trigonometricTable.sin(index));
				even[index].mux(odd[index].mul(unit));
			}
		}
	}
}