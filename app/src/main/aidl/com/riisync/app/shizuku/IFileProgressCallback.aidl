package com.riisync.app.shizuku;

interface IFileProgressCallback {
    void onProgress(int current, int total, String currentFile);
}
