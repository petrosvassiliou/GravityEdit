package com.gravityedit.app.domain.engine

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.gravityedit.app.domain.model.GitRepoStatusSummary
import java.io.File

object GitWorkbenchEngine {

    /**
     * Parses a `.git/HEAD` file payload (`ref: refs/heads/main` or a raw 40-char commit SHA)
     * into a concise branch badge.
     */
    fun parseGitHeadContent(rawHeadText: String): String {
        val trimmed = rawHeadText.trim()
        if (trimmed.isEmpty()) return "HEAD"
        if (trimmed.startsWith("ref:", ignoreCase = true)) {
            val refTarget = trimmed.substringAfter(':').trim()
            return refTarget.removePrefix("refs/heads/").removePrefix("refs/remotes/").ifBlank { "HEAD" }
        }
        return if (trimmed.length >= 7) "detached@${trimmed.take(7)}" else trimmed
    }

    /**
     * Inspects a local filesystem directory (e.g. Googlebook OS / unit test workspace) for `.git/HEAD`
     * and optional `git` CLI status.
     */
    fun detectLocalDirectoryGitStatus(directoryOrFile: File): GitRepoStatusSummary? {
        var cur: File? = if (directoryOrFile.isDirectory) directoryOrFile else directoryOrFile.parentFile
        while (cur != null) {
            val gitDir = File(cur, ".git")
            val headFile = File(gitDir, "HEAD")
            if (headFile.isFile) {
                val headRaw = runCatching { headFile.readText(Charsets.UTF_8) }.getOrDefault("")
                val branch = parseGitHeadContent(headRaw)
                val cliCounts = queryOptionalGitCliCounts(repoRoot = cur, targetFile = directoryOrFile.takeIf { it.isFile })
                return GitRepoStatusSummary(
                    repoRootDisplay = cur.name.ifBlank { cur.absolutePath },
                    branchName = branch,
                    modifiedFileCount = cliCounts.first,
                    isFileTracked = cliCounts.second
                )
            }
            cur = cur.parentFile
        }
        return null
    }

    /**
     * Inspects an Android SAF mounted folder tree (`content://...`) for `.git/HEAD`.
     */
    fun detectSafFolderGitStatus(
        context: Context,
        mountedTreeUri: Uri?,
        dirtyOpenDocCount: Int = 0
    ): GitRepoStatusSummary? {
        if (mountedTreeUri == null) return null
        return runCatching {
            val rootDoc = DocumentFile.fromTreeUri(context, mountedTreeUri) ?: return null
            val gitDir = rootDoc.findFile(".git") ?: return null
            val headDoc = gitDir.findFile("HEAD") ?: return null
            val rawHead = context.contentResolver.openInputStream(headDoc.uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: return null
            val branch = parseGitHeadContent(rawHead)
            GitRepoStatusSummary(
                repoRootDisplay = rootDoc.name ?: "Repository",
                branchName = branch,
                modifiedFileCount = dirtyOpenDocCount,
                isFileTracked = true
            )
        }.getOrNull()
    }

    fun fetchLocalHeadBaselineContent(file: File): String? {
        var cur: File? = file.parentFile
        var repoRoot: File? = null
        while (cur != null) {
            if (File(cur, ".git").exists()) {
                repoRoot = cur
                break
            }
            cur = cur.parentFile
        }
        val root = repoRoot ?: return null
        val relPath = file.canonicalPath.removePrefix(root.canonicalPath).trimStart('/', '\\').replace('\\', '/')
        if (relPath.isEmpty()) return null

        return runCatching {
            val pb = ProcessBuilder("git", "show", "HEAD:$relPath")
            pb.directory(root)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val code = proc.waitFor()
            if (code == 0) out.replace("\r\n", "\n").replace('\r', '\n') else null
        }.getOrNull()
    }

    private fun queryOptionalGitCliCounts(repoRoot: File, targetFile: File?): Pair<Int, Boolean> {
        return runCatching {
            val pb = ProcessBuilder("git", "status", "--porcelain=v1")
            pb.directory(repoRoot)
            val proc = pb.start()
            val stdout = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val code = proc.waitFor()
            if (code != 0) return 0 to false
            val lines = stdout.lines().filter { it.length >= 3 }
            var tracked = targetFile != null
            if (targetFile != null) {
                val rel = targetFile.canonicalPath.removePrefix(repoRoot.canonicalPath).trimStart('/', '\\').replace('\\', '/')
                if (lines.any { it.startsWith("?? ") && it.drop(3).trim('"') == rel }) {
                    tracked = false
                }
            }
            lines.size to tracked
        }.getOrDefault(0 to false)
    }
}
