// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0
package dev.pushport.sdk.internal.serialization

import dev.pushport.sdk.internal.model.TelemetryEvent
import org.json.JSONObject

internal object TelemetryJson {
    fun encode(event: TelemetryEvent): JSONObject =
        JSONObject()
            .put("id", event.id)
            .put("type", event.type)
            .put("occurredAt", event.occurredAt)
            .put("messageId", event.messageId)
            .put("reason", event.reason)
            .put("durationMillis", event.durationMillis)
            .put("sizeBytes", event.sizeBytes)
            .put("httpStatus", event.httpStatus)

    fun decode(json: JSONObject): TelemetryEvent =
        TelemetryEvent(
            json.getString("id"),
            json.getString("type"),
            json.getLong("occurredAt"),
            json.stringOrNull("messageId"),
            json.stringOrNull("reason"),
            json.optLong("durationMillis").takeIf { json.has("durationMillis") },
            json.optLong("sizeBytes").takeIf { json.has("sizeBytes") },
            json.optInt("httpStatus").takeIf { json.has("httpStatus") },
        )
}
