package com.gravityedit.app.domain.engine

import com.gravityedit.app.domain.model.DataGridTable
import com.gravityedit.app.domain.model.EditorDocument
import com.gravityedit.app.domain.model.LineEnding
import com.gravityedit.app.domain.model.SyntaxLanguage
import org.json.JSONArray
import org.json.JSONObject

object StructuredDataGridEngine {

    fun canInspectAsGrid(document: EditorDocument): Boolean {
        val lower = document.title.lowercase()
        if (lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".psv")) {
            return true
        }
        if (document.language == SyntaxLanguage.JSON || lower.endsWith(".json")) {
            return document.content.trim().startsWith("[")
        }
        return false
    }

    fun detectDelimiter(text: String, preferredFileName: String = ""): Char {
        val lower = preferredFileName.lowercase()
        if (lower.endsWith(".tsv")) return '\t'
        if (lower.endsWith(".psv")) return '|'

        val sample = LineEnding.normalizeToLf(text)
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(10)
        if (sample.isEmpty()) return ','

        val candidates = listOf(',', '\t', ';', '|')
        var best = ','
        var bestScore = -1
        for (cand in candidates) {
            val counts = sample.map { countUnquoted(it, cand) }
            val first = counts.firstOrNull() ?: 0
            if (first <= 0) continue
            val consistent = counts.count { it == first }
            val score = consistent * 100 + first
            if (score > bestScore) {
                bestScore = score
                best = cand
            }
        }
        return best
    }

    fun parseTable(
        content: String,
        language: SyntaxLanguage,
        fileName: String = ""
    ): DataGridTable? {
        val trimmed = content.trim()
        val lower = fileName.lowercase()
        if (language == SyntaxLanguage.JSON || lower.endsWith(".json") || (trimmed.startsWith("[") && trimmed.endsWith("]") && !lower.endsWith(".csv"))) {
            val jsonTbl = parseJsonObjectArray(trimmed)
            if (jsonTbl != null) return jsonTbl
            if (language == SyntaxLanguage.JSON || lower.endsWith(".json")) return null
        }
        val delim = detectDelimiter(content, fileName)
        return parseDelimitedTable(content, delim)
    }

    fun serializeTable(table: DataGridTable): String {
        return if (table.isJsonArray) serializeJsonArray(table) else serializeDelimited(table)
    }

    fun sortTable(table: DataGridTable, columnIndex: Int, ascending: Boolean): DataGridTable {
        if (columnIndex !in table.headers.indices) return table
        val sorted = table.rows.sortedWith { aRow, bRow ->
            val a = aRow.getOrElse(columnIndex) { "" }
            val b = bRow.getOrElse(columnIndex) { "" }
            val numA = a.trim().toDoubleOrNull()
            val numB = b.trim().toDoubleOrNull()
            val cmp = if (numA != null && numB != null) {
                numA.compareTo(numB)
            } else {
                a.compareTo(b, ignoreCase = true)
            }
            if (ascending) cmp else -cmp
        }
        return table.copy(rows = sorted)
    }

    fun filterRows(table: DataGridTable, query: String): List<Pair<Int, List<String>>> {
        val q = query.trim()
        if (q.isEmpty()) return table.rows.mapIndexed { idx, row -> idx to row }
        return table.rows.mapIndexedNotNull { idx, row ->
            if (row.any { it.contains(q, ignoreCase = true) }) idx to row else null
        }
    }

    fun updateCell(table: DataGridTable, rowIndex: Int, columnIndex: Int, newValue: String): DataGridTable {
        if (rowIndex !in table.rows.indices || columnIndex !in table.headers.indices) return table
        val mutableRows = table.rows.toMutableList()
        val row = mutableRows[rowIndex].toMutableList()
        while (row.size < table.headers.size) row.add("")
        row[columnIndex] = newValue
        mutableRows[rowIndex] = row
        return table.copy(rows = mutableRows)
    }

    fun appendEmptyRow(table: DataGridTable): DataGridTable {
        val headers = table.headers.ifEmpty { listOf("Column1", "Column2") }
        val updatedRows = table.rows + listOf(List(headers.size) { "" })
        return table.copy(headers = headers, rows = updatedRows)
    }

    fun deleteRow(table: DataGridTable, rowIndex: Int): DataGridTable {
        if (rowIndex !in table.rows.indices) return table
        val mutable = table.rows.toMutableList()
        mutable.removeAt(rowIndex)
        return table.copy(rows = mutable)
    }

    // MARK: - RFC-4180 CSV / DSV

    private fun parseDelimitedTable(rawText: String, delimiter: Char): DataGridTable {
        val normalized = LineEnding.normalizeToLf(rawText)
        if (normalized.isBlank()) {
            return DataGridTable(headers = listOf("Column1", "Column2"), rows = emptyList(), delimiter = delimiter, isJsonArray = false)
        }

        val records = ArrayList<List<String>>()
        var curRow = ArrayList<String>()
        val curCell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < normalized.length) {
            val ch = normalized[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < normalized.length && normalized[i + 1] == '"') {
                        curCell.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    curCell.append(ch)
                }
            } else {
                when {
                    ch == '"' && curCell.isEmpty() -> inQuotes = true
                    ch == delimiter -> {
                        curRow.add(curCell.toString())
                        curCell.clear()
                    }
                    ch == '\n' -> {
                        curRow.add(curCell.toString())
                        records.add(curRow)
                        curRow = ArrayList()
                        curCell.clear()
                    }
                    else -> curCell.append(ch)
                }
            }
            i++
        }
        if (curCell.isNotEmpty() || curRow.isNotEmpty()) {
            curRow.add(curCell.toString())
            records.add(curRow)
        }
        if (records.size > 1 && records.last().size == 1 && records.last()[0].isEmpty()) {
            records.removeAt(records.lastIndex)
        }

        val firstRow = records.firstOrNull()
            ?: return DataGridTable(headers = listOf("Column1"), rows = emptyList(), delimiter = delimiter)
        val maxCols = maxOf(firstRow.size, records.maxOfOrNull { it.size } ?: firstRow.size)
        val headers = (0 until maxCols).map { col ->
            firstRow.getOrNull(col)?.takeIf { it.isNotBlank() } ?: "Column${col + 1}"
        }
        val body = records.drop(1).map { row ->
            when {
                row.size == maxCols -> row
                row.size < maxCols -> row + List(maxCols - row.size) { "" }
                else -> row.take(maxCols)
            }
        }
        return DataGridTable(headers = headers, rows = body, delimiter = delimiter, isJsonArray = false)
    }

    private fun serializeDelimited(table: DataGridTable): String {
        val delimStr = table.delimiter.toString()
        fun encodeCell(v: String): String {
            val mustQuote = v.contains(table.delimiter) || v.contains('"') || v.contains('\n') || v.contains('\r')
            return if (mustQuote) "\"${v.replace("\"", "\"\"")}\"" else v
        }
        val lines = ArrayList<String>(table.rows.size + 1)
        lines.add(table.headers.joinToString(delimStr) { encodeCell(it) })
        for (row in table.rows) {
            val padded = (0 until table.headers.size).map { row.getOrElse(it) { "" } }
            lines.add(padded.joinToString(delimStr) { encodeCell(it) })
        }
        return lines.joinToString("\n")
    }

    // MARK: - JSON Object Array

    private fun parseJsonObjectArray(text: String): DataGridTable? {
        return runCatching {
            val arr = JSONArray(text)
            if (arr.length() == 0) {
                return DataGridTable(headers = listOf("id", "value"), rows = emptyList(), isJsonArray = true)
            }
            val keySet = LinkedHashSet<String>()
            val objects = ArrayList<JSONObject>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: return null
                objects.add(obj)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    keySet.add(keys.next())
                }
            }
            val headers = keySet.toList()
            val rows = objects.map { obj ->
                headers.map { key ->
                    if (!obj.has(key) || obj.isNull(key)) "" else obj.opt(key)?.toString().orEmpty()
                }
            }
            DataGridTable(headers = headers, rows = rows, isJsonArray = true)
        }.getOrNull()
    }

    private fun serializeJsonArray(table: DataGridTable): String {
        val arr = JSONArray()
        for (row in table.rows) {
            val obj = JSONObject()
            for ((colIdx, key) in table.headers.withIndex()) {
                val raw = row.getOrElse(colIdx) { "" }
                val trimmed = raw.trim()
                val typed: Any = when {
                    trimmed == "true" -> true
                    trimmed == "false" -> false
                    trimmed.toLongOrNull() != null && !(trimmed.length > 1 && trimmed.startsWith("0")) -> trimmed.toLong()
                    trimmed.contains('.') && trimmed.toDoubleOrNull() != null && !(trimmed.startsWith("0") && !trimmed.startsWith("0.")) -> trimmed.toDouble()
                    else -> raw
                }
                obj.put(key, typed)
            }
            arr.put(obj)
        }
        return arr.toString(2)
    }

    private fun countUnquoted(line: String, delimiter: Char): Int {
        var inQuotes = false
        var count = 0
        for (ch in line) {
            if (ch == '"') inQuotes = !inQuotes
            else if (ch == delimiter && !inQuotes) count++
        }
        return count
    }
}
