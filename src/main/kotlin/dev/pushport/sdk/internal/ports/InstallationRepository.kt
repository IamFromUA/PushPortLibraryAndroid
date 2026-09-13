// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.ports

import dev.pushport.sdk.internal.model.InstallationState

internal interface InstallationRepository {
    fun read(): InstallationState

    /** Serializes the read/modify/write operation and commits it atomically. */
    fun update(transform: (InstallationState) -> InstallationState): InstallationState
}
