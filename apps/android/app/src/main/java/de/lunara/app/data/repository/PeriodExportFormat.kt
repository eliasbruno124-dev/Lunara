package de.lunara.app.data.repository

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import de.lunara.app.core.model.UserRole
import de.lunara.app.core.model.PeriodEndPolicy
import de.lunara.app.data.local.PeriodEntity
import de.lunara.app.data.preferences.AppMode
import de.lunara.app.data.preferences.AppPreferencesState
import java.nio.ByteBuffer
import java.io.StringReader
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PeriodFileFormat(val mimeType: String, val extension: String) {
    CSV("text/csv", "csv"), JSON("application/json", "json"),
}

data class PeriodTransferIdentity(val userId: String, val mode: AppMode, val baseUrl: String?, val generation: Long) {
    fun matches(state: AppPreferencesState, currentGeneration: Long): Boolean =
        state.session?.role == UserRole.TRACKER && state.session.userId == userId &&
            state.appMode == mode && state.baseUrl == baseUrl && generation == currentGeneration
}

enum class PeriodTransferCompletion { APPLY, ACCOUNT_CHANGED, IGNORE }
object PeriodTransferCompletionPolicy {
    fun evaluate(expected: PeriodTransferIdentity, active: PeriodTransferIdentity?, current: PeriodTransferIdentity?): PeriodTransferCompletion = when {
        active != expected || current == null || current.copy(generation = expected.generation) != expected -> PeriodTransferCompletion.IGNORE
        current.generation != expected.generation -> PeriodTransferCompletion.ACCOUNT_CHANGED
        else -> PeriodTransferCompletion.APPLY
    }
}

data class PeriodDateRange(val startDate: String, val endDate: String?)
data class PeriodImportSummary(val newCount: Int, val duplicateCount: Int, val conflictCount: Int) {
    val canImport: Boolean get() = newCount > 0 && conflictCount == 0
}
data class PeriodImportPlan(val additions: List<PeriodDateRange>, val summary: PeriodImportSummary)
enum class PeriodFileProblem { INVALID_FORMAT, INVALID_DATE, AMBIGUOUS_DATE, TOO_LARGE, TOO_MANY, CHANGED, ACCOUNT_CHANGED }
class PeriodFileException(val problem: PeriodFileProblem) : IllegalArgumentException(problem.name)

/** Date-only migration boundary: no identities, symptoms, forecast derivation, or overwrites. */
object PeriodExportFormat {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_PERIODS = 5_000
    private val startNames = setOf("startdate", "start", "periodstart", "periodstartdate", "beginn", "startdatum", "periodenbeginn", "von")
    private val endNames = setOf("enddate", "end", "periodend", "periodenddate", "ende", "enddatum", "periodenende", "bis")

    fun ownedRanges(periods: List<PeriodEntity>, identity: PeriodTransferIdentity): List<PeriodDateRange> = periods
        .filter { it.userId == identity.userId && it.deletedAt == null }
        .map { PeriodDateRange(it.startDate, it.endDate) }
        .sortedWith(compareBy<PeriodDateRange> { it.startDate }.thenBy { it.endDate.orEmpty() })

    fun encode(ranges: List<PeriodDateRange>, format: PeriodFileFormat, today: LocalDate = LocalDate.now()): ByteArray {
        checkCount(ranges.size)
        val sorted = ranges.map { validate(it, today) }.sortedWith(compareBy<PeriodDateRange> { it.startDate }.thenBy { it.endDate.orEmpty() })
        val text = when (format) {
            PeriodFileFormat.CSV -> "startDate,endDate\r\n" + sorted.joinToString("") { "${it.startDate},${it.endDate.orEmpty()}\r\n" }
            PeriodFileFormat.JSON -> GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(
                linkedMapOf("format" to "lunara-periods", "formatVersion" to 1, "periods" to sorted),
            ) + "\n"
        }
        return text.toByteArray(Charsets.UTF_8).also { if (it.size > MAX_BYTES) fail(PeriodFileProblem.TOO_LARGE) }
    }

    fun decode(bytes: ByteArray, today: LocalDate = LocalDate.now()): List<PeriodDateRange> {
        if (bytes.size > MAX_BYTES) fail(PeriodFileProblem.TOO_LARGE)
        val text = try {
            val encoding = when {
                bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
                bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
                else -> Charsets.UTF_8
            }
            encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF").trimStart(' ', '\r', '\n')
        } catch (_: Exception) { fail(PeriodFileProblem.INVALID_FORMAT) }
        if (text.isBlank()) fail(PeriodFileProblem.INVALID_FORMAT)
        val jsonCandidate = text.trimStart(' ', '\t', '\r', '\n')
        val raw = if (jsonCandidate.startsWith("{") || jsonCandidate.startsWith("[")) jsonRanges(jsonCandidate) else csvRanges(text)
        checkCount(raw.size)
        return raw.map { validate(it, today) }
    }

