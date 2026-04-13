import { NativeEventEmitter, NativeModules } from 'react-native';
import DownloaderSpec from './NativeDownloader';

const DownloaderModule = NativeModules.Downloader || DownloaderSpec;
const eventEmitter = new NativeEventEmitter(DownloaderModule);

// ─── Types ────────────────────────────────────────────────────────────────────

export interface DownloadOptions {
  /** Remote URL to download from */
  url: string;
  /** Optional file name. Auto-detected from URL if not provided */
  fileName?: string;
  /**
   * Run as a background download.
   * - iOS: uses NSURLSession background configuration
   * - Android: uses system DownloadManager
   * Background downloads survive app suspension. Listen to `onDownloadComplete`
   * and `onDownloadError` events instead of awaiting the promise.
   */
  background?: boolean;
  /** Custom request headers (e.g. Authorization) */
  headers?: Record<string, string>;
  /**
   * Destination directory for the downloaded file.
   * - 'downloads': Public Downloads folder (default)
   * - 'cache': App-private cache directory (cleared by OS when space is low)
   * - 'documents': App-private documents directory (persisted)
   */
  destination?: 'downloads' | 'cache' | 'documents';
  /** Android only: custom title for the system download notification */
  notificationTitle?: string;
  /** Android only: custom description for the system download notification */
  notificationDescription?: string;
  /** Optional checksum verification after download completes */
  checksum?: {
    hash: string;
    algorithm: 'md5' | 'sha1' | 'sha256';
  };
  /** Called with progress 0–100 during foreground downloads */
  onProgress?: (percent: number) => void;
}

export interface UploadOptions {
  /** Remote URL to upload to */
  url: string;
  /** Absolute local path of the file to upload */
  filePath: string;
  /** Multi-part field name for the file (default: 'file') */
  fieldName?: string;
  /** Custom request headers */
  headers?: Record<string, string>;
  /** Additional text parameters for the multi-part request */
  parameters?: Record<string, string>;
  /** Called with progress 0–100 during upload */
  onProgress?: (percent: number) => void;
}

export interface DownloadResult {
  success: boolean;
  /** Local path of the saved file */
  filePath?: string;
  /** Unique ID for this download — use with pause/resume/cancel */
  downloadId?: string;
  /** Error message if success is false */
  error?: string;
}

export interface UploadResult {
  success: boolean;
  /** HTTP response status code */
  status?: number;
  /** HTTP response body as string (if any) */
  data?: string;
  /** Error message if success is false */
  error?: string;
}

export interface ActionResult {
  success: boolean;
  error?: string;
}

export interface CachedFile {
  fileName: string;
  filePath: string;
  /** File size in bytes */
  size: number;
  /** Last-modified timestamp in milliseconds */
  modifiedAt: number;
}

export interface CacheResult {
  success: boolean;
  files?: CachedFile[];
  error?: string;
}

// ─── Core download ────────────────────────────────────────────────────────────

/**
 * Download a file.
 *
 * For **foreground** downloads the promise resolves when the file is saved.
 * For **background** downloads the promise resolves immediately with a
 * `downloadId`; listen to the `onDownloadComplete` / `onDownloadError`
 * events for the final result.
 */
export async function download(
  options: DownloadOptions
): Promise<DownloadResult> {
  let progressSubscription: ReturnType<typeof eventEmitter.addListener> | null =
    null;

  if (options.onProgress) {
    progressSubscription = eventEmitter.addListener(
      'onDownloadProgress',
      (event: any) => {
        if (event.url === options.url && options.onProgress) {
          options.onProgress(event.progress);
        }
      }
    );
  }

  try {
    const result = await (DownloaderSpec as any).download({
      url: options.url,
      fileName: options.fileName,
      background: options.background ?? false,
      headers: options.headers ?? {},
      destination: options.destination ?? 'downloads',
      notificationTitle: options.notificationTitle,
      notificationDescription: options.notificationDescription,
      checksum: options.checksum,
    });

    if (progressSubscription) {
      progressSubscription.remove();
    }

    return result as DownloadResult;
  } catch (error: any) {
    if (progressSubscription) {
      progressSubscription.remove();
    }
    return { success: false, error: error?.message || 'UNKNOWN_ERROR' };
  }
}

// ─── Core upload ──────────────────────────────────────────────────────────────

/**
 * Upload a file using multipart/form-data.
 */
