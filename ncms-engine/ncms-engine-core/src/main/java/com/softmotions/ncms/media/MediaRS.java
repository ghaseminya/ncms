package com.softmotions.ncms.media;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Blob;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.collections.map.LRUMap;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.imgscalr.Scalr;
import org.mybatis.guice.transactional.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.softmotions.commons.cont.ArrayUtils;
import com.softmotions.commons.cont.KVOptions;
import com.softmotions.commons.cont.Pair;
import com.softmotions.commons.cont.TinyParamMap;
import com.softmotions.commons.ctype.CTypeUtils;
import com.softmotions.commons.io.DirUtils;
import com.softmotions.commons.io.scanner.DirectoryScanner;
import com.softmotions.commons.io.scanner.DirectoryScannerFactory;
import com.softmotions.commons.io.scanner.DirectoryScannerVisitor;
import com.softmotions.commons.io.watcher.FSWatcher;
import com.softmotions.commons.io.watcher.FSWatcherCollectEventHandler;
import com.softmotions.commons.io.watcher.FSWatcherCreateEvent;
import com.softmotions.commons.io.watcher.FSWatcherDeleteEvent;
import com.softmotions.commons.io.watcher.FSWatcherEventHandler;
import com.softmotions.commons.io.watcher.FSWatcherEventSupport;
import com.softmotions.commons.io.watcher.FSWatcherModifyEvent;
import com.softmotions.commons.io.watcher.FSWatcherRegisterEvent;
import com.softmotions.commons.lifecycle.Dispose;
import com.softmotions.ncms.NcmsEnvironment;
import com.softmotions.ncms.NcmsMessages;
import com.softmotions.ncms.asm.events.AsmRemovedEvent;
import com.softmotions.ncms.events.EnsureResizedImageJobEvent;
import com.softmotions.ncms.events.NcmsEventBus;
import com.softmotions.ncms.fts.FTSUtils;
import com.softmotions.ncms.io.MetadataDetector;
import com.softmotions.ncms.io.MimeTypeDetector;
import com.softmotions.ncms.jaxrs.BadRequestException;
import com.softmotions.ncms.jaxrs.NcmsMessageException;
import com.softmotions.ncms.media.events.MediaDeleteEvent;
import com.softmotions.ncms.media.events.MediaMoveEvent;
import com.softmotions.ncms.media.events.MediaUpdateEvent;
import com.softmotions.web.ResponseUtils;
import com.softmotions.web.security.WSUser;
import com.softmotions.web.security.WSUserDatabase;
import com.softmotions.weboot.mb.MBCriteriaQuery;
import com.softmotions.weboot.mb.MBDAOSupport;

/**
 * Media files manager rest service.
 *
 * @author Adamansky Anton (adamansky@gmail.com)
 */
