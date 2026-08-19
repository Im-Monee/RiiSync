package com.riisync.app.grabber

import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import org.jsoup.Jsoup
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-performance Crawler for the MarioCube WiiSave archive.
 * Uses a unified worker-queue system with smart pruning and local caching.
 */
class WiiSaveRepositoryCrawler(private val baseUrl: String, private val cacheDir: File) {

    private val crawlerCacheFile = File(cacheDir, "crawler_cache.txt")

    private fun loadCache(): List<String> {
        return if (crawlerCacheFile.exists()) {
            try { crawlerCacheFile.readLines().filter { it.isNotBlank() } } catch (e: Exception) { emptyList() }
        } else emptyList()
    }

    private fun saveCache(urls: List<String>) {
        try {
            cacheDir.mkdirs()
            crawlerCacheFile.writeText(urls.distinct().joinToString("\n"))
        } catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * Unified crawl entry point.
     * @param filterIds Optional list of Game IDs to restrict the search.
     * @param onProgress Callback (FilesFound, FoldersScanned).
     */
    suspend fun crawl(filterIds: List<String>? = null, onProgress: (Int, Int) -> Unit): List<String> = coroutineScope {
        val cachedUrls = loadCache()
        val dataBinUrls = CopyOnWriteArrayList<String>(cachedUrls)
        val visited = ConcurrentHashMap.newKeySet<String>()
        val queue = LinkedBlockingQueue<String>()
        
        val cleanFilterIds = filterIds?.map { it.take(4).uppercase() }?.distinct()
        
        // Initial reporting
        onProgress(dataBinUrls.size, 0)
        
        // Start the root
        queue.put(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        
        val activeWorkers = AtomicInteger(0)
        val foldersScanned = AtomicInteger(0)
        
        // Use 4 workers for stability. Too many workers on mobile cause memory/socket exhaustion.
        val workerCount = 4
        
        val jobs = List(workerCount) {
            launch(Dispatchers.IO) {
                try {
                    while (isActive) {
                        // Polling with a short timeout allows us to detect when the queue is truly exhausted
                        val currentUrl = queue.poll(1000, TimeUnit.MILLISECONDS)
                        
                        if (currentUrl == null) {
                            // If queue is empty AND no other workers are currently finding things, we are done
                            if (activeWorkers.get() == 0) break
                            continue
                        }

                        if (!visited.add(currentUrl)) continue
                        
                        activeWorkers.incrementAndGet()
                        try {
                            val scanned = foldersScanned.incrementAndGet()
                            onProgress(dataBinUrls.size, scanned)

                            val doc = Jsoup.connect(currentUrl)
                                .timeout(10000)
                                .userAgent("RiiSync-Crawler/1.1")
                                .get()
                            
                            val links = doc.select("a[href]")
                            for (link in links) {
                                val href = link.attr("href")
                                if (href.startsWith("?") || href.startsWith("/") || href.contains("..")) continue
                                
                                val absoluteUrl = if (currentUrl.endsWith("/")) currentUrl + href else "$currentUrl/$href"
                                val decodedHref = try { java.net.URLDecoder.decode(href, "UTF-8") } catch (e: Exception) { href }

                                if (href.equals("data.bin", ignoreCase = true)) {
                                    val decodedUrl = try { java.net.URLDecoder.decode(absoluteUrl, "UTF-8") } catch (e: Exception) { absoluteUrl }
                                    
                                    val matchesFilter = cleanFilterIds == null || cleanFilterIds.any { id ->
                                        decodedUrl.contains(id, ignoreCase = true)
                                    }
                                    
                                    if (matchesFilter && !dataBinUrls.contains(absoluteUrl)) {
                                        dataBinUrls.add(absoluteUrl)
                                        onProgress(dataBinUrls.size, scanned)
                                    }
                                } else if (href.endsWith("/") || !href.contains(".")) {
                                    val folderName = decodedHref.removeSuffix("/").trim()
                                    
                                    // SMART PRUNING: Only queue subfolders that match our filter or generic paths
                                    val shouldEnter = cleanFilterIds == null || 
                                                      folderName.length <= 3 || // Region/Alpha folders
                                                      folderName.equals("NTSC-U", true) || 
                                                      folderName.equals("NTSC-J", true) || 
                                                      folderName.equals("PAL", true) ||
                                                      cleanFilterIds.any { id ->
                                                          folderName.contains(id, ignoreCase = true) || id.contains(folderName, ignoreCase = true)
                                                      }

                                    if (shouldEnter) {
                                        val nextUrl = if (absoluteUrl.endsWith("/")) absoluteUrl else "$absoluteUrl/"
                                        if (!visited.contains(nextUrl)) {
                                            queue.put(nextUrl)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Specific folder error, skip it
                        } finally {
                            activeWorkers.decrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    // Worker level error
                }
            }
        }

        jobs.joinAll()
        val finalResults = dataBinUrls.toList()
        saveCache(finalResults)
        finalResults
    }
}
