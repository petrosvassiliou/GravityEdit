package com.gravityedit.app.domain.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class GitRepoStatusSummary(
    val repoRootDisplay: String,
    val branchName: String,
    val modifiedFileCount: Int = 0,
    val isFileTracked: Boolean = false
)

@Immutable
data class ParameterFieldSpec(
    val name: String,
    val typeName: String = "String",
    val isMandatory: Boolean = false,
    val isSwitch: Boolean = false,
    val validateSetOptions: List<String> = emptyList(),
    val defaultValue: String? = null,
    val helpMessage: String? = null
)

@Immutable
data class ParameterFormSpec(
    val commandName: String,
    val isFunction: Boolean,
    val synopsis: String? = null,
    val parameters: List<ParameterFieldSpec> = emptyList()
)

@Immutable
data class CmdletHelpParameter(
    val name: String,
    val typeName: String,
    val isMandatory: Boolean = false,
    val description: String
)

@Immutable
data class CmdletHelpTopic(
    val name: String,
    val moduleName: String,
    val synopsis: String,
    val syntax: List<String>,
    val parameters: List<CmdletHelpParameter> = emptyList(),
    val examples: List<String> = emptyList(),
    val sourceBadge: String = "Built-in Reference"
)

@Immutable
data class PesterTestCaseSpec(
    val id: String,
    val describeBlock: String,
    val contextBlock: String? = null,
    val itName: String,
    val lineNumber: Int,
    val bodyCode: String
)

@Immutable
data class PesterTestCaseResult(
    val id: String,
    val describeBlock: String,
    val contextBlock: String? = null,
    val itName: String,
    val lineNumber: Int,
    val passed: Boolean,
    val durationMs: Long,
    val failureMessage: String? = null
)

@Immutable
data class PesterSuiteResult(
    val engineDescription: String,
    val passedCount: Int,
    val failedCount: Int,
    val totalDurationMs: Long,
    val results: List<PesterTestCaseResult>,
    val rawOutput: String
) {
    val allPassed: Boolean get() = failedCount == 0 && results.isNotEmpty()
}

@Immutable
data class DataGridTable(
    val headers: List<String>,
    val rows: List<List<String>>,
    val delimiter: Char = ',',
    val isJsonArray: Boolean = false
)

@Immutable
data class SftpConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val initialPath: String = "/"
)

@Immutable
data class SftpRemoteEntry(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val permissions: String = ""
)
