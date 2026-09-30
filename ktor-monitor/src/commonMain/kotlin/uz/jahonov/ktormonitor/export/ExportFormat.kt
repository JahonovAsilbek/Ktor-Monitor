package uz.jahonov.ktormonitor.export

/** What calls can be shared as, with the file extension and MIME type of the shared file. */
public enum class ExportFormat(public val extension: String, public val mimeType: String) {
    JSON("json", "application/json"),
    TEXT("txt", "text/plain"),
    MARKDOWN("md", "text/markdown"),
    CURL("sh", "text/x-shellscript"),
    WGET("sh", "text/x-shellscript"),
    URLS("txt", "text/plain"),
    HAR("har", "application/json"),
}
