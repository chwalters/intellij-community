package com.intellij.configurationScript

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ReflectionUtil
import org.jetbrains.io.JsonUtil

@Suppress("JsonStandardCompliance")
private const val ref = "\$ref"

internal fun buildRunConfigurationTypeSchema(properties: StringBuilder, definitions: StringBuilder) {
  val configurationTypes = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
  for (configurationType in configurationTypes) {
    val propertyName = rcTypeIdToPropertyName(configurationType)
    val definitionId = "${propertyName}RC"
    val description = configurationType.configurationTypeDescription
    val descriptionField: CharSequence = when {
      StringUtil.equals(propertyName, description) -> ""
      else -> {
        val builder = StringBuilder()
        builder.append('"').append("description").append('"').append(':')
        JsonUtil.escape(description, builder)
        builder.append(',')
        builder
      }
    }
    properties.append("""
      "$propertyName": {
        "type": [
          "array",
          "object"
        ],
        $descriptionField
        "items": {
          "$ref": "#/definitions/$definitionId"
        },
        "$ref": "#/definitions/$definitionId"
      }
      """.trimIndent())
    if (configurationType !== configurationTypes.last()) {
      properties.append(',')
    }

    describeFactories(configurationType, definitions, definitionId)
  }
}

fun describeFactories(configurationType: ConfigurationType, definitions: StringBuilder, definitionId: String) {
  val factories = configurationType.configurationFactories
  if (factories.isEmpty()) {
    LOG.error("Configuration type \"${configurationType.displayName}\" is not valid: factory list is empty")
  }

  val rcProperties = StringBuilder()
  if (factories.size > 1) {
    for (factory in factories) {
      rcProperties.append("""
          "${idToPropertyName(factory.id, null, factory)}": {
            "type": "object"
          },
        """.trimIndent())
      // todo describe factory object with properties
    }
    return
  }

  val factory = factories[0]
  val optionsClass = factory.optionsClass
  if (optionsClass == null) {
    LOG.warn("Configuration factory \"${factory.name}\" is not described because options class not defined")

    definitions.append("""
      "$definitionId": {
        "additionalProperties": true
      },
      """.trimIndent())
    return
  }

  val state = ReflectionUtil.newInstance(optionsClass)
  val stateProperties = StringBuilder()
  state.buildJsonSchema(stateProperties)

  definitions.append("""
      "$definitionId": {
        "properties": {
          ${stateProperties}
        },
        "additionalProperties": false
      },
      """.trimIndent())
}

// returns null if id is not valid
internal fun rcTypeIdToPropertyName(configurationType: ConfigurationType): CharSequence? {
  return idToPropertyName(configurationType.configurationPropertyName, configurationType, null)
}

// returns null if id is not valid
private fun idToPropertyName(string: String, configurationType: ConfigurationType?, factory: ConfigurationFactory?): CharSequence? {
  val result = string
    .removeSuffix("Type")
    .removeSuffix("RunConfiguration")
    .removeSuffix("Configuration")
  if (result.isEmpty()) {
    if (factory == null) {
      LOG.error("Configuration type \"${configurationType!!.displayName}\" is not valid: id is empty")
    }
    else {
      LOG.error("Configuration factory \"${factory.name}\" is not valid: id is empty")
    }
    return null
  }

  var builder: StringBuilder? = null
  var i = 0
  var isAllUpperCased = true
  while (i < result.length) {
    val ch = result[i]
    @Suppress("IfThenToSafeAccess")
    if (ch == '.' || ch == ' ' || ch == '-' || ch == '_') {
      if (builder == null) {
        builder = StringBuilder()
        builder.append(result, 0, i)
      }

      i++
      if (i == result.length) {
        break
      }
      else {
        builder.append(result[i].toUpperCase())
        i++
        continue
      }
    }
    else if (ch == '#') {
      i++
      continue
    }
    else if (ch == '"' || ch == '\'' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\b' || ch == '/' || ch == '\\') {
      if (factory == null) {
        LOG.error("Configuration type \"${configurationType!!.id}\" is not valid: contains invalid symbol \"$ch\"")
      }
      else {
        // todo for factory historically was no id (opposite to type), so, in most cases name can contain invalid chars, may be should be ignored instead of error?
        LOG.error("Configuration factory \"${factory.name}\" is not valid: contains invalid symbol \"$ch\"")
      }
      return null
    }
    else if (i == 0) {
      if (ch.isUpperCase()) {
        if (builder == null) {
          builder = StringBuilder()
          builder.append(result, 0, i)
        }
        builder.append(ch.toLowerCase())
      }
      else {
        isAllUpperCased = false
      }
    }
    else if (builder != null) {
      builder.append(ch)
    }

    if (!ch.isUpperCase()) {
      isAllUpperCased = false
    }

    i++
  }

  if (isAllUpperCased) {
    if (builder == null) {
      return result.toLowerCase()
    }
    else {
      @Suppress("NAME_SHADOWING")
      for (i in 0 until builder.length) {
        builder.setCharAt(i, builder.get(i).toLowerCase())
      }
      return builder
    }
  }
  else {
    return builder ?: result
  }
}