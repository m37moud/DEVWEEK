package dev.daymark.reporting

expect fun observeReportConnectivity(onAvailable: () -> Unit): () -> Unit
