/**
 * Git Operations Manager for RiiSync.
 * This file contains the logic for performing local Git operations like cloning, pulling,
 * committing, and pushing using the JGit library.
 */
package com.riisync.app.git

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.*
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.io.DisabledOutputStream
import java.io.File
import java.util.*

/**
 * Manager class for executing JGit commands.
 */
class GitManager {

    /**
     * Sealed class representing the result of a Git operation.
     */
    sealed class Result {
        data class Success(val message: String, val changedFiles: List<String> = emptyList()) : Result()
        data class Error(val message: String) : Result()
    }

    /**
     * Information about a specific Git commit.
     */
    data class CommitInfo(
        val hash: String,
        val author: String,
        val message: String,
        val date: Date,
        val fullHash: String = ""
    )

    /**
     * Represents a change in a local file compared to the Git index.
     */
    data class LocalChange(
        val path: String,
        val status: String // Modified, Added, Deleted, Untracked
    )

    /**
     * Creates a simple username/password credentials provider.
     */
    private fun credentials(username: String, token: String) =
        UsernamePasswordCredentialsProvider(username, token)

    /**
     * Configures SSH transport for Git operations using a private key.
     */
    private fun sshTransportCallback(privateKeyPath: String?): TransportConfigCallback? {
        if (privateKeyPath == null) return null
        val sshSessionFactory = object : JschConfigSessionFactory() {
            override fun configure(hc: OpenSshConfig.Host, session: com.jcraft.jsch.Session) {
                session.setConfig("StrictHostKeyChecking", "no")
            }

            override fun createDefaultJSch(fs: FS): com.jcraft.jsch.JSch {
                val jsch = super.createDefaultJSch(fs)
                jsch.addIdentity(privateKeyPath)
                return jsch
            }
        }
        return TransportConfigCallback { transport ->
            if (transport is SshTransport) {
                transport.sshSessionFactory = sshSessionFactory
            }
        }
    }

    /**
     * Clones a remote repository to a local directory.
     */
    suspend fun clone(
        repoUrl: String,
        localDir: File,
        username: String = "",
        token: String = "",
        privateKeyPath: String? = null,
        progressMonitor: ProgressMonitor? = null
    ): Result = withContext(Dispatchers.IO) {
        try {
            val absoluteDir = if (localDir.isAbsolute) localDir else File("/", localDir.path)

            if (absoluteDir.exists()) {
                val files = absoluteDir.listFiles()
                if (files != null && files.isNotEmpty()) {
                    return@withContext Result.Error("Directory is not empty.")
                }
            } else {
                absoluteDir.mkdirs()
            }

            val cloneOp = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(absoluteDir)
                .setProgressMonitor(progressMonitor)
            
            if (repoUrl.startsWith("http")) {
                cloneOp.setCredentialsProvider(credentials(username, token))
            } else {
                sshTransportCallback(privateKeyPath)?.let { cloneOp.setTransportConfigCallback(it) }
            }

            cloneOp.call().close()
            Result.Success("Cloned to ${absoluteDir.name}")
        } catch (e: GitAPIException) {
            Log.e("GitManager", "GitAPIException during clone", e)
            Result.Error(e.message ?: "Clone failed")
        } catch (e: Exception) {
            Log.e("GitManager", "Exception during clone", e)
            Result.Error(e.message ?: "Unknown clone error")
        }
    }

