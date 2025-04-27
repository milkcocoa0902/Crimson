package com.milkcocoa.info.crimson.sample.model

import com.milkcocoa.info.crimson.core.CrimsonData
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(val text: String): CrimsonData

@Serializable
data class ChatResponse(val text: String): CrimsonData