package com.softmotions.ncms.asm.render;

import com.softmotions.ncms.asm.AsmDAO;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.mybatis.guice.transactional.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Asm handler.
 *
 * @author Adamansky Anton (adamansky@gmail.com)
 */

@Singleton
public class AsmServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AsmServlet.class);

    @Inject
    AsmDAO adao;

    @Inject
    Provider<AsmRenderer> rendererProvider;


    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        getContent(req, resp, true);
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        getContent(req, resp, true);
    }

    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        getContent(req, resp, false);
    }

    @Transactional
    protected void getContent(HttpServletRequest req, HttpServletResponse resp, boolean transfer) throws ServletException, IOException {
        Writer out = transfer ? resp.getWriter() : new StringWriter();

        String ref = req.getPathInfo();
        if (ref.charAt(0) == '/') {
            ref = ref.substring(1);
        }
        if (ref.isEmpty()) {
            log.warn("Invalid pathInfo: " + req.getPathInfo());
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Object asmRef;
        try {
            asmRef = Long.parseLong(ref);
        } catch (NumberFormatException e) {
            asmRef = ref;
        }

        AsmRendererContext ctx =
                new DefaultAsmRendererContext(req, resp, adao, asmRef, null);
        AsmRenderer renderer = rendererProvider.get();
        renderer.render(ctx, out);

        if (transfer) {
            out.flush();
        } else {
            resp.setContentLength(((StringWriter) out).getBuffer().length());
            resp.flushBuffer();
        }
    }
}
