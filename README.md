# 🚀 rn-downloader

The easiest way to download files in React Native — with background support, pause/resume, and cache management built-in.

> 100% pure native code (Kotlin + Swift). Zero third-party dependencies.

## ✨ Features

- 📥 **Download with progress** — clean `0 → 100` progress natively, no UI freezing
- 🌙 **Background downloads** — survive app suspension (iOS background URLSession + Android DownloadManager)
- ⏸ **Pause & Resume** — resume mid-download using HTTP Range requests
- ❌ **Cancel** — cancel any active download, partial files are cleaned up automatically
- 🔄 **Re-attach** — reconnect to background downloads after app restart
- 📱 **Expo Support** — includes a config plugin for zero-config integration
- ⚡ **TurboModules** — built on the React Native New Architecture
- 📦 **File management** — list, delete individual files, or clear all downloads from Downloads folder
- 📂 **Smart file naming** — auto-detects filename from URL if not provided
- ⚡ **Lightweight** — zero base dependencies

---

## 💻 Installation

```sh
npm install rn-downloader
# or
yarn add rn-downloader
```

```

### 🍎 Expo Installation
Add the plugin to your `app.json` or `app.config.js`:
```json
{
  "expo": {
    "plugins": ["rn-downloader"]
  }
}
```

> iOS: For bare projects, run `pod install` in your `ios/` directory after installing.

---

## 📖 API

### `download(options)`

```javascript
import { download, onDownloadComplete, onDownloadError } from 'rn-downloader';

// Foreground download
const result = await download({
  url: 'https://example.com/file.pdf',
  fileName: 'my_file.pdf',
  onProgress: (percent) => console.log(`Progress: ${percent}%`),
});
if (result.success) {
  console.log('Saved to:', result.filePath);
  console.log('Download ID:', result.downloadId);
}

// Background download (resolves immediately with downloadId)
const { downloadId } = await download({
  url: 'https://example.com/video.mp4',
  background: true,
});
const unsub = onDownloadComplete((r) => {
  console.log('Done:', r.filePath);
  unsub();
});
```

---

### Pause / Resume / Cancel

```javascript
import {
  download,
  pauseDownload,
  resumeDownload,
  cancelDownload,
} from 'rn-downloader';

const { downloadId } = await download({
  url: 'https://example.com/file.zip',
  onProgress: (p) => console.log(`${p}%`),
});

await pauseDownload(downloadId); // pause
await resumeDownload(downloadId); // resume from where it left off (HTTP Range)
await cancelDownload(downloadId); // cancel + delete partial file
```

---

### Re-attach (Background Persistence)

If the app is closed or crashes during a background download, use `getBackgroundDownloads` on restart to find and reconnect to ongoing tasks.

```javascript
import { getBackgroundDownloads } from 'rn-downloader';

const checkOngoing = async () => {
  const { success, downloads } = await getBackgroundDownloads();
  if (success && downloads) {
    downloads.forEach(dl => {
      console.log(`Still downloading: ${dl.downloadId} (${dl.progress}%)`);
      // Re-attach listeners globally using onDownloadComplete/onDownloadError
    });
  }
};
```

---

### Cache Management

```javascript
import { getCachedFiles, deleteFile, clearCache } from 'rn-downloader';

// List all downloaded files in Downloads folder
const { files } = await getCachedFiles();
files?.forEach((f) => console.log(f.fileName, f.size));

// Delete a specific file
await deleteFile('/path/to/file.pdf');

// Clear all downloads from Downloads folder
await clearCache();
```

---

## 📐 Type Reference

| Type              | Fields                                                    |
| ----------------- | --------------------------------------------------------- |
| `DownloadOptions` | `url`, `fileName?`, `background?`, `onProgress?`          |
| `DownloadResult`  | `success`, `filePath?`, `downloadId?`, `error?`           |
| `ActionResult`    | `success`, `error?`                                       |
| `getBackgroundDownloads` | Returns `success`, `downloads?` (list of active tasks), `error?` |
| `CachedFile`      | `fileName`, `filePath`, `size` (bytes), `modifiedAt` (ms) |
| `CacheResult`     | `success`, `files?`, `error?`                             |

---

## 🔗 Links

- [GitHub](https://github.com/chavanRk/react-native-downloader)
- [npm](https://www.npmjs.com/package/rn-downloader)


### Note:

- The server must allow direct downloads (no authentication, redirects etc).
- Large files and media are supported, including background and resumable downloads.
- DRM-protected or streaming-only URLs (like some video services) are not supported.

---

_Made natively for the community 🤝_
