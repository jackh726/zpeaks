package step.subpeaks

import model.Region
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer
import org.apache.commons.math3.special.Erf.erf
import org.apache.commons.math3.util.FastMath.*
import org.apache.commons.math3.util.Precision

/**
 * Represents a Gaussian distribution with shape (adds skewness).
 * Comparison functions only compare amplitude (used for sorting
 * from most to least important when curve fitting).
 *
 * shape: shape parameter (alpha) capturing skewness.
 * amplitude: suqare root of the amplitude of the distribution.
 * mean: center point of the distribution.
 * stdev: standard deviation of the distribution.
 */
data class SkewGaussianParameters(
    override var amplitude: Double,
    override var mean: Double,
    override var stdDev: Double,
    var shape: Double
) : GaussianParameters

typealias SkewSubPeak = SubPeak<SkewGaussianParameters>

fun fitSkew(values: List<Double>, pileUpStart: Int) =
    fit(values, pileUpStart, ::initSkewParameters, ::optimizeSkew, ::skewParametersToRegion)

fun initSkewParameters(region: Region) = SkewGaussianParameters(
    amplitude = 0.0,
    mean = (region.start + region.end) / 2.0,
    stdDev = (region.end - region.start) / 2.0,
    shape = 0.0
)

val SQRT2 = sqrt(2.0)

/**
 * Calculate curve value at given x coordinate
 */
fun curveValue(x: Int, amplitude: Double, mean: Double, stdDev: Double, shape: Double): Double {
    return amplitude * amplitude / stdDev * exp( -(x - mean) * (x - mean) / stdDev * stdDev / 2) *
            (1.0 + erf(shape * (x - mean) / (stdDev * SQRT2)))
}

/**
 * Calculate curve for given parameters (in raw DoubleArray) form
 */
fun calculateCurve(parameters: DoubleArray, curveLength: Int): DoubleArray {
    val curve = DoubleArray(curveLength) { 0.0 }
    for (j in 0 until curveLength) {
        for (k in 0 until parameters.size step 4) {
            curve[j] += curveValue(j, parameters[k], parameters[k+1], parameters[k+2], parameters[k+3])
        }
    }
    return curve
}

val SQRT_PI_OVER_2 = sqrt(PI / 2.0)

fun calculateJacobian(parameters: DoubleArray, curveLength: Int): Array<DoubleArray> {
    val jacobian = Array(curveLength) { DoubleArray(parameters.size) }
    for (j in 0 until curveLength) {
        for (k in 0 until parameters.size step 4) {
            val a = parameters[k] // amplitude
            val m = parameters[k+1] // mean
            val u = parameters[k+2] // standard deviation
            val s = parameters[k+3] // shape
            val dm = j - m
            val exp = exp(-dm * dm / (u * u * 2.0))
            val erf = 1.0 + erf(s * dm / (u * SQRT2))
            val expp = dm * dm / (2 * u * u)
            val expx = exp(-s * s * expp - expp)
            jacobian[j][k] = 2 * a * exp * erf / u // partial with respect to a
            jacobian[j][k+1] = (a * a * dm * exp * erf / (u * u * u)) -
                    expx * a * a * s / (u * u * SQRT_PI_OVER_2) // partial with respect to m
            jacobian[j][k+2] = (a * a * dm * dm * exp * erf) / (u * u * u * u) -
                    expx * a * a * s * dm / (SQRT_PI_OVER_2 * u * u * u) -
                    a * a * exp * erf / (u * u) // partial with respect to u
            jacobian[j][k+3] = expx * a * a * dm / (SQRT_PI_OVER_2 * u * u) // partial with respect to s
        }
    }
    return jacobian
}

fun optimizeSkew(values: DoubleArray, gaussians: List<SkewGaussianParameters>, lambda: Double):
        OptimizeResults<SkewGaussianParameters> {
    val avg = values.average()

    val optimizer = LevenbergMarquardtOptimizer()
        .withInitialStepBoundFactor(lambda)
        .withCostRelativeTolerance(avg * 1e-4)
        .withParameterRelativeTolerance(avg * 1e-8)
        .withOrthoTolerance(avg * 1e-3)
        .withRankingThreshold(Precision.SAFE_MIN)

    val initialParameters = DoubleArray(gaussians.size * 4)
    for (j in 0 until gaussians.size) {
        initialParameters[j*4] = gaussians[j].amplitude
        initialParameters[j*4+1] = gaussians[j].mean
        initialParameters[j*4+2] = gaussians[j].stdDev
        initialParameters[j*4+2] = gaussians[j].shape
    }

    val problem = LeastSquaresBuilder()
        .maxEvaluations(500)
        .maxIterations(500)
        .model(
            { parameters -> calculateCurve(parameters, values.size) },
            { parameters -> calculateJacobian(parameters, values.size) }
        )
        .start(initialParameters)
        .target(values)
        .build()

    val optimum = optimizer.optimize(problem)
    val rawParameters = optimum.point
    val optimizedParameters = mutableListOf<SkewGaussianParameters>()
    for (j in 0 until optimum.point.dimension step 4) {
        optimizedParameters += SkewGaussianParameters(rawParameters.getEntry(j), rawParameters.getEntry(j+1),
            rawParameters.getEntry(j+2), rawParameters.getEntry(j+3))
    }
    return OptimizeResults(optimizedParameters, optimum.rms, optimum.iterations)
}

fun skewParametersToRegion(parameters: GaussianParameters, offset: Int): Region {
    val skewParameters = parameters as SkewGaussianParameters
    val mode = skewMode(skewParameters) + offset
    val start = mode - parameters.stdDev
    val stop = mode + parameters.stdDev
    return Region(start.toInt(), stop.toInt())
}

val SQRT_2_OVER_PI = sqrt(2.0 / PI)
val SQRT_2_OVER_PI_CUBED = pow(SQRT_2_OVER_PI, 3)
const val FOUR_MINUS_PI_OVER_2 = (4.0 - PI) / 2.0
const val NEG_2_PI = -2.0 * PI

/**
 * Calculate the mode for a skewed gaussian
 */
fun skewMode(gaussian: SkewGaussianParameters): Double {
    val delta = gaussian.shape / sqrt(1.0 + gaussian.shape * gaussian.shape)
    val uz = SQRT_2_OVER_PI * delta
    val oz = sqrt(1.0 - uz * uz)
    val skewness = FOUR_MINUS_PI_OVER_2 *
            ((SQRT_2_OVER_PI_CUBED * delta * delta * delta) / pow(1.0 - 2.0 * delta * delta / PI, 1.5))
    return (uz - skewness * oz / 2.0 - sgn(gaussian.shape) / 2.0 * exp(NEG_2_PI / abs(gaussian.shape))) *
            gaussian.stdDev + gaussian.mean
}

fun sgn(value: Double) = if (value > 0.0) 1 else if (value < 0.0) -1 else 0