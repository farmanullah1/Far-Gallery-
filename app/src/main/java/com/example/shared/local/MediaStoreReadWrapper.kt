package com.example.shared.local

import com.example.core.common.FarError
import com.example.core.common.FarResult

/**
 * Validation/read-wrapper concern for shared-library reads.
 *
 * Reads from a [MediaSourceReader] must keep using [FarResult]/[FarError]
 * and must not throw across the read boundary by default. This wrapper makes
 * that rule concrete for [MediaPage] reads.
 *
 * Rules:
 *  - If the read completes successfully, forward the page.
 *  - If the read fails with a known cause, map it through [mediaSourceError].
 *  - If the read fails for any other reason, return a generic [FarError.Unknown].
 */
suspend fun MediaSourceReader.readPageWrapped(request: MediaPageRequest): FarResult<MediaPage> =
    kotlin.runCatching { readPage(request) }
        .onSuccess { FarResult.Ok(it) }
        .onFailure { FarResult.Err(mediaSourceError(it)) }
        .getOrElse { FarResult.Err(FarError.Unknown()) }
