package com.riisync.app.git

import java.net.HttpURLConnection

// Shared helper to set Authorization header correctly using Bearer tokens.
fun setAuthBearer(conn: HttpURLConnection, token: String?) {
    token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
    conn.setRequestProperty("User-Agent", "RiiSync-App/1.0 (Android)")
}
