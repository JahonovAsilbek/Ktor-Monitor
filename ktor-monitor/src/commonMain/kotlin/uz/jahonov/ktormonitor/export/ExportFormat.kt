package uz.jahonov.ktormonitor.export

import uz.jahonov.ktormonitor.InternalKtorMonitorApi

/** What calls can be shared as, with the file extension and MIME type of the shared file. */
@InternalKtorMonitorApi
public enum class ExportFormat(public val label: String, public val extension: String, public val mimeType: String) {
    JSON("JSON", "json", "application/json"),
    TEXT("Text", "txt", "text/plain"),
    MARKDOWN("Markdown", "md", "text/markdown"),
    CURL("cURL", "sh", "text/x-shellscript"),
    WGET("wget", "sh", "text/x-shellscript"),
    URLS("URL list", "txt", "text/plain"),
    HAR("HAR", "har", "application/json"),
}