    /**
     * Pulls updates from the remote repository to the local working directory.
     */
    suspend fun pull(
        localDir: File,
        username: String = "",
        token: String = "",
        privateKeyPath: String? = null,
        progressMonitor: ProgressMonitor? = null
    ): Result = withContext(Dispatchers.IO) {
        try {
            Git.open(localDir).use { git ->
                val pullOp = git.pull().setProgressMonitor(progressMonitor)
                val remoteUrl = git.repository.config.getString("remote", "origin", "url") ?: ""

                if (remoteUrl.startsWith("http")) {
                    pullOp.setCredentialsProvider(credentials(username, token))
                } else {
                    sshTransportCallback(privateKeyPath)?.let { pullOp.setTransportConfigCallback(it) }
                }

                val res = pullOp.call()
                if (res.isSuccessful) {
                    val changedFiles = mutableListOf<String>()
                    val fetchRes = res.fetchResult
                    val mergeRes = res.mergeResult
                    
                    if (mergeRes != null && mergeRes.mergeStatus.isSuccessful) {
                        // In JGit, getting the list of changed files from a merge is non-trivial without scanning.
                        // For simplicity and safety, we\u0027ll return a list containing "riivolution" if it exists,
                        // which will trigger the auto-link check in the UI.
                        if (File(localDir, "riivolution").exists()) {
                            changedFiles.add("riivolution")
                        }
                    }
                    
                    Result.Success("Pull operation successful", changedFiles)
                } else {
                    Result.Error("Merge conflict detected")
                }
            }
        } catch (e: GitAPIException) {
            Log.e("GitManager", "GitAPIException during pull", e)
            Result.Error(e.message ?: "Pull failed")
        } catch (e: Exception) {
            Log.e("GitManager", "Exception during pull", e)
            Result.Error(e.message ?: "Unknown pull error")
        }
    }

    /**
     * Stages all changes, commits them with a message, and pushes to the remote repository.
     */
    suspend fun commitAndPush(
        localDir: File,
        commitMessage: String,
        authorName: String,
        authorEmail: String,
        username: String = "",
        token: String = "",
        privateKeyPath: String? = null,
        progressMonitor: ProgressMonitor? = null
    ): Result = withContext(Dispatchers.IO) {
        try {
            Git.open(localDir).use { git ->
                git.add().addFilepattern(".").call()
                git.commit()
                    .setMessage(commitMessage)
                    .setAuthor(authorName, authorEmail)
                    .call()
                
                val pushOp = git.push().setProgressMonitor(progressMonitor)
                val remoteUrl = git.repository.config.getString("remote", "origin", "url") ?: ""

                if (remoteUrl.startsWith("http")) {
                    pushOp.setCredentialsProvider(credentials(username, token))
                } else {
                    sshTransportCallback(privateKeyPath)?.let { pushOp.setTransportConfigCallback(it) }
                }
                
                pushOp.call()
            }
            Result.Success("Changes published successfully")
        } catch (e: GitAPIException) {
            Log.e("GitManager", "GitAPIException during push", e)
            Result.Error(e.message ?: "Publish failed")
        } catch (e: Exception) {
            Log.e("GitManager", "Exception during push", e)
            Result.Error(e.message ?: "Unknown push error")
        }
    }

    /**
     * Fetches metadata from the remote without modifying the local working directory.
     */
    suspend fun fetch(
        localDir: File,
        username: String = "",
        token: String = "",
        privateKeyPath: String? = null,
        progressMonitor: ProgressMonitor? = null
    ): Result = withContext(Dispatchers.IO) {
        try {
            Git.open(localDir).use { git ->
                val fetchOp = git.fetch().setProgressMonitor(progressMonitor)
                val remoteUrl = git.repository.config.getString("remote", "origin", "url") ?: ""

                if (remoteUrl.startsWith("http")) {
                    fetchOp.setCredentialsProvider(credentials(username, token))
                } else {
                    sshTransportCallback(privateKeyPath)?.let { fetchOp.setTransportConfigCallback(it) }
                }

                fetchOp.call()
                Result.Success("Updated from remote")
            }
        } catch (e: Exception) {
            Log.e("GitManager", "Exception during fetch", e)
            Result.Error(e.message ?: "Fetch failed")
        }
    }

    /**
     * Retrieves commits present on the remote branch but not yet merged into the local branch.
     */
    suspend fun getIncomingCommits(localDir: File): List<CommitInfo> = withContext(Dispatchers.IO) {
        val incoming = mutableListOf<CommitInfo>()
        try {
            Git.open(localDir).use { git ->
                val repo = git.repository
                val head = repo.resolve("HEAD") ?: return@withContext emptyList()
                val branchName = repo.branch
                val remoteBranch = repo.resolve("refs/remotes/origin/$branchName") 
                    ?: repo.resolve("refs/remotes/origin/main")
                    ?: repo.resolve("refs/remotes/origin/master")
                    ?: return@withContext emptyList()

                val walk = RevWalk(repo)
                walk.markStart(walk.parseCommit(remoteBranch))
                walk.markUninteresting(walk.parseCommit(head))

                for (commit in walk) {
                    incoming.add(
                        CommitInfo(
                            hash = commit.name.take(7),
                            fullHash = commit.name,
                            author = commit.authorIdent.name,
                            message = commit.shortMessage,
                            date = commit.authorIdent.`when`
                        )
                    )
                }
                walk.dispose()
            }
        } catch (e: Exception) {
            Log.e("GitManager", "getIncomingCommits exception", e)
        }
        incoming
    }

