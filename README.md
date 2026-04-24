# rn-downloader

[![npm version](https://img.shields.io/npm/v/rn-downloader.svg?style=flat-square)](https://www.npmjs.com/package/rn-downloader)
[![npm downloads](https://img.shields.io/npm/dm/rn-downloader.svg?style=flat-square)](https://www.npmjs.com/package/rn-downloader)
[![license](https://img.shields.io/npm/l/rn-downloader.svg?style=flat-square)](https://github.com/chavan-labs/rn-downloader/blob/main/LICENSE)
[![TypeScript](https://img.shields.io/badge/TypeScript-Ready-blue.svg?style=flat-square)](https://www.typescriptlang.org/)
[![platforms](https://img.shields.io/badge/platforms-iOS%20%7C%20Android-lightgrey.svg?style=flat-square)](https://github.com/chavan-labs/rn-downloader)

The easiest way to download files in React Native — with background support, pause/resume, upload, and cache management built-in.

> 100% pure native code (Kotlin + Swift). Zero third-party dependencies.

**Keywords:** react native download, react native file download, react native background download, react native download manager, react native file upload, react native download progress, react native pause resume download, react native cache manager, react native turbo module, expo download, iOS URLSession, Android DownloadManager, react native checksum validation, base64 converter, url to base64, data uri, share file, open file, file sharing, native share, document viewer

⭐ **Star this repo if you found it useful** — it helps others discover the project!

## Problems?

Most React Native file download solutions have one or more of these problems:

- **Complexity** — require configuring native modules, background tasks, and notification channels separately
- **Dependencies** — rely on heavy third-party libraries that bloat your app size
- **Limited features** — lack pause/resume, checksum validation, or proper background support
- **Poor DX** — complicated APIs that require managing multiple IDs, listeners, and cleanup logic

**rn-downloader** was built to solve these issues. It provides a simple, unified API while leveraging platform-native download managers (URLSession on iOS, DownloadManager on Android) for reliable, battery-efficient downloads. Everything works out of the box — background downloads, progress tracking, pause/resume — without wrestling with native configuration.

## ✨ Features

- **Download with progress** — clean `0 → 100` progress natively, no UI freezing
- **Background downloads** — survive app suspension (iOS background URLSession + Android DownloadManager)
- **Pause & Resume** — resume mid-download using HTTP Range requests
- **Auto-Retry** — automatic retry on network errors with exponential backoff
- **Cancel** — cancel any active download, partial files are cleaned up automatically
- **Re-attach** — reconnect to background downloads after app restart
- **Custom Headers** — support for Authorization tokens and custom metadata
- **Custom Destinations** — save to `downloads`, `cache`, or `documents` folders
- **Multipart Upload** — simple, native file uploading
- **Checksum Validation** — verify file integrity (MD5, SHA1, SHA256) after download
- **Base64 & Data URI Support** — convert base64 strings and data URIs to files natively
- **URL to Base64** — convert remote URLs (images, videos, gifs) to base64 strings
- **Share Files** — share files with other apps using native share dialog
- **Open Files** — open files with default apps or app chooser
- **Expo Support** — includes a config plugin for zero-config integration
- **TurboModules** — built on the React Native New Architecture
- **File management** — list, delete individual files, or clear all downloads

---

## Installation

```sh
npm install rn-downloader
```

---

## API

### `download(options)`

```javascript
import { download } from 'rn-downloader';

const result = await download({
  url: 'https://example.com/file.pdf',
  fileName: 'my_file.pdf',
  headers: { Authorization: 'Bearer <token>' },
  destination: 'documents', // 'downloads' | 'cache' | 'documents'
  checksum: {
    hash: 'd41d8cd98f00b204e9800998ecf8427e',
    algorithm: 'md5',
  },
  onProgress: (p) => console.log(`${p}%`),
  // Auto-retry on network failure (optional)
  retry: {
    attempts: 3, // max retry attempts
    delay: 1000, // base delay in ms; doubles each attempt: 1s → 2s → 4s (capped at 30s)
    onRetry: (attempt, error) => console.log(`Retry #${attempt}: ${error}`),
  },
});
```

> **Retry behaviour:**
>
> - Only retries on **network errors** (timeouts, connection drops, socket errors).
> - Server errors (`4xx`/`5xx`) and checksum mismatches are **not** retried.
> - Delay doubles each attempt (exponential backoff), capped at 30 seconds.
> - Works on both iOS and Android for foreground downloads.

---

### `upload(options)`

```javascript
import { upload } from 'rn-downloader';

const result = await upload({
  url: 'https://example.com/api/upload',
  filePath: '/path/to/my_image.jpg',
  fieldName: 'avatar', // default: 'file'
  parameters: {
    userId: '123',
  },
  headers: { 'X-Custom-Header': 'value' },
  onProgress: (p) => console.log(`Uploading: ${p}%`),
});

if (result.success) {
  console.log('Response:', result.data);
}
```

---

### `saveBase64AsFile(options)`

Save a base64 string or data URI as a file. Perfect for handling base64 images, documents, or any binary data.

```javascript
import { saveBase64AsFile } from 'rn-downloader';

// From data URI (auto-detects file extension)
const result = await saveBase64AsFile({
  base64Data: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUg...',
  fileName: 'photo.png',
  destination: 'documents', // 'downloads' | 'cache' | 'documents'
});

// From plain base64 string
const result = await saveBase64AsFile({
  base64Data: 'SGVsbG8gV29ybGQ=',
  fileName: 'hello.txt',
  destination: 'cache',
});

if (result.success) {
  console.log('File saved at:', result.filePath);
}
```

---

### `urlToBase64(options)`

Convert a web URL (image, video, gif, etc.) to base64 string. Perfect for converting remote media to base64 for local processing.

```javascript
import { urlToBase64 } from 'rn-downloader';

const result = await urlToBase64({
  url: 'https://example.com/photo.jpg',
  headers: { Authorization: 'Bearer <token>' }, // optional
});

if (result.success) {
  console.log('Base64:', result.base64);
  console.log('Data URI:', result.dataUri);
  console.log('MIME Type:', result.mimeType); // e.g., 'image/jpeg'

  // Use in Image component
  // <Image source={{ uri: result.dataUri }} />
}
```

---

### `shareFile(options)`

Share a file with other apps using the native share dialog.

```javascript
import { shareFile } from 'rn-downloader';

const result = await shareFile({
  filePath: '/path/to/document.pdf',
  title: 'Share Document', // Android only
  subject: 'Check this out', // Android only
});

if (result.success) {
  console.log('File shared successfully');
}
```

---

### `openFile(options)`

Open a file with the default app or app chooser.

```javascript
import { openFile } from 'rn-downloader';

const result = await openFile({
  filePath: '/path/to/document.pdf',
  mimeType: 'application/pdf', // optional, auto-detected if not provided
});

if (result.success) {
  console.log('File opened');
}
```

---

### Pause / Resume / Cancel

```javascript
import { pauseDownload, resumeDownload, cancelDownload } from 'rn-downloader';

await pauseDownload(downloadId);
await resumeDownload(downloadId);
await cancelDownload(downloadId);
```

---

### Cache Management

```javascript
import { getCachedFiles, deleteFile, clearCache } from 'rn-downloader';

// List all files in the cache/documents folders
const { files } = await getCachedFiles();

// Delete a specific file
await deleteFile('/path/to/file.pdf');

// Clear all managed files
await clearCache();
```

---

## Use Cases

### Download from URLs

Perfect for downloading files from remote servers with progress tracking, background support, and resume capability.

### Convert URLs to Base64

Convert remote media (images, videos, gifs) to base64 strings for:

- Display in Image components without downloading to disk
- Inline embedding in HTML/emails
- Upload to APIs requiring base64 format
- Cross-platform data transfer

### Save Base64/Data URIs

Convert base64-encoded data (like images from canvas, camera, or API responses) directly to files:

- Canvas-generated images (`canvas.toDataURL()`)
- Camera/photo library base64 outputs
- API responses with base64-encoded files
- Email attachments in base64 format

---

## Type Reference

| Type                 | Fields                                                                                                                    |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `DownloadOptions`    | `url`, `fileName?`, `background?`, `headers?`, `destination?`, `notificationTitle?`, `checksum?`, `onProgress?`, `retry?` |
| `RetryOptions`       | `attempts`, `delay?`, `onRetry?`                                                                                          |
| `UploadOptions`      | `url`, `filePath`, `fieldName?`, `headers?`, `parameters?`, `onProgress?`                                                 |
| `SaveBase64Options`  | `base64Data`, `fileName?`, `destination?`                                                                                 |
| `UrlToBase64Options` | `url`, `headers?`                                                                                                         |
| `ShareFileOptions`   | `filePath`, `title?`, `subject?`                                                                                          |
| `OpenFileOptions`    | `filePath`, `mimeType?`                                                                                                   |
| `DownloadResult`     | `success`, `filePath?`, `downloadId?`, `error?`                                                                           |
| `UploadResult`       | `success`, `status?`, `data?`, `error?`                                                                                   |
| `SaveBase64Result`   | `success`, `filePath?`, `error?`                                                                                          |
| `UrlToBase64Result`  | `success`, `base64?`, `mimeType?`, `dataUri?`, `error?`                                                                   |
| `ShareFileResult`    | `success`, `completed?`, `error?`                                                                                         |
| `OpenFileResult`     | `success`, `error?`                                                                                                       |
| `Checksum`           | `hash`, `algorithm: 'md5' \| 'sha1' \| 'sha256'`                                                                          |

---

## Articles & Resources

- [**Why Downloading Files in React Native is Still Broken in 2026 (and How to Fix It)**](https://medium.com/@chavanrohit413/why-downloading-files-in-react-native-is-still-broken-in-2026-and-how-to-fix-it-16ca47a6bd8b) — Deep dive into the problems with existing solutions and how rn-downloader solves them

---

## Links

- [GitHub](https://github.com/chavan-labs/rn-downloader)
- [npm](https://www.npmjs.com/package/rn-downloader)

---

_Made natively for the community 🤝 by Rohit Chavan_
