package com.evi.service

import com.evi.model.AuthorityMode
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

data class AuthorityPromptContract(
    val systemInstruction: String,
    val userTemplate: String,
    val responseSchema: JsonNode,
)

class AuthorityPromptRepository(
    private val objectMapper: ObjectMapper,
    private val classLoader: ClassLoader = AuthorityPromptRepository::class.java.classLoader,
) {
    fun load(mode: AuthorityMode): AuthorityPromptContract {
        val resourcePath = "evi-prompts/${mode.promptFileName}"
        val markdown = classLoader.getResourceAsStream(resourcePath)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Authority prompt resource not found: $resourcePath")

        val systemInstruction = extractSection(markdown, "System instruction")
        val userTemplate = extractSection(markdown, "User content template")
        val schemaJson = Regex("```json\\s*([\\s\\S]*?)\\s*```")
            .findAll(markdown)
            .lastOrNull()
            ?.groupValues
            ?.get(1)
            ?: error("Authority prompt response schema not found: $resourcePath")

        return AuthorityPromptContract(
            systemInstruction = systemInstruction,
            userTemplate = userTemplate,
            responseSchema = normalizeSchemaTypes(objectMapper.readTree(schemaJson)),
        )
    }

    private fun extractSection(markdown: String, heading: String): String {
        val pattern = Regex("## ${Regex.escape(heading)}\\s*```text\\s*([\\s\\S]*?)\\s*```")
        return pattern.find(markdown)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: error("Authority prompt section not found: $heading")
    }

    private fun normalizeSchemaTypes(node: JsonNode): JsonNode {
        if (node.isObject) {
            val objectNode = node.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
            objectNode.fields().forEach { (fieldName, fieldValue) ->
                if (fieldName == "type" && fieldValue.isTextual) {
                    objectNode.put(fieldName, fieldValue.asText().lowercase())
                } else {
                    objectNode.replace(fieldName, normalizeSchemaTypes(fieldValue))
                }
            }
            return objectNode
        }
        if (node.isArray) {
            val arrayNode = node.deepCopy<com.fasterxml.jackson.databind.node.ArrayNode>()
            for (index in 0 until arrayNode.size()) {
                arrayNode.set(index, normalizeSchemaTypes(arrayNode[index]))
            }
            return arrayNode
        }
        return node
    }
}