    /**
     * Lists files changed in a specific local commit.
     */
    suspend fun getFilesForCommitLocal(localDir: File, commitSha: String): List<LocalChange> = withContext(Dispatchers.IO) {
        val files = mutableListOf<LocalChange>()
        try {
            Git.open(localDir).use { git ->
                val repo = git.repository
                val walk = RevWalk(repo)
                val commit = walk.parseCommit(repo.resolve(commitSha))
                val parent = if (commit.parentCount > 0) walk.parseCommit(commit.getParent(0).id) else null
                
                val df = DiffFormatter(DisabledOutputStream.INSTANCE)
                df.setRepository(repo)
                df.setDiffComparator(RawTextComparator.DEFAULT)
                df.isDetectRenames = true

                val diffs = if (parent != null) {
                    df.scan(parent.tree, commit.tree)
                } else {
                    df.scan(null, commit.tree)
                }

                for (diff in diffs) {
                    files.add(LocalChange(diff.newPath, diff.changeType.name))
                }
                df.close()
                walk.dispose()
            }
        } catch (e: Exception) {
            Log.e("GitManager", "getFilesForCommitLocal exception", e)
        }
        files
    }

    /**
     * Generates a diff string for a specific file in the repository.
     */
    suspend fun getFileDiff(localDir: File, path: String): List<String> = withContext(Dispatchers.IO) {
        val diffLines = mutableListOf<String>()
        try {
            Git.open(localDir).use { git ->
                val repo = git.repository
                val out = java.io.ByteArrayOutputStream()
                val df = DiffFormatter(out)
                df.setRepository(repo)
                val diffs = git.diff().setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(path)).call()
                for (diff in diffs) {
                    df.format(diff)
                }
                df.flush()
                diffLines.addAll(out.toString().lines())
                df.close()
            }
        } catch (e: Exception) {
            Log.e("GitManager", "getFileDiff exception", e)
        }
        diffLines
    }

    /**
     * Lists all local branches in the repository.
     */
    suspend fun getBranches(localDir: File): List<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(localDir).use { git ->
                git.branchList().call().map { it.name.substringAfterLast("/") }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Switches the repository to the specified branch.
     */
    suspend fun checkoutBranch(localDir: File, branchName: String): Result = withContext(Dispatchers.IO) {
        try {
            Git.open(localDir).use { git ->
                git.checkout().setName(branchName).call()
                Result.Success("Switched to $branchName")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Checkout failed")
        }
    }

    /**
     * Reverts uncommitted changes for a specific path or the entire repository.
     */
    suspend fun discardChanges(localDir: File, path: String? = null): Result = withContext(Dispatchers.IO) {
        try {
            Git.open(localDir).use { git ->
                val checkoutOp = git.checkout()
                if (path != null) checkoutOp.addPath(path) else checkoutOp.setAllPaths(true)
                checkoutOp.call()
                Result.Success("Changes discarded")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Discard failed")
        }
    }

    /**
     * Scans the repository for modified, added, deleted, or untracked files.
     */
    suspend fun getLocalChanges(localDir: File): List<LocalChange> = withContext(Dispatchers.IO) {
        val changes = mutableListOf<LocalChange>()
        try {
            if (!localDir.exists()) return@withContext emptyList()
            Git.open(localDir).use { git ->
                val status = git.status().call()
                status.modified.forEach { changes.add(LocalChange(it, "Modified")) }
                status.added.forEach { changes.add(LocalChange(it, "Added")) }
                status.removed.forEach { changes.add(LocalChange(it, "Deleted")) }
                status.missing.forEach { changes.add(LocalChange(it, "Deleted")) }
                status.untracked.forEach { changes.add(LocalChange(it, "Untracked")) }
                status.changed.forEach { changes.add(LocalChange(it, "Changed")) }
            }
        } catch (e: Exception) {
            Log.e("GitManager", "getLocalChanges exception", e)
        }
        changes.distinctBy { it.path }
    }

    /**
     * Creates a new Git stash containing uncommitted changes.
     */
    suspend fun stashCreate(localDir: File, message: String = "RiiSync Stash"): Result = withContext(Dispatchers.IO) {
        try {
            Git.open(localDir).use { git ->
                val res = git.stashCreate().setIndexMessage(message).call()
                if (res != null) Result.Success("Workspace stashed") else Result.Success("Nothing to stash")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Stash failed")
        }
    }

    /**
     * Pops the most recent stash onto the working directory.
     */
    suspend fun stashPop(localDir: File): Result = withContext(Dispatchers.IO) {
        try {
            Git.open(localDir).use { git ->
                git.stashApply().call()
                git.stashDrop().setStashRef(0).call()
                Result.Success("Stash applied")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Apply stash failed")
        }
    }

    /**
     * Retrieves the full commit history of the repository.
     */
    suspend fun getCommitHistory(localDir: File): List<CommitInfo> = withContext(Dispatchers.IO) {
        try {
            Git.open(localDir).use { git ->
                git.log().call().map { rev ->
                    CommitInfo(
                        hash = rev.name.take(7),
                        fullHash = rev.name,
                        author = rev.authorIdent.name,
                        message = rev.shortMessage,
                        date = rev.authorIdent.`when`
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GitManager", "getCommitHistory exception", e)
            emptyList()
        }
    }

    /**
     * Calculates repository statistics such as disk usage and cleanliness.
     */
    suspend fun getRepositoryStats(localDir: File): Map<String, String> = withContext(Dispatchers.IO) {
        val stats = mutableMapOf<String, String>()
        try {
            val totalSize = calculateSize(localDir)
            stats["disk_usage"] = formatSize(totalSize)
            
            Git.open(localDir).use { git ->
                val lastCommit = git.log().setMaxCount(1).call().firstOrNull()
                stats["last_sync"] = lastCommit?.authorIdent?.`when`?.toString() ?: "Never"
                
                val status = git.status().call()
                stats["is_clean"] = status.isClean.toString()
                stats["modified_count"] = (status.modified.size + status.added.size + status.removed.size).toString()
            }
        } catch (e: Exception) { Log.e("GitManager", "getRepositoryStats exception", e) }
        stats
    }

    /**
     * Recursively calculates the size of a directory.
     */
    private fun calculateSize(file: File): Long {
        if (file.isFile) return file.length()
        var size = 0L
        file.listFiles()?.forEach { size += calculateSize(it) }
        return size
    }

    /**
     * Formats a raw byte count into a human-readable string (KB, MB, GB).
     */
    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    /**
     * Resolves merge conflicts by choosing either \u0027ours\u0027 (MINE) or \u0027theirs\u0027 (THEIRS) version.
     */
    suspend fun resolveConflict(localDir: File, strategy: String): Result = withContext(Dispatchers.IO) {
        try {
            Git.open(localDir).use { git ->
                // strategy: "MINE" or "THEIRS"
                val checkoutOp = git.checkout()
                if (strategy == "MINE") {
                    checkoutOp.setStage(org.eclipse.jgit.api.CheckoutCommand.Stage.OURS)
                } else {
                    checkoutOp.setStage(org.eclipse.jgit.api.CheckoutCommand.Stage.THEIRS)
                }
                checkoutOp.addPath(".").call()
                git.add().addFilepattern(".").call()
                git.commit().setMessage("Resolved conflicts using $strategy strategy").call()
                Result.Success("Conflicts resolved successfully")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Resolution failed")
        }
    }

    /**
     * Retrieves the URL of the \u0027origin\u0027 remote repository.
     */
    suspend fun getRemoteUrl(localDir: File): String? = withContext(Dispatchers.IO) {
        try {
            Git.open(localDir).use { git ->
                return@withContext git.repository.config.getString("remote", "origin", "url")
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Configures a new remote named \u0027origin\u0027 with the specified URL.
     */
    suspend fun addRemote(localDir: File, url: String): Result = withContext(Dispatchers.IO) {
        try {
            Git.open(localDir).use { git ->
                git.remoteAdd()
                    .setName("origin")
                    .setUri(URIish(url))
                    .call()
                Result.Success("Remote added: $url")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to add remote")
        }
    }
}
