package latexcompiler.sync;

import java.nio.file.Path;

public final class DriveSyncService {
    public SyncStatus status() {
        return SyncStatus.NOT_CONNECTED;
    }

    public void syncFile(Path file) {
        throw new UnsupportedOperationException("Google Drive sync is not connected yet.");
    }
}