export async function upload(options: UploadOptions): Promise<UploadResult> {
  let progressSubscription: ReturnType<typeof eventEmitter.addListener> | null =
    null;

  if (options.onProgress) {
    progressSubscription = eventEmitter.addListener(
      'onUploadProgress',
      (event: any) => {
        if (event.url === options.url && options.onProgress) {
          options.onProgress(event.progress);
        }
      }
    );
  }

  try {
    const result = await (DownloaderSpec as any).upload({
      url: options.url,
      filePath: options.filePath,
      fieldName: options.fieldName ?? 'file',
      headers: options.headers ?? {},
      parameters: options.parameters ?? {},
    });

    if (progressSubscription) {
      progressSubscription.remove();
    }

    return result as UploadResult;
  } catch (error: any) {
    if (progressSubscription) {
      progressSubscription.remove();
    }
    return { success: false, error: error?.message || 'UNKNOWN_ERROR' };
  }
}

// ─── Pause / Resume / Cancel ──────────────────────────────────────────────────

/**
 * Pause an active foreground download.
 * On iOS the partial data is stored so it can be resumed.
 * On Android the download thread is suspended.
 */
export async function pauseDownload(downloadId: string): Promise<ActionResult> {
  try {
    return (await (DownloaderSpec as any).pauseDownload(
      downloadId
    )) as ActionResult;
  } catch (error: any) {
    return { success: false, error: error?.message || 'UNKNOWN_ERROR' };
  }
}

/**
 * Resume a previously paused download.
 * On iOS resumes from partial data (HTTP Range). On Android unblocks the thread.
 */
export async function resumeDownload(
  downloadId: string
): Promise<ActionResult> {
  try {
    return (await (DownloaderSpec as any).resumeDownload(
      downloadId
    )) as ActionResult;
  } catch (error: any) {
    return { success: false, error: error?.message || 'UNKNOWN_ERROR' };
  }
}

/**
 * Cancel and discard a download (foreground or background).
 * Any partially-downloaded file is deleted.
 */
export async function cancelDownload(
  downloadId: string
): Promise<ActionResult> {
  try {
    return (await (DownloaderSpec as any).cancelDownload(
      downloadId
    )) as ActionResult;
  } catch (error: any) {
    return { success: false, error: error?.message || 'UNKNOWN_ERROR' };
  }
}

// ─── Cache management ─────────────────────────────────────────────────────────

/**
 * List all files in the app's cache directory.
 */
export async function getCachedFiles(): Promise<CacheResult> {
  try {
    return (await (DownloaderSpec as any).getCachedFiles()) as CacheResult;
  } catch (error: any) {
    return { success: false, error: error?.message || 'UNKNOWN_ERROR' };
  }
}

/**
 * Delete a single file by its absolute path.
 */
export async function deleteFile(filePath: string): Promise<ActionResult> {
  try {
    return (await (DownloaderSpec as any).deleteFile(filePath)) as ActionResult;
  } catch (error: any) {
    return { success: false, error: error?.message || 'UNKNOWN_ERROR' };
  }
}

/**
 * Delete all files in the app's cache directory.
 */
export async function clearCache(): Promise<ActionResult> {
  try {
    return (await (DownloaderSpec as any).clearCache()) as ActionResult;
  } catch (error: any) {
    return { success: false, error: error?.message || 'UNKNOWN_ERROR' };
  }
}

/**
 * Get all active background downloads.
 * Use this after app restart to "re-attach" to ongoing downloads.
 */
export async function getBackgroundDownloads(): Promise<{
  success: boolean;
  downloads?: Array<{
    downloadId: string;
    url: string;
    status: number;
    progress: number;
  }>;
  error?: string;
}> {
  try {
    return (await (DownloaderSpec as any).getBackgroundDownloads()) as any;
  } catch (error: any) {
    return { success: false, error: error?.message || 'UNKNOWN_ERROR' };
  }
}

// ─── Event helpers ────────────────────────────────────────────────────────────

/**
 * Subscribe to background download completion events.
 * Returns an unsubscribe function.
 */
export function onDownloadComplete(
  callback: (result: DownloadResult) => void
): () => void {
  const sub = eventEmitter.addListener('onDownloadComplete', callback as any);
  return () => sub.remove();
}

/**
 * Subscribe to background download error events.
 * Returns an unsubscribe function.
 */
export function onDownloadError(
  callback: (result: DownloadResult) => void
): () => void {
  const sub = eventEmitter.addListener('onDownloadError', callback as any);
  return () => sub.remove();
}

/**
 * Subscribe to upload progress events.
 * Returns an unsubscribe function.
 */
export function onUploadProgress(
  callback: (result: { url: string; progress: number }) => void
): () => void {
  const sub = eventEmitter.addListener('onUploadProgress', callback as any);
  return () => sub.remove();
}

export default { download, upload };
