package com.softmotions.ncms.io;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;

import java.io.BufferedInputStream;
import java.io.IOException;

/**
 * Simple tika-based mime type detector.
 *
 * @author Adamansky Anton (adamansky@gmail.com)
 */
public class MimeTypeDetector {

    private MimeTypeDetector() {
    }

    private static final TikaConfig TIKA_DEFAULT_CFG = TikaConfig.getDefaultConfig();

    public static MediaType detect(BufferedInputStream bis,
                                   String resourceName,
                                   String ctype,
                                   String enc) throws IOException {
        AutoDetectParser parser = new AutoDetectParser(TIKA_DEFAULT_CFG);
        Detector detector = parser.getDetector();
        Metadata md = new Metadata();
        if (resourceName != null) {
            md.add(Metadata.RESOURCE_NAME_KEY, resourceName);
        }
        if (ctype != null) {
            md.add(Metadata.CONTENT_TYPE, ctype);
        }
        if (enc != null) {
            md.add(Metadata.CONTENT_ENCODING, enc);
        }
        return detector.detect(bis, md);
    }
}