    fun plan(incoming: List<PeriodDateRange>, existing: List<PeriodDateRange>, today: LocalDate = LocalDate.now()): PeriodImportPlan {
        checkCount(incoming.size)
        val known = existing.map { validate(it, today) }.toSet()
        val seen = known.toMutableSet()
        var duplicates = 0
        val additions = incoming.map { validate(it, today) }.filter { if (!seen.add(it)) { duplicates++; false } else true }
            .sortedBy { it.startDate }
        data class Interval(val start: LocalDate, val end: LocalDate, val candidate: Int?)
        fun interval(range: PeriodDateRange, candidate: Int?): Interval {
            val start = LocalDate.parse(range.startDate)
            return Interval(start, PeriodEndPolicy.overlapEnd(start, range.endDate?.let(LocalDate::parse)), candidate)
        }
        val all = known.map { interval(it, null) } + additions.mapIndexed { index, range -> interval(range, index) }
        val ordered = all.sortedWith(compareBy<Interval> { it.start }.thenBy { it.end })
        val conflicts = mutableSetOf<Int>()
        var furthestEnd: LocalDate? = null
        // Prefix maxima and the next start detect any inclusive overlap in O(n log n).
        ordered.forEachIndexed { index, item ->
            if (item.candidate != null && (furthestEnd?.let { !it.isBefore(item.start) } == true ||
                    ordered.getOrNull(index + 1)?.let { !it.start.isAfter(item.end) } == true)) conflicts.add(item.candidate)
            if (furthestEnd == null || item.end.isAfter(furthestEnd)) furthestEnd = item.end
        }
        return PeriodImportPlan(additions, PeriodImportSummary(additions.size - conflicts.size, duplicates, conflicts.size))
    }

    private fun validate(range: PeriodDateRange, today: LocalDate): PeriodDateRange {
        val start = parseDate(range.startDate)
        val end = range.endDate?.takeIf { it.isNotBlank() }?.let(::parseDate)
        if (start.isAfter(today) || end?.isAfter(today) == true || end?.isBefore(start) == true) fail(PeriodFileProblem.INVALID_DATE)
        return PeriodDateRange(start.toString(), end?.toString())
    }

    private fun parseDate(raw: String): LocalDate {
        val value = raw.trim()
        return try {
            when {
                value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> LocalDate.parse(value)
                value.matches(Regex("\\d{4}/\\d{2}/\\d{2}")) -> LocalDate.parse(value.replace('/', '-'))
                value.matches(Regex("\\d{1,2}\\.\\d{1,2}\\.\\d{4}")) -> {
                    val p = value.split('.').map(String::toInt); LocalDate.of(p[2], p[1], p[0])
                }
                value.matches(Regex("\\d{1,2}/\\d{1,2}/\\d{4}")) -> {
                    val p = value.split('/').map(String::toInt)
                    if (p[0] <= 12 && p[1] <= 12 && p[0] != p[1]) fail(PeriodFileProblem.AMBIGUOUS_DATE)
                    if (p[0] > 12) LocalDate.of(p[2], p[1], p[0]) else LocalDate.of(p[2], p[0], p[1])
                }
                value.matches(Regex("\\d{4}-\\d{2}-\\d{2}T.+")) -> {
                    DateTimeFormatter.ISO_DATE_TIME.parse(value)
                    LocalDate.parse(value.substring(0, 10))
                }
                else -> fail(PeriodFileProblem.INVALID_DATE)
            }
        } catch (error: PeriodFileException) { throw error }
        catch (_: Exception) { fail(PeriodFileProblem.INVALID_DATE) }
    }

