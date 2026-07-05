package com.xavadigital.mileagetracker.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal Google Sheets v4 REST client — append/update/read rows on the first
 * sheet of the spreadsheet. Kept to plain HttpURLConnection + org.json so we
 * don't drag in the heavyweight Google API client libraries.
 */
class SheetsApi(private val accessToken: String) {

    data class SheetInfo(val spreadsheetId: String, val url: String)

    /** Raw data rows (sheet rows 2..n, header excluded), padded to [HEADER] size. */
    data class Entries(val rows: List<List<String>>) {
        /** entryId (last column) → absolute 1-based sheet row number. */
        val idToRow: Map<String, Int> = rows.mapIndexedNotNull { index, row ->
            row.getOrNull(COL_ENTRY_ID)?.takeIf { it.isNotBlank() }?.let { it to index + 2 }
        }.toMap()
    }

    fun createSpreadsheet(title: String): SheetInfo {
        val body = JSONObject().put("properties", JSONObject().put("title", title))
        val response = execute("POST", "$BASE/spreadsheets", body)
        return SheetInfo(
            spreadsheetId = response.getString("spreadsheetId"),
            url = response.optString(
                "spreadsheetUrl",
                "https://docs.google.com/spreadsheets/d/${response.getString("spreadsheetId")}"
            )
        )
    }

    fun ensureHeader(spreadsheetId: String) {
        val existing = execute("GET", valuesUrl(spreadsheetId, "A1:L1"))
        if (existing.optJSONArray("values") == null) {
            writeRange(spreadsheetId, "A1:L1", listOf(HEADER))
        }
    }

    fun readEntries(spreadsheetId: String): Entries {
        val response = execute("GET", valuesUrl(spreadsheetId, "A2:L"))
        val values = response.optJSONArray("values") ?: return Entries(emptyList())
        val rows = (0 until values.length()).map { r ->
            val row = values.getJSONArray(r)
            (0 until HEADER.size).map { c -> if (c < row.length()) row.optString(c) else "" }
        }
        return Entries(rows)
    }

    fun appendRow(spreadsheetId: String, row: List<String>) {
        val body = JSONObject().put("values", JSONArray().put(JSONArray(row)))
        execute(
            "POST",
            valuesUrl(spreadsheetId, "A1") +
                ":append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS",
            body
        )
    }

    fun updateRow(spreadsheetId: String, sheetRow: Int, row: List<String>) {
        writeRange(spreadsheetId, "A$sheetRow:L$sheetRow", listOf(row))
    }

    private fun writeRange(spreadsheetId: String, range: String, rows: List<List<String>>) {
        val values = JSONArray()
        rows.forEach { values.put(JSONArray(it)) }
        val body = JSONObject().put("values", values)
        execute("PUT", valuesUrl(spreadsheetId, range) + "?valueInputOption=USER_ENTERED", body)
    }

    private fun valuesUrl(spreadsheetId: String, range: String): String =
        "$BASE/spreadsheets/$spreadsheetId/values/$range"

    private fun execute(method: String, url: String, body: JSONObject? = null): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("Sheets API HTTP $code: ${text.take(300)}")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://sheets.googleapis.com/v4"

        val HEADER = listOf(
            "Date", "Start Time", "End Time", "Start Location", "End Location",
            "Distance (km)", "Driver", "Type", "Business", "Purpose", "Source", "Entry Id"
        )

        const val COL_DATE = 0
        const val COL_START_TIME = 1
        const val COL_END_TIME = 2
        const val COL_DISTANCE = 5
        const val COL_DRIVER = 6
        const val COL_ENTRY_ID = 11
    }
}
