package com.softmotions.ncms.media.events;

import static com.softmotions.ncms.media.MediaRS.normalizeFolder;
import static com.softmotions.ncms.media.MediaRS.normalizePath;

import com.softmotions.ncms.events.BasicEvent;

/**
 * Fired if media item was removed.
 *
 * @author Adamansky Anton (adamansky@gmail.com)
 */
public class MediaDeleteEvent extends BasicEvent {

    final boolean isFolder;

    final String path;

    public MediaDeleteEvent(Object source, boolean isFolder, String path) {
        super(source, MediaDeleteEvent.class.getSimpleName());
        this.path = isFolder ? normalizeFolder(path) : normalizePath(path);
        this.isFolder = isFolder;
    }

    public boolean isFolder() {
        return isFolder;
    }

    public String getPath() {
        return path;
    }
}