    private fun normalized(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[\\s_-]+"), "")

    private fun jsonRanges(text: String): List<PeriodDateRange> = try {
        // A tree parser replaces repeated property names. Reject them before any date is chosen.
        JsonReader(StringReader(text)).use { reader ->
            reader.strictness = Strictness.STRICT
            validateJsonKeys(reader, 0)
            if (reader.peek() != JsonToken.END_DOCUMENT) fail(PeriodFileProblem.INVALID_FORMAT)
        }
        val root = JsonReader(StringReader(text)).use { reader ->
            reader.strictness = Strictness.STRICT
            val value = JsonParser.parseReader(reader)
            if (reader.peek() != JsonToken.END_DOCUMENT) fail(PeriodFileProblem.INVALID_FORMAT)
            value
        }
        val array = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                if (obj.get("format")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString == "lunara-periods" &&
                    obj.get("formatVersion")?.toString() != "1") fail(PeriodFileProblem.INVALID_FORMAT)
                val entries = obj.get("periods") ?: obj.get("data")?.takeIf(JsonElement::isJsonObject)?.asJsonObject?.get("periods")
                entries?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: fail(PeriodFileProblem.INVALID_FORMAT)
            }
            else -> fail(PeriodFileProblem.INVALID_FORMAT)
        }
        checkCount(array.size())
        array.map { value ->
            val obj = value.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: fail(PeriodFileProblem.INVALID_FORMAT)
            PeriodDateRange(jsonDate(obj, startNames, required = true)!!, jsonDate(obj, endNames, required = false))
        }
    } catch (error: PeriodFileException) { throw error }
    catch (_: Exception) { fail(PeriodFileProblem.INVALID_FORMAT) }

    private fun validateJsonKeys(reader: JsonReader, depth: Int) {
        if (depth > 64) fail(PeriodFileProblem.INVALID_FORMAT)
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                val names = mutableSetOf<String>()
                while (reader.hasNext()) {
                    if (!names.add(reader.nextName())) fail(PeriodFileProblem.INVALID_FORMAT)
                    validateJsonKeys(reader, depth + 1)
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) validateJsonKeys(reader, depth + 1)
                reader.endArray()
            }
            JsonToken.STRING, JsonToken.NUMBER, JsonToken.BOOLEAN, JsonToken.NULL -> reader.skipValue()
            else -> fail(PeriodFileProblem.INVALID_FORMAT)
        }
    }

    private fun jsonDate(obj: JsonObject, names: Set<String>, required: Boolean): String? {
        val matches = obj.entrySet().filter { normalized(it.key) in names }
        if (matches.size > 1 || (required && matches.isEmpty())) fail(PeriodFileProblem.INVALID_FORMAT)
        val value = matches.singleOrNull()?.value
        if (value == null || value.isJsonNull) {
            if (required) fail(PeriodFileProblem.INVALID_DATE)
            return null
        }
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) fail(PeriodFileProblem.INVALID_DATE)
        return value.asString.trim().takeIf(String::isNotEmpty).also { if (required && it == null) fail(PeriodFileProblem.INVALID_DATE) }
    }

    private fun csvRanges(text: String): List<PeriodDateRange> {
        val header = text.lineSequence().first()
        val delimiterCounts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        var quotedHeader = false
        for (ch in header) {
            if (ch == '"') quotedHeader = !quotedHeader
            else if (!quotedHeader && ch in delimiterCounts) delimiterCounts[ch] = delimiterCounts.getValue(ch) + 1
        }
        val delimiter = delimiterCounts.maxBy { it.value }.key
        val rows = csvRows(text, delimiter).filterNot { row -> row.all(String::isBlank) }
        if (rows.isEmpty()) fail(PeriodFileProblem.INVALID_FORMAT)
        val names = rows.first().map(::normalized)
        val starts = names.indices.filter { names[it] in startNames }
        val ends = names.indices.filter { names[it] in endNames }
        if (starts.size != 1 || ends.size > 1) fail(PeriodFileProblem.INVALID_FORMAT)
        checkCount(rows.size - 1)
        return rows.drop(1).map { row ->
            if (row.size != names.size) fail(PeriodFileProblem.INVALID_FORMAT)
            PeriodDateRange(row[starts.single()].trim(), ends.singleOrNull()?.let { row[it].trim().ifBlank { null } })
        }
    }

    private fun csvRows(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var closedQuote = false
        var index = 0
        fun finishCell() { row.add(cell.toString()); cell.clear(); closedQuote = false }
        fun finishRow() { finishCell(); rows.add(row); row = mutableListOf(); if (rows.size > MAX_PERIODS + 1) fail(PeriodFileProblem.TOO_MANY) }
        while (index < text.length) {
            val ch = text[index++]
            if (quoted) {
                if (ch == '"') {
                    if (index < text.length && text[index] == '"') { cell.append('"'); index++ }
                    else { quoted = false; closedQuote = true }
                } else cell.append(ch)
            } else when {
                ch == delimiter -> finishCell()
                ch == '\n' || ch == '\r' -> { if (ch == '\r' && index < text.length && text[index] == '\n') index++; finishRow() }
                ch == '"' && cell.isEmpty() && !closedQuote -> quoted = true
                closedQuote && ch.isWhitespace() -> Unit
                closedQuote || ch == '"' -> fail(PeriodFileProblem.INVALID_FORMAT)
                else -> cell.append(ch)
            }
        }
        if (quoted) fail(PeriodFileProblem.INVALID_FORMAT)
        if (cell.isNotEmpty() || row.isNotEmpty() || closedQuote) finishRow()
        return rows
    }

    private fun checkCount(size: Int) { if (size > MAX_PERIODS) fail(PeriodFileProblem.TOO_MANY) }
    private fun fail(problem: PeriodFileProblem): Nothing = throw PeriodFileException(problem)
}
