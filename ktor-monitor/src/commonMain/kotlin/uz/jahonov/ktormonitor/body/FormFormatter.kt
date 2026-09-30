package uz.jahonov.ktormonitor.body

import io.ktor.http.decodeURLQueryComponent

/**
 * An url-encoded form as decoded `key = value` lines. Nested keys (`user[name]`, `user.name`) are
 * grouped under a `user` line that folds, expanded.
 */
internal object FormFormatter {

    fun format(text: String): CodeDocument {
        val entries = ArrayList<FormEntry>()
        val groups = HashMap<String, FormEntry.Group>()
        for (pair in text.trim().split('&')) {
            if (pair.isEmpty()) continue
            val key = decode(pair.substringBefore('='))
            val value = if ('=' in pair) decode(pair.substringAfter('=')) else ""
            val cut = key.indexOfFirst { it == '[' || it == '.' }
            if (cut <= 0) {
                entries += FormEntry.Field(key, value)
                continue
            }
            val name = key.substring(0, cut)
            val group = groups.getOrPut(name) { FormEntry.Group(name).also { entries += it } }
            group.fields += FormEntry.Field(subKey(key.substring(cut)), value)
        }

        val out = CodeBuilder(CodeLanguage.FORM)
        for (entry in entries) {
            when (entry) {
                is FormEntry.Field -> field(out, entry, depth = 0)
                is FormEntry.Group -> {
                    val start = out.line
                    out.add(entry.name.visible(), TokenKind.KEY)
                    out.newLine()
                    entry.fields.forEach { field(out, it, depth = 1) }
                    out.fold(start, out.line - 1)
                }
            }
        }
        return out.build()
    }

    private fun field(out: CodeBuilder, field: FormEntry.Field, depth: Int) {
        out.indent(depth)
        out.add(field.key.visible(), TokenKind.KEY)
        out.add(" = ", TokenKind.PUNCTUATION)
        out.add(field.value.visible(), TokenKind.STRING)
        out.newLine()
    }

    /** `[name]` → `name`, `[a][b]` → `a[b]`, `.a.b` → `a.b`, `[]` → `[]`. */
    private fun subKey(rest: String): String {
        val key = when {
            rest.startsWith('.') -> rest.substring(1)
            rest.startsWith('[') && ']' in rest -> rest.substring(1, rest.indexOf(']')) + rest.substringAfter(']')
            else -> rest
        }
        return key.ifEmpty { "[]" }
    }

    private fun decode(value: String) =
        runCatching { value.decodeURLQueryComponent(plusIsSpace = true) }.getOrDefault(value)

    private fun String.visible() = replace("\r", "\\r").replace("\n", "\\n")
}

private sealed interface FormEntry {
    class Field(val key: String, val value: String) : FormEntry
    class Group(val name: String) : FormEntry {
        val fields = ArrayList<Field>()
    }
}
