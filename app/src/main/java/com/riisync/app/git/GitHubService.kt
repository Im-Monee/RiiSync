/**
 * GitHub API Service for RiiSync.
 * This file contains the logic for interacting with the GitHub REST API to perform repository searches,
 * profile lookups, and metadata retrieval.
 */
package com.riisync.app.git

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*

/**
 * A service class that communicates with the GitHub API.
 */
class GitHubService {

    /**
     * Data class representing basic repository information fetched from the API.
     */
    data class RepoInfo(
        val name: String,
        val fullName: String,
        val htmlUrl: String,
        val cloneUrl: String,
        val description: String?,
        val defaultBranch: String = "main",
        val ownerLogin: String = "",
        val canPush: Boolean = false,
        val isPrivate: Boolean = false
    )

    /**
     * Data class representing a file affected by a specific commit.
     */
    data class CommitFile(
        val filename: String,
        val status: String,
        val additions: Int,
        val deletions: Int,
        val patch: String?
    )

    /**
     * Data class representing a GitHub user's public profile information.
     */
    data class UserProfile(
        val login: String,
        val name: String?,
        val avatarUrl: String,
        val bio: String?,
        val followers: Int,
        val following: Int,
        val publicRepos: Int,
        val htmlUrl: String
    )

    /**
     * Data class representing an entry in a Git tree (file or folder).
     */
    data class GitHubFile(
        val path: String,
        val type: String,
        val size: Long,
        val url: String
    )

    // Helper to set Authorization header safely
    private fun setAuth(conn: HttpURLConnection, token: String?) {
        // Modern GitHub tokens work best with "Bearer".
        // We also ensure a consistent, professional User-Agent for all requests.
        token?.let { conn.setRequestProperty("Authorization", "Bearer ${it.trim()}") }
        conn.setRequestProperty("User-Agent", "RiiSync-App/1.0 (Android)")
    }

