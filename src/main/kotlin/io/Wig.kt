package io

import model.SignalData
import mu.KotlinLogging
import org.apache.commons.math3.util.FastMath.*
import util.logProgress
import util.pow
import java.nio.file.*
import java.util.zip.GZIPOutputStream


private val log = KotlinLogging.logger {}

data class SignalSection(val chr: String, val start: Int, val span: Int, val value: Number) {
    val end: Int get() = start + span
}

enum class SignalOutputFormat { WIG, BED_GRAPH }

fun createSignalFile(signalOut: Path, format: SignalOutputFormat, chr: String, data: SignalData, signalResolution: Int = 1) {
    when (format) {
        SignalOutputFormat.WIG -> {
            writeWig(signalOut, chr, data, signalResolution)
        }
        SignalOutputFormat.BED_GRAPH -> {
            writeBedGraph(signalOut, chr, data, signalResolution)
        }
    }
}

fun writeWig(path: Path, chr: String, data: SignalData, signalResolution: Int = 1) {
    log.info { "Writing signal data for $chr to wig file $path..." }
    Files.newBufferedWriter(path).use { writer ->
        writer.write("track type=wiggle_0\n")
        var lastSpan: Int? = null
        iterateSignalSections(chr, data, signalResolution) { section ->
            if (lastSpan != section.span) {
                writer.write("variableStep chrom=${section.chr} span=${section.span}\n")
                lastSpan = section.span
            }
            writer.write("${section.start + 1} ${section.value}\n")
        }
    }
    log.info { "Wig file write complete!" }
}

fun writeBedGraph(path: Path, chr: String, data: SignalData, signalResolution: Int = 1) {
    log.info { "Writing signal data for $chr to bed-graph file $path..." }
    Files.newBufferedWriter(path).use { writer ->
        writer.write("track type=bedGraph\n")
        iterateSignalSections(chr, data, signalResolution) { section ->
            writer.write("${section.chr}\t${section.start}\t${section.end}\t${section.value}\n")
        }
    }
    log.info { "Bed-graph file write complete!" }
}

fun iterateSignalSections(chr: String, data: SignalData, signalResolution: Int,
        processSection: (section: SignalSection) -> Unit) {
    val roundFactor = (10.0).pow(signalResolution)
    var currentStart: Int? = null
    var currentValue: Number? = null
    for (index in data.range) {
        val value = floor(data[index].toDouble() * roundFactor) / roundFactor
        if (currentValue == value) continue
        if (currentValue != null) {
            processSection(SignalSection(chr, currentStart!!, index - currentStart, currentValue))
        }
        if (value > 0.0) {
            currentStart = index
            currentValue = value
        } else {
            currentStart = null
            currentValue = null
        }
    }
}
