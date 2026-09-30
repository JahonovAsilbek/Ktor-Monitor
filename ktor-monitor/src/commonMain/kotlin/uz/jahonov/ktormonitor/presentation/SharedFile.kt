package uz.jahonov.ktormonitor.presentation

import uz.jahonov.ktormonitor.InternalKtorMonitorApi

/** Text for the platform share sheet: written to a temporary file named [name] and shared from there. */
@InternalKtorMonitorApi
public data class SharedFile(val name: String, val mimeType: String, val content: String)
