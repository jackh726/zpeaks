import model.*
import mu.KotlinLogging
import org.junit.jupiter.api.*
import step.*
import step.subpeaks.*
import util.*
import java.nio.file.*

private val log = KotlinLogging.logger {}

@Disabled
class PerformanceTest {

    @Test
    fun `Run Skew Sub-Peaks on Large Peak`() {
        val pileUp = pileUp()
        val pdf = pdf(TEST_BAM_CHR, pileUp, 50.0, false)
        val peaks = callChromPeaks(pdf, 6.0)

        val maxPeak = peaks.maxBy { it.region.end - it.region.start }!!
        runChromSkewSubPeaks(TEST_BAM_CHR, listOf(maxPeak), pdf)
    }

    @Test
    fun `Run Skew Sub-Peaks on All Peaks`() {
        val testSamIn = TEST_BAM_PATH
        val subPeaksFilename = testSamIn.filenameWithoutExtension()
        val testDir = Files.createTempDirectory("zpeaks_test")
        val subPeaksOut = testDir.resolve(subPeaksFilename)

        run(samIn = testSamIn, signalOut = null, peaksOut = null, subPeaksOut = subPeaksOut,
            pileUpOptions = PileUpOptions(Strand.BOTH, PileUpAlgorithm.START), smoothing = 50.0,
            normalizePDF = false, threshold = 6.0)

        Files.copy(subPeaksOut, TEST_BAM_PATH.resolveSibling(subPeaksFilename), StandardCopyOption.REPLACE_EXISTING)
    }

    @Test
    fun `Run Skew Sub-Peaks many times for one peak and take the average score`() {
        val sampleRange =
            //10_000_000 until 15_000_000 // Small - Single curve
            //41_000_000 until 42_000_000 // Medium 1
            //44_000_000 until 46_000_000 // Medium 2
            46_075_000 until 46_100_000 // Large
            //46_050_000 until 46_075_000 // Largest

        val pileUp = pileUp()

        val errors = mutableSetOf<Double>()
        val times = mutableSetOf<Long>()
        repeat(25) {
            log.info { "Running $it" }
            val pdf = pdf(TEST_BAM_CHR, pileUp, 50.0, false, sampleRange)
            val peaks = callChromPeaks(pdf, 6.0)
            val maxPeak = peaks.maxBy { it.region.end - it.region.start }!!

            val peakRegion = maxPeak.region
            val peakValues = (peakRegion.start..peakRegion.end).map { pdf[it] }

            val startTime = System.currentTimeMillis()
            val fits = fitSkew(peakValues, peakRegion.start)
            errors += fits.map { it.error }.average()
            times += System.currentTimeMillis() - startTime
        }
        log.info { "Average error: ${errors.average()}" }
        log.info { "Average time: ${times.average()}" }
    }

}

private fun pileUp() = runPileUp(TEST_BAM_PATH, PileUpOptions(Strand.BOTH, PileUpAlgorithm.START))
    .getValue(TEST_BAM_CHR)