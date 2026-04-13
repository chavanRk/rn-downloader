import { TurboModuleRegistry, type TurboModule } from 'react-native';

export interface Spec extends TurboModule {
  download(options: Object): Promise<Object>;
  upload(options: Object): Promise<Object>;
  pauseDownload(downloadId: string): Promise<Object>;
  resumeDownload(downloadId: string): Promise<Object>;
  cancelDownload(downloadId: string): Promise<Object>;
  getCachedFiles(): Promise<Object>;
  deleteFile(filePath: string): Promise<Object>;
  clearCache(): Promise<Object>;
  getBackgroundDownloads(): Promise<Object>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Downloader');
