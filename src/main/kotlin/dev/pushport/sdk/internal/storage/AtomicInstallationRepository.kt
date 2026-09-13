// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.storage

import android.content.Context
import android.util.AtomicFile
import dev.pushport.sdk.internal.model.InstallationState
import dev.pushport.sdk.internal.ports.InstallationRepository
import java.io.File

/** File name and noBackup directory remain stable for SDK upgrades from 0.2.0 onward. */
internal class AtomicInstallationRepository(
    context: Context,
    private val codec: InstallationStateJson = InstallationStateJson(),
) : InstallationRepository {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    override fun read(): InstallationState =
        synchronized(lock) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
                InstallationState()
            } else {
                file.openRead().bufferedReader().use { codec.decode(it.readText()) }
            }
        }

    override fun update(transform: (InstallationState) -> InstallationState): InstallationState =
        synchronized(lock) {
            val previous = read()
            val updated = transform(previous)
            if (updated == previous) return@synchronized previous
            val output = file.startWrite()
            try {
                output.write(codec.encode(updated).toByteArray(Charsets.UTF_8))
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
            updated
        }

    private companion object {
        const val FILE_NAME = "pushport-installation.json"
        val lock = Any()
    }
}
