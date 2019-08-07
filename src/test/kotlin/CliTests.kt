import io.SignalOutputFormat
import io.mockk.*
import model.*
import org.junit.jupiter.api.Test
import runner.*
import step.*
import util.*
import java.nio.file.Files

class CliTests {

    @Test
    fun `Test CLI`() {
        val testDir = Files.createTempDirectory("zpeaks_test")
        val signalOut = testDir.resolve("testSignalOut.bed")
        val peaksOut = testDir.resolve("testPeaksOut.bed")

        val mockRun = spyk<(RunType, ZRunConfig, Boolean) -> Unit>()
        val args =
            """
            -bamIn=$MULTI_BAM_1_PATH -strand=both -pileUpAlgorithm=start -forwardShift=5 -reverseShift=10
            -bamIn=$MULTI_BAM_2_PATH -strand=plus -pileUpAlgorithm=length -forwardShift=-5 -reverseShift=15
            -chrFilter=$CHR_FILTER_PATH
            -signalOut=$signalOut -signalOutType=raw -signalOutFormat=wig -signalResolution=2
            -peaksOut=$peaksOut
            -smoothingFactor=60.0 -threshold=7.0
            -parallelism=4
            """.trimIndent().split("\\s+".toRegex())
        ZPeaksCommand(run = mockRun).main(args)

        val expectedRunConfig = ZRunConfig(
            pileUpRunner = BamPileUpRunner(listOf(
                PileUpInput(MULTI_BAM_1_PATH, PileUpOptions(Strand.BOTH, PileUpAlgorithm.START, 5, 10)),
                PileUpInput(MULTI_BAM_2_PATH, PileUpOptions(Strand.PLUS, PileUpAlgorithm.LENGTH, -5, 15))
            )),
            chrFilter = mapOf("chr22" to (30000000 until 30500000)),
            signalOut = SignalOutput(signalOut, SignalOutputType.RAW, SignalOutputFormat.WIG, signalResolution = 2),
            peaksOut = peaksOut,
            smoothing = 60.0,
            threshold = 7.0,
            parallelism = 4
        )
        verify { mockRun(any(), expectedRunConfig, false) }
    }

}