@SuppressWarnings("unchecked")
@javax.ws.rs.Path("/media")
@Produces("application/json;charset=UTF-8")
public class MediaRS extends MBDAOSupport implements MediaRepository, FSWatcherEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MediaRS.class);

    public static final String SIZE_CACHE_FOLDER = ".size_cache";

    private static final int MB = 1048576;

    private static final File[] EMPTY_FILES_ARRAY = new File[0];

    private static final int PUT_NO_KEYS = 1;

    private static final int PUT_SYSTEM = 1 << 1;

    final NcmsEnvironment env;

    final File basedir;

    final RWLocksLRUCache locksCache;

    final Map<Object, Map<String, Object>> metaCache;

    final ObjectMapper mapper;

    final NcmsMessages message;

    final NcmsEventBus ebus;

    final WSUserDatabase userdb;

    private final Executor resizer;

    @Inject
    public MediaRS(NcmsEnvironment env,
                   SqlSession sess,
                   ObjectMapper mapper,
                   NcmsMessages message,
                   NcmsEventBus ebus,
                   WSUserDatabase userdb) throws IOException {
        super(MediaRS.class, sess);
        this.env = env;
        HierarchicalConfiguration<ImmutableNode> xcfg = env.xcfg();
        String dir = xcfg.getString("media.basedir");
        if (dir == null) {
            throw new RuntimeException("Missing required configuration property: media.basedir");
        }
        this.basedir = new File(dir);
        DirUtils.ensureDir(basedir, true);
        this.locksCache = new RWLocksLRUCache(xcfg.getInt("media.locks-lrucache-size", 128));
        this.metaCache = new LRUMap(xcfg.getInt("media.meta-lrucache-size", 1024));
        this.mapper = mapper;
        this.message = message;
        this.ebus = ebus;
        this.userdb = userdb;
        this.ebus.register(this);
        this.resizer = Executors.newSingleThreadExecutor();
    }


    /**
     * Get media-entity path by specified ID
     *
     * @param id Media entry ID
     */
    @GET
    @javax.ws.rs.Path("/path/{id}")
    @Transactional
    public String path(@PathParam("id") Long id) {
        Map<String, ?> row = selectOne("selectEntityPathById", "id", id);
        if (row == null) {
            throw new NotFoundException("");
        }
        return String.valueOf(row.get("folder")) + row.get("name");
    }

    /**
     * Save uploaded file.
     * <p/>
     * Example:
     * curl --upload-file ./myfile.txt http://localhost:8080/ncms/rs/media/file/foo/bar/test.txt
     */
    @PUT
    @Consumes("*/*")
    @javax.ws.rs.Path("/file/{folder:.*}/{name}")
    @Transactional(executorType = ExecutorType.SIMPLE)
    public void put(@PathParam("folder") String folder,
                    @PathParam("name") String name,
                    @Context HttpServletRequest req,
                    @Context HttpServletResponse resp,
                    InputStream in) throws Exception {
        _put(folder, name, req, resp, in, 0);
    }

    @PUT
    @Consumes("*/*")
    @javax.ws.rs.Path("/file/{name}")
    @Transactional(executorType = ExecutorType.SIMPLE)
    public void put(@PathParam("name") String name,
                    @Context HttpServletRequest req,
                    @Context HttpServletResponse resp,
                    InputStream in) throws Exception {
        _put("", name, req, resp, in, 0);
    }


    @GET
    @javax.ws.rs.Path("/file/{folder:.*}/{name}")
    @Transactional
    public Response get(@PathParam("folder") String folder,
                        @PathParam("name") String name,
                        @Context HttpServletRequest req,
                        @QueryParam("w") Integer width,
                        @QueryParam("h") Integer height) throws Exception {
        return _get(folder, name, req, width, height,
                    BooleanUtils.toBoolean(req.getParameter("inline")), true);
    }

    @GET
    @javax.ws.rs.Path("/file/{name}")
    @Transactional
    public Response get(@PathParam("name") String name,
                        @Context HttpServletRequest req,
                        @QueryParam("w") Integer width,
                        @QueryParam("h") Integer height) throws Exception {
        return _get("", name, req, width, height,
                    BooleanUtils.toBoolean(req.getParameter("inline")), true);
    }


    @GET
    @javax.ws.rs.Path("/fileid/{id}")
    @Transactional
    public Response get(@PathParam("id") Long id,
                        @Context HttpServletRequest req,
                        @QueryParam("w") Integer width,
                        @QueryParam("h") Integer height) throws Exception {
        return _get(id, req, width, height, BooleanUtils.toBoolean(req.getParameter("inline")));
    }


    @HEAD
    @javax.ws.rs.Path("/fileid/{id}")
    @Transactional
    public Response head(@PathParam("id") Long id,
                         @Context HttpServletRequest req,
                         @QueryParam("w") Integer width,
                         @QueryParam("h") Integer height) throws Exception {
        Map<String, ?> row = selectOne("selectEntityPathById", "id", id);
        if (row == null) {
            throw new NotFoundException("");
        }
        return _get((String) row.get("folder"), (String) row.get("name"),
                    req, width, height,
                    BooleanUtils.toBoolean(req.getParameter("inline")), false);
    }

    @HEAD
    @javax.ws.rs.Path("/file/{folder:.*}/{name}")
    @Transactional
    public Response head(@PathParam("folder") String folder,
                         @PathParam("name") String name,
                         @Context HttpServletRequest req,
                         @QueryParam("w") Integer width,
                         @QueryParam("h") Integer height) throws Exception {
        return _get(folder, name, req, width, height,
                    BooleanUtils.toBoolean(req.getParameter("inline")), false);
    }

    @HEAD
    @javax.ws.rs.Path("/file/{name}")
    @Transactional
    public Response head(@PathParam("name") String name,
                         @Context HttpServletRequest req,
                         @QueryParam("w") Integer width,
                         @QueryParam("h") Integer height) throws Exception {
        return _get("", name, req, width, height,
                    BooleanUtils.toBoolean(req.getParameter("inline")), false);
    }

    @GET
    @javax.ws.rs.Path("/thumbnail2/{id}")
    @Transactional
    public Response thumbnail(@PathParam("id") Long id,
                              @Context HttpServletRequest req) throws Exception {
        return _thumbnail(id, null, null, req);
    }


    @GET
    @javax.ws.rs.Path("/thumbnail/{folder:.*}/{name}")
    @Transactional
    public Response thumbnail(@PathParam("folder") String folder,
                              @PathParam("name") String name,
                              @Context HttpServletRequest req) throws Exception {
        return _thumbnail(null, folder, name, req);
    }

    @GET
    @javax.ws.rs.Path("/thumbnail/{name}")
    @Transactional
    public Response thumbnail(@PathParam("name") String name,
                              @Context HttpServletRequest req) throws Exception {
        return _thumbnail(null, "", name, req);
    }


    @GET
    @javax.ws.rs.Path("/files/{folder:.*}")
    @Transactional
    public JsonNode listFiles(@PathParam("folder") String folder,
                              @Context HttpServletRequest req) throws IOException {
        return _list(folder, FileFileFilter.FILE, req);
    }

    @GET
    @javax.ws.rs.Path("/files")
    @Transactional
    public JsonNode listFiles(@Context HttpServletRequest req) throws IOException {
        return _list("", FileFileFilter.FILE, req);
    }

    @GET
    @javax.ws.rs.Path("/folders/{folder:.*}")
    @Transactional
    public JsonNode listFolders(@PathParam("folder") String folder,
                                @Context HttpServletRequest req) throws IOException {
        return _list(folder, DirectoryFileFilter.INSTANCE, req);
    }

    @GET
    @javax.ws.rs.Path("/folders")
    @Transactional
    public JsonNode listFolders(@Context HttpServletRequest req) throws IOException {
        return _list("", DirectoryFileFilter.INSTANCE, req);
    }


    @GET
    @javax.ws.rs.Path("/all/{folder:.*}")
    @Transactional
    public JsonNode listAll(@PathParam("folder") String folder,
                            @Context HttpServletRequest req) throws IOException {
        return _list(folder, TrueFileFilter.INSTANCE, req);
    }

    @GET
    @javax.ws.rs.Path("/all")
    @Transactional
    public JsonNode listAll(@Context HttpServletRequest req) throws IOException {
        return _list("", TrueFileFilter.INSTANCE, req);
    }

    @GET
    @javax.ws.rs.Path("/select")
    @Transactional
    public Response select(@Context final HttpServletRequest req) {
        return Response.ok((StreamingOutput) output -> {
            final JsonGenerator gen = new JsonFactory().createGenerator(output);
            try {
                MBCriteriaQuery cq = createSelectQ(req, false);
                gen.writeStartArray();
                //noinspection InnerClassTooDeeplyNested
                select(cq.getStatement(), context -> {
                    Map<String, ?> row = (Map<String, ?>) context.getResultObject();
                    try {
                        gen.writeStartObject();
                        gen.writeNumberField("id", ((Number) row.get("id")).longValue());
                        gen.writeStringField("name", (String) row.get("name"));
                        gen.writeStringField("folder", (String) row.get("folder"));
                        gen.writeStringField("content_type", (String) row.get("content_type"));
                        gen.writeStringField("owner", (String) row.get("owner"));
                        String username = (String) row.get("owner");
                        WSUser user = (username != null) ? userdb.findUser(username) : null;
                        if (user != null) {
                            gen.writeStringField("owner_fullName", user.getFullName());
                        }
                        if (row.get("content_length") != null) {
                            gen.writeNumberField("content_length", ((Number) row.get("content_length")).longValue());
                        }
                        gen.writeStringField("description", (String) row.get("description"));
                        gen.writeStringField("tags", row.get("tags") != null ? row.get("tags").toString() : null);
                        gen.writeEndObject();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, cq);
            } finally {
                gen.writeEndArray();
            }
            gen.flush();
        }).type("application/json;charset=UTF-8").build();
    }

    @GET
    @javax.ws.rs.Path("/select/count")
    @Produces("text/plain")
    @Transactional
    public Integer selectCount(@Context HttpServletRequest req) {
        MBCriteriaQuery cq = createSelectQ(req, true);
        return selectOne(cq.getStatement(), cq);
    }

    @PUT
    @javax.ws.rs.Path("/folder/{folder:.*}")
    @Transactional
    public JsonNode newFolder(@PathParam("folder") String folder,
                              @Context HttpServletRequest req,
                              @Context HttpServletResponse resp) throws Exception {

        ensureAuthenticated(req, resp);

        try (final ResourceLock l = new ResourceLock(folder, true)) {
            File f = new File(basedir, folder);
            if (!f.exists()) {
                if (!f.mkdirs()) {
                    throw new IOException("Cannot create dir: " + folder);
                }
            }
            String name = f.getName();
            String dirname = getResourceParentFolder(folder);
            Number id = selectOne("selectEntityIdByPath",
                                  "folder", dirname,
                                  "name", name);
            if (id == null) {
                Map params = new HashMap();
                params.put("folder", dirname);
                params.put("name", name);
                params.put("owner", req.getRemoteUser());
                params.put("status", 1);
                params.put("system", 0);
                insert("insertEntity", params);

                id = (Number) params.get("id");
                ebus.fireOnSuccessCommit(
                        new MediaUpdateEvent(this, true, id, dirname + name));
            } else {
                throw new NcmsMessageException(message.get("ncms.mmgr.folder.exists", req, folder), true);
            }
            return mapper.createObjectNode()
                         .put("label", name)
                         .put("status", 1)
                         .put("system", isInSystemFolder(dirname + name));
        }
    }

    @PUT
    @javax.ws.rs.Path("/copy-batch/{target:.*}")
    @Transactional
    public void copy(@Context HttpServletRequest req,
                     @Context HttpServletResponse resp,
                     @PathParam("target") String target,
                     ArrayNode files) throws Exception {
        _copy(req, resp, target, files);
    }

    @PUT
    @javax.ws.rs.Path("/copy-batch")
    @Transactional
    public void copy(@Context HttpServletRequest req,
                     @Context HttpServletResponse resp,
                     ArrayNode files) throws Exception {
        _copy(req, resp, "", files);
    }

    private void _copy(HttpServletRequest req, HttpServletResponse resp,
                       String tfolder, ArrayNode files) throws Exception {

        ensureAuthenticated(req, resp);

        checkFolder(tfolder);
        tfolder = normalizeFolder(tfolder);
        checkEditAccess(tfolder, req);

        for (int i = 0, l = files.size(); i < l; ++i) {
            String spath = normalizePath(files.get(i).asText());
            checkFolder(spath);
            try (final ResourceLock l1 = new ResourceLock(spath, false)) {
                String sfolder = getResourceParentFolder(spath);
                String sname = getResourceName(spath);
                String tpath = tfolder + sname;
                if (spath.equals(tpath)) {
                    continue;
                }
                File sfile = new File(basedir, spath);
                if (!sfile.exists()) {
                    continue;
                }

                try (final ResourceLock l2 = new ResourceLock(tpath, true)) {
                    File tfile = new File(basedir, tpath);
                    Map<String, Object> row = selectOne("selectResourceAttrsByPath",
                                                        "folder", sfolder,
                                                        "name", sname);
                    if (row == null) {
                        log.error("File to be copied: {} is missing in DB", spath);
                        continue;
                    }

                    FileUtils.copyFile(sfile, tfile);

                    row.put("folder", tfolder);
                    row.put("owner", req.getRemoteUser());
                    row.remove("id");

                    delete("deleteFile",
                           "folder", tfolder,
                           "name", sname);

                    insert("insertEntity", row);

                    if (row.get("id") != null) {
                        updateFTSKeywords(((Number) row.get("id")).longValue(), req);
                    }

                } catch (IOException e) {
                    log.error("Failed to copy {} => {}", spath, tpath, e);
                    throw e;
                }
            }
        }
    }

    @PUT
    @javax.ws.rs.Path("/move/{path:.*}")
    @Transactional(executorType = ExecutorType.BATCH)
    public void move(@PathParam("path") String path,
                     @Context HttpServletRequest req,
                     @Context HttpServletResponse resp,
                     String npath) throws Exception {

        ensureAuthenticated(req, resp);

        path = StringUtils.strip(path, "/");
        npath = StringUtils.strip(npath, "/");
        if (StringUtils.isBlank(npath)) {
            throw new BadRequestException("");
        }
        if (npath.equals(path)) {
            return;
        }

        checkFolder(path);
        checkFolder(npath);

        Long id;

        try (final ResourceLock l1 = new ResourceLock(path, true)) {
            try (final ResourceLock l2 = new ResourceLock(npath, true)) {
                File f1 = new File(basedir, path);
                if (!f1.exists()) {
                    throw new NotFoundException(path);
                }

                checkEditAccess(path, req);

                File f2 = new File(basedir, npath);
                if (f2.exists()) {
                    throw new NcmsMessageException(message.get("ncms.mmgr.file.exists", req, npath), true);
                }
                File pf = f2.getParentFile();
                if (pf != null && !pf.exists() && !pf.mkdirs()) {
                    throw new IOException("Cannot create the target directory");
                }
                if (log.isDebugEnabled()) {
                    log.debug("Moving {} => {}", f1, f2);
                }
                if (f1.isDirectory()) {

                    String p1 = f1.getCanonicalPath();
                    String p2 = f2.getCanonicalPath();
                    if (p2.startsWith(p1 + '/')) {
                        String msg = message.get("ncms.mmgr.folder.cantMoveIntoSubfolder", req, path, npath);
                        throw new NcmsMessageException(msg, true);
                    }
                    String like = '/' + path + "/%";
                    update("fixFolderName",
                           "new_prefix", '/' + npath + '/',
                           "prefix_like_len", like.length(),
                           "prefix_like", like);

                    String nname = getResourceName(npath);
                    String nfolder = getResourceParentFolder(npath);
                    update("fixResourceLocation",
                           "nfolder", nfolder,
                           "nname", nname,
                           "folder", getResourceParentFolder(path),
                           "name", getResourceName(path));

                    FileUtils.moveDirectory(f1, f2);

                    synchronized (metaCache) {
                        metaCache.clear();
                    }

                    ebus.fireOnSuccessCommit(new MediaMoveEvent(this, null, true, path, npath));

                } else if (f1.isFile()) {

                    String name = getResourceName(path);
                    String folder = getResourceParentFolder(path);
                    String nname = getResourceName(npath);
                    String nfolder = getResourceParentFolder(npath);
                    update("fixResourceLocation",
                           "nfolder", nfolder,
                           "nname", nname,
                           "folder", folder,
                           "name", name);

                    FileUtils.moveFile(f1, f2);

                    id = selectOne("selectEntityIdByPath",
                                   "name", nname,
                                   "folder", nfolder);

                    synchronized (metaCache) {
                        metaCache.remove(folder + name);
                        if (id != null) {
                            metaCache.remove(id);
                        }
                    }

                    if (id != null) {
                        //Handle resize image dir
                        File rdir = getResizedImageDir(folder, id);
                        if (rdir.exists() && !folder.equals(nfolder)) {
                            File nrdir = getResizedImageDir(nfolder, id);
                            File pnrdir = nrdir.getParentFile();
                            if (pnrdir != null && (pnrdir.exists() || pnrdir.mkdirs())) {
                                try {
                                    FileUtils.deleteDirectory(nrdir);
                                    FileUtils.moveDirectory(rdir, pnrdir);
                                } catch (IOException e) {
                                    log.error("Failed to move directory: {} to {}", rdir, nrdir, e);
                                }
                            }
                        }

                        ebus.fireOnSuccessCommit(new MediaMoveEvent(this, id, false, path, npath));
                        updateFTSKeywords(id, req);
                    }
                } else {
                    throw new IOException("Unsupported file type");
                }
            }
        }
    }

    @DELETE
    @javax.ws.rs.Path("/delete/{path:.*}")
    @Transactional(executorType = ExecutorType.BATCH)
    public void deleteResource(@PathParam("path") String path,
                               @Context HttpServletRequest req,
                               @Context HttpServletResponse resp) throws Exception {

        ensureAuthenticated(req, resp);

        path = StringUtils.strip(path, "/");
        checkFolder(path);
        if (log.isDebugEnabled()) {
            log.debug("deleteResource: {}", path);
        }

        boolean isdir;
        String name = getResourceName(path);
        String folder = getResourceParentFolder(path);

        try (final ResourceLock l = new ResourceLock(path, true)) {

            File f = new File(basedir, path);
            checkEditAccess(path, req);

            isdir = f.isDirectory();
            if (isdir) {

                deleteDirectoryInternal(path, true);
                delete("deleteFolder",
                       "prefix_like", '/' + path + "/%");
                delete("deleteFile",
                       "folder", folder,
                       "name", name);

                synchronized (metaCache) {
                    metaCache.clear();
                }

            } else {

                Long id = selectOne("selectEntityIdByPath",
                                    "folder", folder,
                                    "name", name);

                checkFileDeletion(id, req);

                boolean exists = f.exists();
                if (f.delete() || !exists) {
                    delete("deleteFile",
                           "folder", folder,
                           "name", name);
                } else {
                    throw new NotFoundException(message.get("ncms.mmgr.file.cannot.delete", req, path));
                }

                synchronized (metaCache) {
                    metaCache.remove(folder + name);
                    if (id != null) {
                        metaCache.remove(id);
                    }
                }

                if (id != null) {
                    File sdir = getResizedImageDir(folder, id);
                    if (sdir.exists()) {
                        try {
                            FileUtils.deleteDirectory(sdir);
                        } catch (IOException e) {
                            log.error("", e);
                        }
                    }
                }
            }
        }

        ebus.fireOnSuccessCommit(
                new MediaDeleteEvent(this, isdir, path)
        );
    }

    @DELETE
    @javax.ws.rs.Path("/delete-batch")
    public void deleteBatch(@Context HttpServletRequest req,
                            @Context HttpServletResponse resp,
                            ArrayNode files) throws Exception {
        for (int i = 0, l = files.size(); i < l; ++i) {
            String path = files.get(i).asText();
            deleteResource(path, req, resp);
        }
    }


    @GET
    @javax.ws.rs.Path("/meta/{id}")
    @Transactional
    public ObjectNode getMeta(@PathParam("id") Long id,
                              @Context HttpServletRequest req) throws Exception {
        Map<String, Object> meta = getCachedMeta(id);
        if (meta == null) {
            throw new NotFoundException("");
        }
        ObjectNode res = mapper.createObjectNode();
        res.put("id", id);
        res.put("folder", (String) meta.get("folder"));
        res.put("name", (String) meta.get("name"));
        res.put("meta", (String) meta.get("meta"));
        return res;
    }

    /**
     * Update some meta fields of files.
     */
    @POST
    @javax.ws.rs.Path("/meta/{id}")
    @Consumes("application/x-www-form-urlencoded")
    @Transactional(executorType = ExecutorType.BATCH)
    public void updateMeta(@PathParam("id") Long id,
                           @Context HttpServletRequest req,
                           MultivaluedMap<String, String> form) throws Exception {

        checkEditAccess(id, req);

        Map<String, Object> qm = new TinyParamMap();
        qm.put("id", id);

        if (form.containsKey("tags")) {
            final Collator coll = Collator.getInstance(message.getLocale(req));
            String tagStr = form.getFirst("tags");
            Set<String> tagSet = new HashSet<>();
            if (tagStr != null) {
                String[] tagArr = tagStr.split(",");
                for (String tag : tagArr) {
                    tag = tag.trim();
                    if (!tag.isEmpty()) {
                        tagSet.add(tag);
                    }
                }
            }
            int i = 0;
            Object[] qtags = new Object[tagSet.size()];
            for (final String tag : tagSet) {
                qtags[i++] = tag;
            }
            Arrays.sort(qtags, (o1, o2) -> coll.compare(String.valueOf(o1), String.valueOf(o2)));
            qm.put("tags", ArrayUtils.stringJoin(qtags, ", "));
        }
        if (form.containsKey("description")) {
            String desc = form.getFirst("description");
            qm.put("description", StringUtils.isBlank(desc) ? "" : desc);
        }
        if (form.containsKey("owner")) {
            String owner = form.getFirst("owner");
            if (StringUtils.isBlank(owner)) {
                throw new BadRequestException("");
            }
            qm.put("owner", owner);
        }
        // if qm.size() < 2: only .put(id) called - update query is empty!
        if (qm.size() > 1) {
            update("updateMeta", qm);
            updateFTSKeywords(id, req);
        }
    }

    @Override
    public MediaResource findMediaResource(Long id, Locale locale) {
        return findMediaResource("entity:" + id, locale);
    }

    @Override
    @Transactional
    public MediaResource findMediaResource(String path, Locale locale) {
        Map<String, Object> meta;
        if (path.startsWith("entity:")) {
            Long id;
            try {
                id = Long.parseLong(path.substring("entity:".length()));
            } catch (NumberFormatException e) {
                log.error("", e);
                return null;
            }
            meta = getCachedMeta(id);
        } else {
            meta = getCachedMeta(path);
        }
        if (meta == null) {
            return null;
        }

        final String folder = (String) meta.get("folder");
        final String name = (String) meta.get("name");
        final Date mdate = (Date) meta.get("mdate");
        final Number length = (Number) meta.get("content_length");
        final KVOptions kvmeta = new KVOptions((String) meta.get("meta"));

        return new MediaResourceImpl(this,
                                     ((Number) meta.get("id")).longValue(),
                                     (folder + name),
                                     (String) meta.get("content_type"),
                                     (mdate != null ? mdate.getTime() : 0),
                                     (length != null ? length.longValue() : -1L),
                                     locale,
                                     (String) meta.get("description"),
                                     kvmeta);
    }

    public File getBasedir() {
        return basedir;
    }


    private Map<String, Object> getCachedMeta(Long id) {
        Map<String, Object> res;
        synchronized (metaCache) {
            res = metaCache.get(id);
        }
        if (res != null) {
            return res;
        }
        res = selectOne("selectResourceAttrsById",
                        "id", id);
        if (res != null) {
            synchronized (metaCache) {
                metaCache.put(id, res);
            }
        }
        return res;
    }

    private Map<String, Object> getCachedMeta(String folder, String name) {
        Map<String, Object> res;
        folder = normalizeFolder(folder);
        String key = folder + name;
        synchronized (metaCache) {
            res = metaCache.get(key);
        }
        if (res != null) {
            return res;
        }
        res = selectOne("selectResourceAttrsByPath",
                        "folder", folder,
                        "name", name);
        if (res != null) {
            synchronized (metaCache) {
                metaCache.put(key, res);
            }
        }
        return res;
    }

    private Map<String, Object> getCachedMeta(String path) {
        return getCachedMeta(getResourceParentFolder(path), getResourceName(path));
    }


    /**
     * Update media item search tokens
     */
    private void updateFTSKeywords(Long id, HttpServletRequest req) throws Exception {
        Map<String, Object> row = selectOne("selectMeta", "id", id);
        if (row == null) {
            return;
        }
        Locale locale = message.getLocale(req);
        Set<String> keywords = new HashSet<>();
        String name = (String) row.get("name");
        String ctype = (String) row.get("content_type");
        String val;

        if (row.get("description") != null) {
            val = ((String) row.get("description")).toLowerCase();
            Collections.addAll(keywords, FTSUtils.stemWordsLangAware(val, locale, 3));
        }
        if (row.get("tags") != null) {
            Collections.addAll(keywords, FTSUtils.stemWordsLangAware((String) row.get("tags"), locale, 3));
        }
        val = FilenameUtils.getName(name);
        if (!StringUtils.isBlank(val) && val.length() > 2) {
            val = val.toLowerCase();
            Collections.addAll(keywords, FTSUtils.stemWordsLangAware(val, Locale.ENGLISH, 3));
        }
        val = FilenameUtils.getExtension(name);
        if (!StringUtils.isBlank(val) && val.length() > 2) {
            keywords.add(val.toLowerCase());
        }
        MediaType mtype = MediaType.parse(ctype);
        if (mtype != null) {
            mtype = mtype.getBaseType();
            keywords.add(mtype.getType());
            keywords.add(mtype.getSubtype());
        }
        delete("dropKeywords", "id", id);
        for (final String k : keywords) {
            if (k.length() > 24) { //VARCHAR(24)
                continue;
            }
            insert("insertKeyword",
                   "id", id,
                   "keyword", k);
        }
    }

    private boolean deleteDirectoryInternal(String path, boolean nolock) throws Exception {
        boolean res = true;
        ReentrantReadWriteLock rwlock = null;
        try {
            rwlock = nolock ? null : acquirePathRWLock(path, true);
            File f = new File(basedir, path);
            if (!f.exists() || !f.isDirectory()) {
                return true;
            }
            File[] flist = f.listFiles();
            if (flist == null) {
                return false;
            }
            for (final File sf : flist) {
                if (sf.isDirectory()) {
                    deleteDirectoryInternal(path + '/' + sf.getName(), false);
                } else {
                    boolean exists = sf.exists();
                    if (!sf.delete() && exists) {
                        log.error("Cannot to delete file: {}", sf.getAbsolutePath());
                    }
                }
            }
            if (!f.delete()) {
                log.error("Cannot delete directory: {}", f.getAbsolutePath());
                res = false;
            }
        } finally {
            if (rwlock != null) {
                rwlock.writeLock().unlock();
            }
        }
        return res;
    }

    private MBCriteriaQuery createSelectQ(HttpServletRequest req, boolean count) {
        Locale locale = message.getLocale(req);
        MBCriteriaQuery cq = createCriteria();
        String val;
        if (req.isUserInRole("admin")) {
            cq.withParam("system", true);
        }
        if (!count) {
            val = req.getParameter("firstRow");
            if (val != null) {
                Integer frow = Integer.valueOf(val);
                cq.offset(frow);
                val = req.getParameter("lastRow");
                if (val != null) {
                    Integer lrow = Integer.valueOf(val);
                    cq.limit(Math.abs(frow - lrow) + 1);
                }
            }
        }
        val = req.getParameter("status");
        if (!StringUtils.isBlank(val)) {
            cq.withParam("status", Integer.parseInt(val));
        }
        val = req.getParameter("folder");
        if (!StringUtils.isBlank(val)) {
            if (!val.endsWith("/")) {
                val += '/';
            }
            if (BooleanUtils.toBoolean(req.getParameter("subfolders"))) {
                val += '%';
            }
            cq.withParam("folder", val);
        }

        val = req.getParameter("nfolder");
        if (!StringUtils.isBlank(val)) {
            if (!val.endsWith("/")) {
                val += '/';
            }
            val += '%';
            cq.withParam("nfolder", val);
        } else if (!BooleanUtils.toBoolean(req.getParameter("inpages"))) {
            String folder = (String) cq.get("folder");
            if (folder == null || !folder.startsWith("/pages/")) {
                cq.withParam("nfolder", "/pages/%");
            }
        }

        val = req.getParameter("stext");
        if (!StringUtils.isBlank(val)) {
            val = val.toLowerCase();
            if (BooleanUtils.toBoolean(req.getParameter("fts"))) {
                String[] stemWords = FTSUtils.stemWordsLangAware(val, locale, 3);
                if (stemWords.length == 0) { //no keywords fetched fallback to plain query
                    val = val.toLowerCase(locale).trim() + '%';
                    cq.withParam("name", val);
                    cq.withStatement(count ? "count" : "select");
                } else {
                    for (int i = 0; i < stemWords.length; ++i) {
                        stemWords[i] += '%';
                    }
                    List<String> keywords = Arrays.asList(stemWords);
                    while (keywords.size() > 10) { //limit to 10 keywords
                        keywords.remove(keywords.size() - 1);
                    }
                    cq.withParam("keywords", keywords);
                    cq.withParam("keywordsSize", keywords.size());
                    cq.withStatement(count ? "countByKeywords" : "selectByKeywords");
                }
            } else {
                val = val.toLowerCase(locale).trim() + '%';
                cq.withParam("name", val);
                cq.withStatement(count ? "count" : "select");
            }
        } else {
            cq.withStatement(count ? "count" : "select");
        }

        if (!count) {
            val = req.getParameter("sortAsc");
            if (!StringUtils.isBlank(val)) {
                cq.orderBy("e." + val).asc();
            }
            val = req.getParameter("sortDesc");
            if (!StringUtils.isBlank(val)) {
                cq.orderBy("e." + val).desc();
            }
        }
        return cq.finish();
    }

    /**
     * GET list of files in the specified directory(folder).
     * <p/>
     * Produces the following JSON:
     * <p/>
     * <pre>
     *     [
     *       {
     *          "label" : file name,
     *          "status" : 1 if it is folder 0 otherwise,
     *          "system" : 1 if it is in system folder 0 otherwise
     *       },
     *       ...
     *     ]
     * </pre>
     */
    private JsonNode _list(String folder,
                           FileFilter filter,
                           HttpServletRequest req) throws IOException {
        checkFolder(folder);
        ArrayNode res = mapper.createArrayNode();
        ReentrantReadWriteLock rwlock = acquirePathRWLock(folder, false);
        try {
            File f = new File(basedir, folder);
            if (!f.exists()) {
                throw new NotFoundException(folder);
            }
            if (!f.isDirectory()) {
                return res;
            }
            final Collator collator = Collator.getInstance(message.getLocale(req));
            File[] files = f.listFiles(filter);
            if (files == null) files = EMPTY_FILES_ARRAY;
            Arrays.sort(files, (f1, f2) -> {
                int res1 = Integer.compare(f2.isDirectory() ? 1 : 0, f1.isDirectory() ? 1 : 0);
                if (res1 == 0) {
                    return collator.compare(f1.getName(), f2.getName());
                }
                return res1;
            });
            folder = normalizeFolder(folder);
            boolean parentInSystem = isInSystemFolder(folder);
            for (int i = 0, l = files.length; i < l; ++i) {
                File file = files[i];
                if (file.getName().charAt(0) == '.') {
                    continue;
                }
                boolean inSystem = parentInSystem || isInSystemFolder(folder + file.getName());
                if (file.isDirectory() && inSystem && !req.isUserInRole("admin")) {
                    continue;
                }
                res.addObject()
                   .put("label", file.getName())
                   .put("status", file.isDirectory() ? 1 : 0)
                   .put("system", inSystem ? 1 : 0);
            }
        } finally {
            rwlock.readLock().unlock();
        }
        return res;
    }

    private Response _thumbnail(Long id,
                                String folder,
                                String name,
                                HttpServletRequest req) throws Exception {

        Map<String, ?> rec;
        if (id != null) {
            rec = selectOne("selectIcon2", "id", id);
            if (rec == null) {
                throw new NotFoundException("");
            }
            folder = (String) rec.get("folder");
            name = (String) rec.get("name");

        } else {

            checkFolder(folder);
            folder = normalizeFolder(folder);
            rec = selectOne("selectIcon", "folder", folder, "name", name);
            if (rec == null) {
                throw new NotFoundException("");
            }
            id = ((Number) rec.get("id")).longValue();
        }

        String path = folder + name;
        HierarchicalConfiguration<ImmutableNode> xcfg = env.xcfg();
        int thumbWidth = xcfg.getInt("media.thumbnails-width", 255);
        String ctype = (String) rec.get("content_type");
        String iconCtype = (String) rec.get("icon_content_type");
        if (ctype == null || !ctype.startsWith("image/")) {
            throw new BadRequestException(path);
        }
        String thumbFormat = getImageFileResizeFormat(ctype);
        final Blob icon = (Blob) rec.get("icon");


        if (icon != null) {
            final byte[] icondata = icon.getBytes(1, (int) icon.length());
            return Response.ok((StreamingOutput) output -> output.write(icondata)).type(iconCtype)
                           .header(HttpHeaders.CONTENT_LENGTH, icondata.length)
                           .build();
        }

        BufferedImage image;
        try (final ResourceLock l = new ResourceLock(path, false)) {
            File f = new File(basedir, path.substring(1));
            if (!f.exists()) {
                throw new NotFoundException(path);
            }
            image = ImageIO.read(f);
        }
        if (image == null) {
            log.warn("Unable to generated thumbnail. Content type: {} cannot read source image: {}", ctype, path);
            return Response.serverError().build();
        }

        //Now resize the image
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        BufferedImage thumbnail = (image.getWidth() > thumbWidth || image.getHeight() > thumbWidth) ?
                                  Scalr.resize(image, thumbWidth) : image;
        if (!ImageIO.write(thumbnail, thumbFormat, bos)) {
            throw new RuntimeException("Cannot find image writer for thumbFormat=" + thumbFormat);
        }

        iconCtype = "image/" + thumbFormat;
        final byte[] icondata = bos.toByteArray();
        update("updateIcon",
               "id", id,
               "icon", icondata,
               "icon_content_type", iconCtype);

        return Response.ok((StreamingOutput) output -> output.write(icondata)).type(iconCtype)
                       .header(HttpHeaders.CONTENT_LENGTH, icondata.length)
                       .build();
    }

    private String getImageFileResizeFormat(String ctype) {
        if (ctype.startsWith("image/jpeg")) {
            return "jpeg";
        } else if (ctype.startsWith("image/png")) {
            return "png";
        } else {
            return env.xcfg().getString("media.resize-default-format", "jpeg");
        }
    }


    @Override
    @Nonnull
    public Response get(Long id,
                        HttpServletRequest req,
                        Integer width,
                        Integer height,
                        boolean inline) throws Exception {
        return _get(id, req, width, height, inline);
    }

    private Response _get(Long id,
                          HttpServletRequest req,
                          Integer width,
                          Integer height,
                          boolean inline) throws Exception {
        Map<String, ?> row = selectOne("selectEntityPathById", "id", id);
        if (row == null) {
            throw new NotFoundException("");
        }
        return _get((String) row.get("folder"), (String) row.get("name"),
                    req, width, height, inline, true);
    }

    private Response _get(String folder,
                          String name,
                          HttpServletRequest req,
                          Integer width,
                          Integer height,
                          boolean inline,
                          boolean transfer) throws Exception {
        checkFolder(folder);
        if (!folder.endsWith("/")) {
            folder += '/';
        }
        Response r;
        Response.ResponseBuilder rb = Response.ok();
        String path = folder + name;

        final ResourceLock l = new ResourceLock(path, false);
        try {
            File f = new File(new File(basedir, folder), name);
            if (!f.exists() || !f.isFile()) {
                //noinspection ThrowCaughtLocally
                throw new NotFoundException(path);
            }
            if (folder.isEmpty() || folder.charAt(0) != '/') {
                folder = '/' + folder;
            }

            Map<String, ?> res = selectOne("selectByPath",
                                           "folder", folder,
                                           "name", name);
            if (res == null) {
                //noinspection ThrowCaughtLocally
                throw new NotFoundException(path);
            }
            String ctype = (String) res.get("content_type");
            if (ctype == null) {
                ctype = "application/octet-stream";
            }

            Number clength = (Number) res.get("content_length");
            Number id = (Number) res.get("id");
            MediaType mtype = MediaType.parse(ctype);
            final File respFile;

            if (id == null) {
                throw new NotFoundException(path);
            }
            if ((width != null || height != null) && CTypeUtils.isImageContentType(ctype)) {
                MediaType rsMtype = MediaType.parse("image/" + getImageFileResizeFormat(ctype));
                File rsFile = getResizedImageFile(rsMtype, folder, id.longValue(), width, height);
                if (rsFile.exists() && rsFile.length() > 0) { //we have resized version
                    mtype = rsMtype;
                    clength = rsFile.length();
                    respFile = rsFile;
                } else {
                    respFile = f;
                }
            } else {
                respFile = f;
            }
            if (mtype != null) {
                rb.type(mtype.toString());
                rb.encoding(mtype.getParameters().get("charset"));
            }
            rb.header(HttpHeaders.CONTENT_DISPOSITION,
                      ResponseUtils.encodeContentDisposition(name, inline));

            if (transfer) {
                if (clength != null) {
                    rb.header(HttpHeaders.CONTENT_LENGTH, clength);
                }
                l.releaseParent(); //unlock parent folder read-lock
                rb.entity((StreamingOutput) output -> {
                              try (final FileInputStream fis = new FileInputStream(respFile)) {
                                  IOUtils.copyLarge(fis, output);
                              } finally {
                                  l.close();
                              }
                          }
                );
            } else {
                if (clength != null) {
                    rb.header("X-Content-Length", clength);
                }
                rb.status(Response.Status.NO_CONTENT);
            }
            r = rb.build();
        } catch (Throwable e) {
            l.close();
            throw e;
        }
        if (!transfer) {
            l.close();
        }

        return r;
    }


    @Override
    @Transactional
    public void updateResizedImages(long id) throws IOException {
        Map<String, ?> row = selectOne("selectEntityPathById", "id", id);
        if (row == null) {
            return;
        }
        updateResizedImages(String.valueOf(row.get("folder")) + row.get("name"));
    }

    @Override
    @Transactional
    public void updateResizedImages(String path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path");
        }
        //collect widths
        List<Pair<Integer, Integer>> hints = new ArrayList<>();
        try (final ResourceLock l = new ResourceLock(path, false)) {
            String folder = getResourceParentFolder(path);
            String name = getResourceName(path);
            Number id = selectOne("selectEntityIdByPath",
                                  "folder", folder,
                                  "name", name);
            if (id == null) {
                return;
            }
            File dir = getResizedImageDir(folder, id.longValue());
            if (!dir.exists()) {
                return;
            }
            for (String h : dir.list(FileFileFilter.FILE)) {
                h = FilenameUtils.removeExtension(h);
                //noinspection EmptyCatchBlock
                try {
                    int ind = h.indexOf('x');
                    if (ind == 0) {
                        hints.add(new Pair<>(
                                null, Integer.parseInt(h.substring(1))));
                    } else if (ind > 0) {
                        if (ind < h.length() - 1) {
                            hints.add(new Pair<>(
                                    Integer.parseInt(h.substring(0, ind)),
                                    Integer.parseInt(h.substring(ind + 1))
                            ));
                        }
                    } else if (!h.isEmpty()) {
                        hints.add(new Pair<>(Integer.parseInt(h), null));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        for (Pair<Integer, Integer> h : hints) {
            ensureResizedImage(path, h.getOne(), h.getTwo(), 0);
        }
    }

    @Override
    @Transactional
    public Pair<Integer, Integer> ensureResizedImage(long id, Integer width, Integer height,
                                                     int flags) throws IOException {
        Map<String, ?> row = selectOne("selectEntityPathById", "id", id);
        if (row == null) {
            return null;
        }
        return ensureResizedImage(String.valueOf(row.get("folder")) + row.get("name"),
                                  width, height, flags);
    }

    @Override
    @Transactional
    public Pair<Integer, Integer> ensureResizedImage(String path,
                                                     Integer width, Integer height,
                                                     int flags) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path");
        }
        if ((width == null && height == null) ||
            (width != null && (width <= 0 || width > 6000)) ||
            (height != null && (height <= 0 || height > 6000))) {
            throw new IllegalArgumentException("width|height");
        }

        boolean skipSmall = ((flags & RESIZE_SKIP_SMALL) != 0);
        boolean coverArea = ((flags & RESIZE_COVER_AREA) != 0);

        try (final ResourceLock l = new ResourceLock(path, false)) {
            String folder = getResourceParentFolder(path);
            String name = getResourceName(path);
            Map<String, Object> info = getCachedMeta(folder, name);
            if (info == null) {
                return null;
            }
            long id = ((Number) info.get("id")).longValue();
            String ctype = (String) info.get("content_type");
            if (!CTypeUtils.isImageContentType(ctype)) {
                log.warn("ensureResizedImage: Not applicable file content type: {}", ctype);
                return null;
            }
            @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
            KVOptions kvmeta = new KVOptions((String) info.get("meta"));
            Integer iWidth = kvmeta.getIntObject("width", null);
            Integer iHeight = kvmeta.getIntObject("height", null);
            if (iWidth != null && iHeight != null) {
                if ((width == null || (width == kvmeta.getInt("width", Integer.MAX_VALUE))) &&
                    (height == null || (height == kvmeta.getInt("height", Integer.MAX_VALUE)))) {
                    return new Pair<>(iWidth, iHeight);
                }
                if (skipSmall) {
                    if ((width == null || (width > kvmeta.getInt("width", Integer.MAX_VALUE))) &&
                        (height == null || (height > kvmeta.getInt("height", Integer.MAX_VALUE)))) {
                        return new Pair<>(iWidth, iHeight);
                    }
                }
            }

            MediaType mtype = MediaType.parse("image/" + getImageFileResizeFormat(ctype));
            File source = new File(basedir, path.startsWith("/") ? path.substring(1) : path);
            if (!source.exists()) {
                return null;
            }

            if (coverArea &&
                width != null && height != null &&
                iWidth != null && iHeight != null &&
                iWidth > 0 && iHeight > 0) {

                double mul = Math.max(width.doubleValue() / iWidth.doubleValue(), height.doubleValue() / iHeight.doubleValue());
                width = (int) Math.ceil(mul * (double) iWidth);
                height = null;
            }

            File tfile = getResizedImageFile(mtype, folder, id, width, height);
            if (tfile.exists() && !FileUtils.isFileNewer(source, tfile)) {
                return new Pair<>(width, height); //up-to-date resized file version exists
            }
            BufferedImage image = ImageIO.read(source);
            if (image == null) {
                log.warn("Cannot read file as image: {}", source);
                return null;
            }
            if (width != null && height != null) {
                image = Scalr.resize(image, Scalr.Method.ULTRA_QUALITY, Scalr.Mode.FIT_EXACT, width, height);
            } else if (width != null) {
                image = Scalr.resize(image, Scalr.Method.ULTRA_QUALITY, Scalr.Mode.FIT_TO_WIDTH, width);
            } else {
                image = Scalr.resize(image, Scalr.Method.ULTRA_QUALITY, Scalr.Mode.FIT_TO_HEIGHT, height);
            }

            //Unlock read lock before acuiring exclusive write lock
            l.close();
            try (final ResourceLock wl = new ResourceLock(path, true)) {
                if (source.exists()) {
                    tfile.getParentFile().mkdirs();
                    try (final FileOutputStream fos = new FileOutputStream(tfile)) {
                        if (!ImageIO.write(image, mtype.getSubtype(), fos)) {
                            throw new RuntimeException("Cannot find image writer for: '" +
                                                       mtype.getSubtype() + "'");
                        }
                    }
                }
            }

            return new Pair<>(width, height);
        }
    }

    private File getResizedImageDir(String folder, long entryId) {
        if (!folder.endsWith("/")) {
            folder += "/";
        }
        return new File(basedir,
                        folder +
                        SIZE_CACHE_FOLDER +
                        '/' + entryId);
    }

    private File getResizedImageFile(MediaType mtype,
                                     String folder, long entryId,
                                     Integer width,
                                     Integer height) {
        if (!folder.endsWith("/")) {
            folder += "/";
        }
        String hint;
        if (width != null && height != null) {
            hint = width.toString() + "x" + height;
        } else if (width != null) {
            hint = width.toString();
        } else if (height != null) {
            hint = "x" + height;
        } else {
            throw new IllegalArgumentException("Either width or height must be specified");
        }
        return new File(basedir,
                        folder +
                        SIZE_CACHE_FOLDER +
                        '/' + entryId +
                        '/' + hint + '.' + mtype.getSubtype());
    }

    private void checkFolder(String folder) {
        if (folder == null) {
            throw new IllegalArgumentException();
        }
        if (folder.contains("..") || folder.contains(SIZE_CACHE_FOLDER)) {
            throw new BadRequestException(folder);
        }
    }


    private void ensureAuthenticated(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (req.getRemoteUser() == null) {
            if (resp == null || !req.authenticate(resp)) {
                throw new ForbiddenException("");
            }
        }
    }

    private Long _put(String folder,
                      String name,
                      HttpServletRequest req,
                      HttpServletResponse resp,
                      InputStream in,
                      int flags) throws Exception {

        ensureAuthenticated(req, resp);

        checkFolder(folder);
        Number id;
        folder = normalizeFolder(folder);

        //Used in order to detect ctype with TIKA (mark/reset are supported by BufferedInputStream)
        BufferedInputStream bis = new BufferedInputStream(in);
        FileUploadStream us = null;
        boolean localFilePut = ((req instanceof MediaRSLocalRequest) && ((MediaRSLocalRequest) req).getFile() != null);
        String rctype = (req.getContentType() == null) ?
                        req.getServletContext().getMimeType(name) :
                        req.getContentType();

        //We do not trust to the content-type provided by request
        MediaType mtype = MimeTypeDetector.detect(bis, name, rctype, req.getCharacterEncoding());
        if (mtype.getBaseType().toString().startsWith("text/") &&
            mtype.getParameters().get("charset") == null) {
            Charset charset = MimeTypeDetector.detectCharset(bis, name, rctype, req.getCharacterEncoding());
            if (charset != null) {
                mtype = new MediaType(mtype, charset);
            }
        }

        if (!localFilePut) {
            HierarchicalConfiguration<ImmutableNode> xcfg = env.xcfg();
            int memTh = xcfg.getInt("media.max-upload-inmemory-size", MB); //1Mb by default
            int uplTh = xcfg.getInt("media.max-upload-size", MB * 10); //10Mb by default
            us = new FileUploadStream(memTh, uplTh, "ncms-", ".upload", env.getTmpdir());
        }

        try (final ResourceLock l = new ResourceLock(folder + name, true)) {
            id = selectOne("selectEntityIdByPath",
                           "folder", folder,
                           "name", name);

            checkEditAccess(folder + name, req);

            long actualLength;
            if (localFilePut) {
                actualLength = ((MediaRSLocalRequest) req).getFile().length();
            } else {
                actualLength = IOUtils.copyLarge(bis, us);
                us.close();
            }
            if (req.getContentLength() != -1 && req.getContentLength() != actualLength) {
                throw new BadRequestException(
                        String.format("Wrong Content-Length request header specified. " +
                                      "The file %s/%s will be rejected",
                                      folder, name
                        )
                );
            }
            File dir = new File(basedir, folder.substring(1));
            File target = new File(dir, name);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (log.isDebugEnabled()) {
                log.debug("Writing {}/{} into: {} as {} size: {}",
                          folder, name, target.getAbsolutePath(), mtype, actualLength);
            }
            try (final FileOutputStream fos = new FileOutputStream(target)) {
                if (us != null) {
                    us.writeTo(fos);
                } else {
                    IOUtils.copyLarge(bis, fos);
                }
                fos.flush();
            }

            KVOptions meta;
            if (CTypeUtils.isImageContentType(mtype.toString())) {
                Metadata metadata = MetadataDetector.detect(mtype, target);
                if (metadata.get("width") == null) {
                    if (metadata.get(Metadata.IMAGE_WIDTH) != null) {
                        metadata.add("width", metadata.get(Metadata.IMAGE_WIDTH));
                    }
                }
                if (metadata.get("height") == null) {
                    if (metadata.get(Metadata.IMAGE_LENGTH) != null) {
                        metadata.add("height", metadata.get(Metadata.IMAGE_LENGTH));
                    }
                }
                meta = MetadataDetector.metadata2Options(metadata,
                                                         "width", "height");
            } else {
                meta = new KVOptions();
            }

            if (id == null) {

                Map<String, Object> args = new HashMap<>();
                args.put("folder", folder);
                args.put("name", name);
                args.put("status", 0);
                args.put("content_type", mtype.toString());
                args.put("put_content_type", req.getContentType());
                args.put("content_length", actualLength);
                args.put("owner", req.getRemoteUser());
                args.put("meta", meta.toString());
                args.put("system", ((flags & PUT_SYSTEM) != 0) ? 1 : 0);
                insert("insertEntity", args);
                id = (Number) args.get("id"); //autogenerated

            } else {

                update("updateEntity",
                       "id", id,
                       "content_type", mtype.toString(),
                       "content_length", actualLength,
                       "owner", req.getRemoteUser(),
                       "meta", meta.toString(),
                       "system", ((flags & PUT_SYSTEM) != 0) ? 1 : 0);
            }

            synchronized (metaCache) {
                metaCache.remove(folder + name);
                if (id != null) {
                    metaCache.remove(id);
                }
            }

            if (id != null) {
                ebus.fireOnSuccessCommit(new MediaUpdateEvent(this, false, id, folder + name));
                if ((PUT_NO_KEYS & flags) == 0) {
                    updateFTSKeywords(id.longValue(), req);
                }
            }

        } finally {
            if (us != null && us.getFile() != null) {
                us.getFile().delete();
            }
            bis.close();
        }

        return id != null ? id.longValue() : null;
    }

    public Closeable acquireReadResourceLock(String path) {
        return new ResourceLock(path, false);
    }

    public Closeable acquireWriteResourceLock(String path) {
        return new ResourceLock(path, true);
    }

    final class ResourceLock implements Closeable {

        ReentrantReadWriteLock parent;

        ReentrantReadWriteLock child;

        final boolean parentWriteLock;

        final boolean childWriteLock;

        private ResourceLock(String path, boolean childWriteLock) {
            this(path, false, childWriteLock);
        }

        private ResourceLock(String path, boolean parentWriteLock, boolean childWriteLock) {
            if (path.isEmpty() || path.charAt(0) != '/') {
                path = '/' + path;
            }
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            this.parent = null;
            this.child = null;
            this.parentWriteLock = parentWriteLock;
            this.childWriteLock = childWriteLock;
            String folder = getResourceParentFolder(path);
            try {
                if (!folder.equals(path)) {
                    parent = acquirePathRWLock(folder, parentWriteLock);
                    child = acquirePathRWLock(path, childWriteLock);
                } else {
                    parent = null;
                    child = acquirePathRWLock(path, childWriteLock);
                }
            } catch (Throwable e) {
                //noinspection finally
                try {
                    close();
                } catch (IOException e1) {
                    log.error("", e1);
                } finally {
                    //noinspection ThrowFromFinallyBlock
                    throw new RuntimeException(e);
                }
            }
        }

        public void releaseParent() {
            if (parent != null) {
                if (parentWriteLock) {
                    parent.writeLock().unlock();
                } else {
                    parent.readLock().unlock();
                }
                parent = null;
            }
        }

        public void releaseChild() {
            if (child != null) {
                if (childWriteLock) {
                    child.writeLock().unlock();
                } else {
                    child.readLock().unlock();
                }
                child = null;
            }
        }

        public void close() throws IOException {
            try {
                releaseChild();
            } finally {
                releaseParent();
            }
        }
    }

    private ReentrantReadWriteLock acquirePathRWLock(String path, boolean acquireWrite) {
        if (path.isEmpty() || path.charAt(0) != '/') {
            path = '/' + path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (log.isDebugEnabled()) {
            log.debug("Locking: {} W: {}", path, acquireWrite);
        }
        ReentrantReadWriteLock rwlock;
        while (true) {
            synchronized (locksCache) {
                rwlock = (ReentrantReadWriteLock) locksCache.get(path);
                if (rwlock == null) {
                    rwlock = new ReentrantReadWriteLock();
                    locksCache.put(path, rwlock);
                }
            }

            //Optimistic locking used here

            if (acquireWrite) {
                rwlock.writeLock().lock();
            } else {
                rwlock.readLock().lock();
            }

            synchronized (locksCache) {
                //noinspection ObjectEquality
                if (rwlock == locksCache.get(path)) {
                    //Locked rwlock is not changed we are safe to use it until it remains locked
                    break;
                } else {
                    //Locked rwlock is changed (removed from locksCache and replaced) so release it and try again
                    if (acquireWrite) {
                        rwlock.writeLock().unlock();
                    } else {
                        rwlock.readLock().unlock();
                    }
                }
            }
        }
        return rwlock;
    }

    private void checkEditAccess(Long id, HttpServletRequest req) {
        if ((req instanceof MediaRSLocalRequest) || req.isUserInRole("admin")) {
            return;
        }
        Map<String, Object> meta = getCachedMeta(id);
        if (meta != null) {
            _checkEditAccess(meta, (String) meta.get("folder") + meta.get("name"), req);
        }
    }

    private void checkEditAccess(String path, HttpServletRequest req) {
        if ((req instanceof MediaRSLocalRequest) || req.isUserInRole("admin")) {
            return;
        }
        _checkEditAccess(getCachedMeta(path), path, req);
    }

    private void _checkEditAccess(Map<String, ?> fmeta, String path, HttpServletRequest req) {

        //todo check page access

        boolean isFile = (fmeta != null && 0 == ((Number) fmeta.get("status")).intValue());
        if (isFile) {
            if (!req.getRemoteUser().equals(fmeta.get("owner"))) {
                String msg = message.get("ncms.mmgr.access.denied", req, path);
                throw new ForbiddenException(msg);
            }
        } else {
            File f = new File(basedir, (!path.isEmpty() && path.charAt(0) == '/') ? path.substring(1) : path);
            if (!f.isDirectory()) {
                return;
            }
            // check not owned files in the directory
            Number count = selectOne("countNotOwned",
                                     "folder", normalizeFolder(path),
                                     "owner", req.getRemoteUser());
            if (count != null && count.intValue() > 0) {
                throw new ForbiddenException(message.get("ncms.mmgr.access.denied.notOwned", req));
            }
        }
    }

    private void checkFileDeletion(Long id, HttpServletRequest req) throws Exception {
        if (id == null) {
            return;
        }
        List<Map> rows = select("selectAsmMediaDepsByFile", id);
        if (rows.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map row : rows) {
            sb.append(System.getProperty("line.separator"));
            String pname = (String) row.get("name");
            String attrName = (String) row.get("attr_name");
            if (pname == null || attrName == null) {
                continue;
            }
            sb.append(pname).append(": ").append(attrName);
        }
        String msg = message.get("ncms.mmgr.access.file.used", sb.toString());
        throw new ForbiddenException(msg);
    }

    private boolean isInSystemFolder(String path) {
        path = normalizeFolder(path);
        List<Object> sdirs = env.xcfg().getList("media.system-directories.directory");
        for (final Object sdirObj : sdirs) {
            String sdir = String.valueOf(sdirObj);
            if (path.startsWith(normalizeFolder(sdir))) {
                return true;
            }
        }
        return false;
    }

    public static String getResourceParentFolder(String path) {
        String dirname = FilenameUtils.getPath(path);
        if (StringUtils.isBlank(dirname)) {
            dirname = "/";
        } else {
            dirname = normalizeFolder(dirname);
        }
        return dirname;
    }

    public static String getResourceName(String path) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return null;
        }
        return FilenameUtils.getName(path);
    }

    public static String normalizeFolder(String folder) {
        if (folder.isEmpty() || folder.charAt(0) != '/') {
            folder = '/' + folder;
        }
        if (!folder.endsWith("/")) {
            folder += '/';
        }
        return folder;
    }

    public static String normalizePath(String path) {
        if (path.isEmpty() || path.charAt(0) != '/') {
            path = '/' + path;
        }
        return path;
    }

    @Subscribe
    public void pageDropped(AsmRemovedEvent ev) {
        String path = getPageLocalFolderPath(ev.getId());
        try (final ResourceLock l = new ResourceLock(path, true)) {
            File pdir = new File(basedir, path);
            if (pdir.isDirectory()) {
                File[] files = pdir.listFiles(f -> (!f.isDirectory()) || SIZE_CACHE_FOLDER.equals(f.getName()));
                for (final File f : files) {
                    log.info("Remove file/dir: {}", f);
                    FileUtils.deleteQuietly(f);
                }
            }
        } catch (IOException e) {
            log.error("Failed to drop page files dir: {}", path, e);
        }
    }

    @Subscribe
    public void mediaUpdate(MediaUpdateEvent ev) {
        if (!ev.isFolder()) {
            try {
                updateResizedImages(ev.getPath());
            } catch (IOException e) {
                log.error("Failed to update resized images dir", e);
            }
        }
    }

    @Subscribe
    public void ensureResizedImage(EnsureResizedImageJobEvent ev) {
        resizer.execute(() -> _ensureResizedImage(ev));
    }

    private void _ensureResizedImage(EnsureResizedImageJobEvent ev) {
        try {
            ensureResizedImage(ev.getId(), ev.getWidth(), ev.getHeight(), ev.getFlags());
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Override
    public String getPageLocalFolderPath(Long pageId) {
        StringBuilder sb = new StringBuilder(16);
        sb.append("/pages");
        String sPageId = pageId.toString();
        for (int i = 0, l = sPageId.length(); i < l; ++i) {
            if (i % 3 == 0) {
                sb.append('/');
            }
            sb.append(sPageId.charAt(i));
        }
        return sb.toString();
    }

    private static final class RWLocksLRUCache extends LRUMap {

        private RWLocksLRUCache(int maxSize) {
            super(maxSize, 0.75f, true);
        }

        //we must be already in synchronized block!
        @Override
        protected boolean removeLRU(LinkEntry entry) {
            ReentrantReadWriteLock lock = (ReentrantReadWriteLock) entry.getValue();
            if (lock.writeLock().tryLock()) { //check if rwlock is completely free
                lock.writeLock().unlock();
                return true;
            }
            return false; //Do not remove: someone holds the lock
        }
    }

    private static final class FileUploadStream extends DeferredFileOutputStream {

        final int hardThreshould;

        /**
         * @param memThreashould Threshold after that new data will be flushed on disk.
         * @param hardThreshould Threshold after that IOException will fired
         */
        FileUploadStream(int memThreashould, int hardThreshould, String prefix, String suffix, File dir) {
            super(memThreashould, prefix, suffix, dir);
            this.hardThreshould = hardThreshould;
        }

        void check(int length) throws IOException {
            if (getByteCount() + length >= hardThreshould) {
                throw new IOException("Reached the maximum upload file size: " + hardThreshould);
            }
        }

        @Override
        public void write(int b) throws IOException {
            check(1);
            super.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            check(b.length);
            super.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            check(len);
            super.write(b, off, len);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //                       Import resources staff                          //
    ///////////////////////////////////////////////////////////////////////////

    private final List<DirectoryScanner> watchScanners = Collections.synchronizedList(new ArrayList<>());

    private final FSWatcherCollectEventHandler watchHandler = new FSWatcherCollectEventHandler(
            FSWatcherCollectEventHandler.MOVE_CREATED_INTO_MODIFIED
    );

    @Override
    public void init(FSWatcher watcher) {
    }

    @Override
    public void handlePollTimeout(FSWatcher watcher) {
        FSWatcherCollectEventHandler snapshot;
        synchronized (watchHandler) {
            if (watchHandler.getModified().isEmpty() &&
                watchHandler.getDeleted().isEmpty()) {
                watchHandler.clear();
                return;
            }
            snapshot = (FSWatcherCollectEventHandler) watchHandler.clone();
            watchHandler.clear();
        }
        FSWatcherUserData data = watcher.getUserData();
        Set<Path> modified = new HashSet<>(snapshot.getModified());
        Set<Path> deleted = new HashSet<>(snapshot.getDeleted());
        for (final Path path : snapshot.getModified()) {
            if (modified.contains(path)) {
                if (Files.exists(path)) {
                    deleted.remove(path);
                } else {
                    modified.remove(path);
                }
            }
        }

        for (final Path path : deleted) {
            Path target = data.target.resolve(data.ds.getBasedir().relativize(path));
            try {
                deleteResource(target.toString(), new MediaRSLocalRequest(env, target.toFile()), null);
            } catch (NotFoundException ignored) {
            } catch (Exception e) {
                log.error("File deletion failed. Path: {} target: {} error: {}", path, target, e.getMessage());
            }
        }

        for (final Path path : modified) {
            Path target = data.target.resolve(data.ds.getBasedir().relativize(path));
            try {
                importFile(path.toString(),
                           target.toString(),
                           data.overwrite,
                           data.system);
            } catch (IOException e) {
                log.error("File import failed. Path: {} target: {} error: {}", path, target, e.getMessage());
            }

        }
    }

    @Override
    public void handleRegisterEvent(FSWatcherRegisterEvent ev) throws Exception {
        watcherImportFile(ev);
    }

    @Override
    public void handleCreateEvent(FSWatcherCreateEvent ev) {
        synchronized (watchHandler) {
            watchHandler.handleCreateEvent(ev);
        }
    }

    @Override
    public void handleModifyEvent(FSWatcherModifyEvent ev) {
        synchronized (watchHandler) {
            watchHandler.handleModifyEvent(ev);
        }
    }

    @Override
    public void handleDeleteEvent(FSWatcherDeleteEvent ev) throws Exception {
        synchronized (watchHandler) {
            watchHandler.handleDeleteEvent(ev);
        }
    }

    private void watcherImportFile(FSWatcherEventSupport ev) throws Exception {
        FSWatcherUserData data = ev.getWatcher().getUserData();
        Path target = data.target.resolve(data.ds.getBasedir().relativize(ev.getFullPath()));
        importFile(ev.getFullPath().toString(),
                   target.toString(),
                   data.overwrite,
                   data.system);
    }

    @Override
    @Nonnull
    public String resolveFileLink(Long id) {
        return env.getAppRoot() + "/rs/media/fileid/" + id;
    }

    @Override
    @Nonnull
    public String resolveFileLink(Long id, boolean inline) {
        return resolveFileLink(id) + "?inline=true";
    }

    @Override
    public Long getFileIdByResourceSpec(String spec) {
        spec = spec.toLowerCase();
        if (!spec.startsWith("media:") && !spec.startsWith("image:")) {
            return null;
        }
        spec = spec.substring("media:".length()); /*'image:' string has the same length*/
        if (spec.charAt(0) == '/') {
            spec = spec.substring(1);
        }
        int ind;
        int ind1 = spec.indexOf('/');
        int ind2 = spec.indexOf('|');
        if (ind1 == -1 || ind2 == -1) {
            ind = Math.max(ind1, ind2);
        } else {
            ind = Math.min(ind1, ind2);
        }
        if (ind != -1) {
            spec = spec.substring(0, ind);
        }
        try {
            return Long.valueOf(spec);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    @Transactional(executorType = ExecutorType.SIMPLE)
    public void importDirectory(String source,
                                String target,
                                String[] includes,
                                String[] excludes,
                                int flags) throws IOException {

        final boolean overwrite = (flags & IMPORT_OVERWRITE) != 0;
        final boolean watch = (flags & IMPORT_WATCH) != 0;
        final boolean system = (flags & IMPORT_SYSTEM) != 0;


        if (target.startsWith("/")) {
            target = target.substring(1);
        }
        log.info("Importing {} into {} flags={}", source, target, flags);
        final String importTarget = target;
        final DirectoryScannerFactory sf = new DirectoryScannerFactory(Paths.get(source));
        for (final String inc : includes) {
            sf.include(inc);
        }
        for (final String exc : excludes) {
            sf.exclude(exc);
        }
        if (watch) {
            DirectoryScanner ds = sf.createScanner();
            watchScanners.add(ds);
            ds.activateFileSystemWatcher(this, 200L,
                                         new FSWatcherUserData(ds,
                                                               Paths.get(target),
                                                               overwrite,
                                                               system));
        } else {
            try (DirectoryScanner ds = sf.createScanner()) {
                ds.scan(new DirectoryScannerVisitor() {
                    public void visit(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                        importFile(sf.getBasedir().resolve(path).toString(),
                                   Paths.get(importTarget).resolve(path).toString(),
                                   overwrite, system);
                    }

                    @Override
                    public void error(Path path, IOException e) throws IOException {
                        log.error("Failed to scan path: {}", path, e);
                    }
                });
            }
        }

        if ((flags & IMPORT_CLEANUP_MISSING) != 0) {
            Set<Path> srcPaths = new HashSet<>();
            Set<Path> tgtPaths = new HashSet<>();
            try (DirectoryScanner ds = sf.createScanner()) {
                ds.scan(new DirectoryScannerVisitor() {
                    @Override
                    public void visit(Path path, BasicFileAttributes attrs) throws IOException {
                        srcPaths.add(path);
                    }

                    @Override
                    public void error(Path path, IOException e) throws IOException {
                    }
                });
            }

            try (DirectoryScanner ds = new DirectoryScannerFactory(
                    getBasedir().toPath().resolve(importTarget)).createScanner()) {

                ds.scan(new DirectoryScannerVisitor() {
                    @Override
                    public void visit(Path path, BasicFileAttributes attrs) throws IOException {
                        tgtPaths.add(path);
                    }

                    @Override
                    public void error(Path path, IOException exc) throws IOException {
                    }
                });
            }

            for (final Path tpath : tgtPaths) {
                if (srcPaths.contains(tpath)) {
                    continue;
                }
                String path = importTarget + '/' + tpath;
                log.info("Removing missing resource: {}", path);
                String name = getResourceName(path);
                String folder = getResourceParentFolder(path);
                try (final ResourceLock l = new ResourceLock(path, true)) {
                    File f = new File(basedir, path);
                    FileUtils.deleteQuietly(f);
                    delete("deleteFile",
                           "folder", folder,
                           "name", name);
                }
            }
        }
    }

    @Override
    @Transactional(executorType = ExecutorType.SIMPLE)
    public Long importFile(String source, String target, boolean overwrite, boolean system) throws IOException {
        File srcFile = new File(source);
        if (!srcFile.isFile()) {
            throw new IOException(srcFile.getAbsolutePath() + " is not a file");
        }
        File tgt = new File(basedir, target);
        if (tgt.isDirectory()) {
            throw new IOException("Cannot overwrite existing directory: " + target);
        }
        String name = getResourceName(target);
        String folder = getResourceParentFolder(target);
        if (!overwrite &&
            tgt.exists() &&
            !FileUtils.isFileNewer(srcFile, tgt)) {
            //check db
            Number id = selectOne("selectEntityIdByPath",
                                  "name", name,
                                  "folder", folder);
            if (id != null) {
                return id.longValue();
            }
        }
        log.info("Importing {}", target);
        try (final FileInputStream fis = new FileInputStream(srcFile)) {
            try {
                int flags = 0;
                if (system) {
                    flags |= PUT_SYSTEM;
                }
                return _put(folder, name, new MediaRSLocalRequest(env, srcFile), null, fis, flags);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    @Override
    public Long importFile(InputStream source, String target, boolean system) throws IOException {
        String name = getResourceName(target);
        String folder = getResourceParentFolder(target);
        try {
            int flags = 0;
            if (system) {
                flags |= PUT_SYSTEM;
            }
            return _put(folder, name, new MediaRSLocalRequest(env), null, source, flags);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static final class FSWatcherUserData {
        private final DirectoryScanner ds;
        private final Path target;
        private final boolean overwrite;
        private final boolean system;

        private FSWatcherUserData(DirectoryScanner ds,
                                  Path target,
                                  boolean overwrite,
                                  boolean system) {
            this.ds = ds;
            this.target = target;
            this.overwrite = overwrite;
            this.system = system;
        }
    }

    @Override
    @Dispose
    public void close() throws IOException {
        log.info("Disposing all directory scanners");
        DirectoryScanner[] wsArr = watchScanners.toArray(new DirectoryScanner[watchScanners.size()]);
        for (final DirectoryScanner ds : wsArr) {
            try {
                ds.close();
            } catch (IOException e) {
                log.error("", e);
            }
        }
        watchHandler.clear();
        watchScanners.clear();
    }
}