    /**
     * Checks if a specific user is a collaborator on a repository.
     * Returns true if the user is a collaborator.
     */
    suspend fun isUserCollaborator(owner: String, repo: String, username: String, token: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (token.isNullOrBlank() || username.isBlank()) return@withContext false
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/collaborators/$username")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            // GitHub returns 204 No Content if the user is a collaborator
            return@withContext conn.responseCode == 204
        } catch (e: Exception) {
            Log.e("GitHubService", "isUserCollaborator exception", e)
            false
        }
    }

    /**
     * Retrieves the file tree for a specific repository and branch.
     */
    suspend fun getRepositoryTree(fullName: String, branch: String = "main", token: String? = null): List<GitHubFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<GitHubFile>()
        try {
            val url = URL("https://api.github.com/repos/$fullName/git/trees/$branch?recursive=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(response)
                val tree = obj.getJSONArray("tree")
                for (i in 0 until tree.length()) {
                    val f = tree.getJSONObject(i)
                    files.add(GitHubFile(
                        path = f.getString("path"),
                        type = f.getString("type"),
                        size = if (f.has("size")) f.getLong("size") else 0,
                        url = f.getString("url")
                    ))
                }
            } else {
                Log.d("GitHubService", "getRepositoryTree error: ${conn.responseCode} ${conn.responseMessage}")
            }
        } catch (e: Exception) { 
            Log.e("GitHubService", "getRepositoryTree exception", e)
        }
        files
    }

    /**
     * Fetches the content of a repository's README file in HTML format.
     */
    suspend fun getReadmeHtml(fullName: String, token: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$fullName/readme")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3.html")

            if (conn.responseCode == 200) {
                return@withContext conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.d("GitHubService", "getReadmeHtml error: ${conn.responseCode} ${conn.responseMessage}")
            }
        } catch (e: Exception) { 
            Log.e("GitHubService", "getReadmeHtml exception", e)
        }
        null
    }

    /**
     * Fetches details for a specific user profile.
     * If username is empty, it fetches the authenticated user's profile.
     */
    suspend fun getUserProfile(username: String, token: String? = null): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val urlString = if (username.isBlank()) "https://api.github.com/user" else "https://api.github.com/users/$username"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(response)
                return@withContext UserProfile(
                    login = obj.getString("login"),
                    name = if (obj.isNull("name")) null else obj.getString("name"),
                    avatarUrl = obj.getString("avatar_url"),
                    bio = if (obj.isNull("bio")) null else obj.getString("bio"),
                    followers = obj.getInt("followers"),
                    following = obj.getInt("following"),
                    publicRepos = obj.getInt("public_repos"),
                    htmlUrl = obj.getString("html_url")
                )
            } else {
                Log.d("GitHubService", "getUserProfile error: ${conn.responseCode} ${conn.responseMessage}")
            }
        } catch (e: Exception) {
            Log.e("GitHubService", "getUserProfile exception", e)
        }
        null
    }

    /**
     * Lists repositories belonging to a specified user.
     */
    suspend fun getUserRepos(username: String, token: String? = null, isOwnProfile: Boolean = false): List<RepoInfo> = withContext(Dispatchers.IO) {
        val repos = mutableListOf<RepoInfo>()
        try {
            val urlString = if (isOwnProfile && token != null) {
                // Explicitly request both owned and collaborative repositories
                "https://api.github.com/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator"
            } else {
                "https://api.github.com/users/$username/repos?per_page=100&sort=updated"
            }
            
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    try {
                        repos.add(parseRepo(jsonArray.getJSONObject(i)))
                    } catch (e: Exception) { 
                        Log.e("GitHubService", "parseRepo getUserRepos exception", e)
                    }
                }
                Log.d("GitHubService", "getUserRepos success: fetched ${repos.size} repos for $username")
            } else {
                Log.e("GitHubService", "getUserRepos error: ${conn.responseCode} ${conn.responseMessage} for $username")
            }
        } catch (e: Exception) {
            Log.e("GitHubService", "getUserRepos exception", e)
        }
        repos
    }

    /**
     * Searches for repositories on GitHub matching a search query.
     */
    suspend fun searchRepositories(query: String, token: String? = null, page: Int = 1): List<RepoInfo> = withContext(Dispatchers.IO) {
        val repos = mutableListOf<RepoInfo>()
        if (query.isBlank()) return@withContext repos
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://api.github.com/search/repositories?q=$encodedQuery&sort=updated&order=desc&per_page=15&page=$page")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(response)
                val items = jsonObj.getJSONArray("items")
                for (i in 0 until items.length()) {
                    try {
                        repos.add(parseRepo(items.getJSONObject(i)))
                    } catch (e: Exception) { 
                        Log.e("GitHubService", "parseRepo searchRepositories exception", e)
                    }
                }
            } else {
                Log.d("GitHubService", "searchRepositories error: ${conn.responseCode} ${conn.responseMessage}")
            }
        } catch (e: Exception) {
            Log.e("GitHubService", "searchRepositories exception", e)
        }
        repos
    }

    /**
     * Searches for GitHub users matching a search query.
     */
    suspend fun searchUsers(query: String, token: String? = null, page: Int = 1): List<UserProfile> = withContext(Dispatchers.IO) {
        val users = mutableListOf<UserProfile>()
        if (query.isBlank()) return@withContext users
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://api.github.com/search/users?q=$encodedQuery&per_page=15&page=$page")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(response)
                val items = jsonObj.getJSONArray("items")
                for (i in 0 until items.length()) {
                    try {
                        val obj = items.getJSONObject(i)
                        users.add(UserProfile(
                            login = obj.getString("login"),
                            name = null,
                            avatarUrl = obj.getString("avatar_url"),
                            bio = null,
                            followers = 0,
                            following = 0,
                            publicRepos = 0,
                            htmlUrl = obj.getString("html_url")
                        ))
                    } catch (e: Exception) { 
                        Log.e("GitHubService", "parseUserProfile searchUsers exception", e)
                    }
                }
            } else {
                Log.d("GitHubService", "searchUsers error: ${conn.responseCode} ${conn.responseMessage}")
            }
        } catch (e: Exception) {
            Log.e("GitHubService", "searchUsers exception", e)
        }
        users
    }

    /**
     * Creates a new repository for the authenticated user.
     */
    suspend fun createRepository(name: String, description: String, private: Boolean, token: String): RepoInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/user/repos")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            // token parameter is mandatory here
            setAuth(conn, token)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val json = JSONObject().apply {
                put("name", name)
                put("description", description)
                put("private", private)
                put("auto_init", false)
            }

            conn.outputStream.use { it.write(json.toString().toByteArray()) }

            if (conn.responseCode == 201) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseRepo(JSONObject(response))
            } else {
                Log.d("GitHubService", "createRepository error: ${conn.responseCode} ${conn.responseMessage}")
            }
        } catch (e: Exception) {
            Log.e("GitHubService", "createRepository exception", e)
        }
        null
    }

    /**
     * Fetches detailed information for a specific repository by its full name.
     */
    suspend fun getRepository(fullName: String, token: String? = null): RepoInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$fullName")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseRepo(JSONObject(response))
            } else {
                Log.d("GitHubService", "getRepository error: ${conn.responseCode} ${conn.responseMessage}")
            }
        } catch (e: Exception) {
            Log.e("GitHubService", "getRepository exception", e)
        }
        null
    }

    /**
     * Retrieves the list of collaborators for a repository.
     */
    suspend fun getCollaborators(fullName: String, token: String?): List<UserProfile> = withContext(Dispatchers.IO) {
        val collaborators = mutableListOf<UserProfile>()
        try {
            val url = URL("https://api.github.com/repos/$fullName/collaborators")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    collaborators.add(UserProfile(
                        login = obj.getString("login"),
                        name = null,
                        avatarUrl = obj.getString("avatar_url"),
                        bio = null,
                        followers = 0,
                        following = 0,
                        publicRepos = 0,
                        htmlUrl = obj.getString("html_url")
                    ))
                }
            } else {
                Log.d("GitHubService", "getCollaborators error: ${conn.responseCode} ${conn.responseMessage}")
            }
        } catch (e: Exception) {
            Log.e("GitHubService", "getCollaborators exception", e)
        }
        collaborators
    }

    /**
     * Helper to parse a JSON object into a [RepoInfo] data class.
     */
    private fun parseRepo(obj: JSONObject): RepoInfo {
        val permissions = obj.optJSONObject("permissions")
        return RepoInfo(
            name = obj.getString("name"),
            fullName = obj.getString("full_name"),
            htmlUrl = obj.getString("html_url"),
            cloneUrl = obj.getString("clone_url"),
            description = if (obj.isNull("description")) null else obj.getString("description"),
            defaultBranch = obj.optString("default_branch", "main"),
            ownerLogin = obj.getJSONObject("owner").getString("login"),
            canPush = permissions?.optBoolean("push", false) ?: false,
            isPrivate = obj.optBoolean("private", false)
        )
    }

    /**
     * Retrieves the list of recent commits for a repository.
     */
    suspend fun getRepoCommits(fullName: String, token: String?, page: Int = 1): List<GitManager.CommitInfo> = withContext(Dispatchers.IO) {
        val commits = mutableListOf<GitManager.CommitInfo>()
        try {
            val url = URL("https://api.github.com/repos/$fullName/commits?per_page=10&page=$page")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")

                for (i in 0 until jsonArray.length()) {
                    try {
                        val obj = jsonArray.getJSONObject(i)
                        val commitObj = obj.getJSONObject("commit")
                        val authorObj = commitObj.getJSONObject("author")
                        
                        commits.add(
                            GitManager.CommitInfo(
                                hash = obj.getString("sha").take(7),
                                author = authorObj.getString("name"),
                                message = commitObj.getString("message").lines().firstOrNull() ?: "",
                                date = dateFormat.parse(authorObj.getString("date")) ?: Date(),
                                fullHash = obj.getString("sha")
                            )
                        )
                    } catch (e: Exception) { 
                        Log.e("GitHubService", "parseCommit getRepoCommits exception", e)
                    }
                }
            } else {
                Log.d("GitHubService", "getRepoCommits error: ${conn.responseCode} ${conn.responseMessage}")
            }
        } catch (e: Exception) {
            Log.e("GitHubService", "getRepoCommits exception", e)
        }
        commits
    }

    /**
     * Retrieves the files changed in a specific commit.
     */
    suspend fun getCommitFiles(fullName: String, sha: String, token: String?): List<CommitFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<CommitFile>()
        try {
            val url = URL("https://api.github.com/repos/$fullName/commits/$sha")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(response)
                val filesArray = jsonObj.getJSONArray("files")
                for (i in 0 until filesArray.length()) {
                    try {
                        val f = filesArray.getJSONObject(i)
                        files.add(
                            CommitFile(
                                filename = f.getString("filename"),
                                status = f.getString("status"),
                                additions = f.getInt("additions"),
                                deletions = f.getInt("deletions"),
                                patch = if (f.isNull("patch")) null else f.getString("patch")
                            )
                        )
                    } catch (e: Exception) { 
                        Log.e("GitHubService", "parseCommitFile getCommitFiles exception", e)
                    }
                }
            } else {
                Log.d("GitHubService", "getCommitFiles error: ${conn.responseCode} ${conn.responseMessage}")
            }
        } catch (e: Exception) {
            Log.e("GitHubService", "getCommitFiles exception", e)
        }
        files
    }

    /**
     * Retrieves the latest release version tag for the RiiSync app from GitHub.
     */
    suspend fun getLatestReleaseVersion(token: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/Mone/RiiSync/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(response)
                return@withContext jsonObj.getString("tag_name")
            } else {
                Log.d("GitHubService", "getLatestReleaseVersion error: ${conn.responseCode} ${conn.responseMessage}")
            }
        } catch (e: Exception) {
            Log.e("GitHubService", "getLatestReleaseVersion exception", e)
        }
        null
    }

    /**
     * Fetches metadata for the latest release of a GitHub repository.
     */
    suspend fun getLatestRelease(owner: String, repo: String, token: String? = null): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            setAuth(conn, token)
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext JSONObject(response)
            } else {
                Log.d("GitHubService", "getLatestRelease error ($owner/$repo): ${conn.responseCode} ${conn.responseMessage}")
            }
        } catch (e: Exception) {
            Log.e("GitHubService", "getLatestRelease exception", e)
        }
        null
    }

    /**
     * Fetches metadata for the latest release of Shizuku from GitHub.
     */
    suspend fun getLatestShizukuRelease(token: String? = null): JSONObject? = getLatestRelease("RikkaApps", "Shizuku", token)
}
