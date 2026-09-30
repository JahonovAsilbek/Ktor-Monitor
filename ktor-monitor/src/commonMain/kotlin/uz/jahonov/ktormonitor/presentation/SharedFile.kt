package uz.jahonov.ktormonitor.presentation

/** Text for the platform share sheet: written to a temporary file named [name] and shared from there. */
public data class SharedFile(val name: String, val mimeType: String, val content: String)
