package com.gravityedit.app.domain.engine

import com.gravityedit.app.domain.model.DocumentEncoding
import com.gravityedit.app.domain.model.LineEnding
import com.gravityedit.app.domain.model.RemoteFileLocation
import com.gravityedit.app.domain.model.SftpConnectionProfile
import com.gravityedit.app.domain.model.SftpRemoteEntry
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties

object RemoteSFTPEngine {

    data class DownloadedRemotePayload(
        val content: String,
        val encoding: DocumentEncoding,
        val lineEnding: LineEnding
    )

    fun normalizeRemotePath(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "/"
        var cleaned = trimmed.replace("//", "/")
        if (cleaned.length > 1 && cleaned.endsWith("/")) {
            cleaned = cleaned.dropLast(1)
        }
        return if (cleaned.startsWith("/")) cleaned else "/$cleaned"
    }

    fun parentRemotePath(path: String): String {
        val norm = normalizeRemotePath(path)
        if (norm == "/") return "/"
        val idx = norm.lastIndexOf('/')
        return if (idx <= 0) "/" else norm.substring(0, idx)
    }

    fun listRemoteDirectory(
        profile: SftpConnectionProfile,
        directoryPath: String,
        password: String? = null,
        privateKeyPem: String? = null
    ): List<SftpRemoteEntry> {
        val normDir = normalizeRemotePath(directoryPath.ifBlank { profile.initialPath })
        return withSftpChannel(
            host = profile.host,
            port = profile.port,
            username = profile.username,
            password = password,
            privateKeyPem = privateKeyPem
        ) { channel ->
            val rawVec = channel.ls(normDir)
            val entries = ArrayList<SftpRemoteEntry>()
            for (item in rawVec) {
                val lsEntry = item as? ChannelSftp.LsEntry ?: continue
                val name = lsEntry.filename ?: continue
                if (name == "." || name == "..") continue
                val attrs = lsEntry.attrs
                val isDir = attrs?.isDir == true
                val fullPath = if (normDir == "/") "/$name" else "$normDir/$name"
                entries.add(
                    SftpRemoteEntry(
                        name = name,
                        fullPath = fullPath,
                        isDirectory = isDir,
                        sizeBytes = attrs?.size ?: 0L,
                        permissions = attrs?.permissionsString.orEmpty()
                    )
                )
            }
            entries.sortedWith(compareByDescending<SftpRemoteEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }

    fun downloadRemoteFile(
        location: RemoteFileLocation,
        password: String? = null
    ): DownloadedRemotePayload {
        val bytes = withSftpChannel(
            host = location.host,
            port = location.port,
            username = location.username,
            password = password,
            privateKeyPem = location.privateKeyPem
        ) { channel ->
            val out = ByteArrayOutputStream()
            channel.get(location.remotePath, out)
            out.toByteArray()
        }
        val (decodedText, detectedEnc) = decodeBytesWithBom(bytes)
        val detectedLe = LineEnding.detect(decodedText)
        return DownloadedRemotePayload(
            content = LineEnding.normalizeToLf(decodedText),
            encoding = detectedEnc,
            lineEnding = detectedLe
        )
    }

    fun uploadRemoteFile(
        content: String,
        encoding: DocumentEncoding,
        lineEnding: LineEnding,
        to: RemoteFileLocation,
        password: String? = null
    ) {
        val serialized = LineEnding.applyLineEnding(content, lineEnding)
        val bytes = encodeBytesWithBom(serialized, encoding)
        withSftpChannel(
            host = to.host,
            port = to.port,
            username = to.username,
            password = password,
            privateKeyPem = to.privateKeyPem
        ) { channel ->
            channel.put(ByteArrayInputStream(bytes), to.remotePath, ChannelSftp.OVERWRITE)
        }
    }

    private fun <T> withSftpChannel(
        host: String,
        port: Int,
        username: String,
        password: String?,
        privateKeyPem: String?,
        block: (ChannelSftp) -> T
    ): T {
        val cleanHost = host.trim()
        val cleanUser = username.trim()
        require(cleanHost.isNotEmpty() && cleanUser.isNotEmpty()) {
            "Both SFTP host and username are required."
        }

        val jsch = JSch()
        if (!privateKeyPem.isNullOrBlank()) {
            jsch.addIdentity(
                "gravity-ephemeral-key",
                privateKeyPem.trim().toByteArray(Charsets.UTF_8),
                null,
                password?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
            )
        }

        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = jsch.getSession(cleanUser, cleanHost, port.coerceIn(1, 65535))
            if (!password.isNullOrEmpty()) {
                session.setPassword(password)
            }
            val config = Properties()
            config["StrictHostKeyChecking"] = "accept-new"
            session.setConfig(config)
            session.connect(10_000)

            val rawChannel = session.openChannel("sftp")
            rawChannel.connect(10_000)
            channel = rawChannel as ChannelSftp
            return block(channel)
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { session?.disconnect() }
        }
    }

    private fun decodeBytesWithBom(bytes: ByteArray): Pair<String, DocumentEncoding> {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8) to DocumentEncoding.UTF_8_BOM
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE) to DocumentEncoding.UTF_16LE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE) to DocumentEncoding.UTF_16BE
        }
        return String(bytes, Charsets.UTF_8) to DocumentEncoding.UTF_8
    }

    private fun encodeBytesWithBom(text: String, encoding: DocumentEncoding): ByteArray {
        val body = text.toByteArray(encoding.resolveCharset())
        if (!encoding.hasBom) return body
        val bom = when (encoding) {
            DocumentEncoding.UTF_8_BOM -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            DocumentEncoding.UTF_16LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            DocumentEncoding.UTF_16BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
            else -> byteArrayOf()
        }
        return bom + body
    }
}
