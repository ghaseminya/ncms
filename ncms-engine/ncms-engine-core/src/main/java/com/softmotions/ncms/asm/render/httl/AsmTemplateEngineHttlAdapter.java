package com.softmotions.ncms.asm.render.httl;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.text.ParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import httl.Engine;
import httl.Template;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.softmotions.commons.lifecycle.Dispose;
import com.softmotions.ncms.NcmsEnvironment;
import com.softmotions.ncms.asm.render.AsmRendererContext;
import com.softmotions.ncms.asm.render.AsmRenderingException;
import com.softmotions.ncms.asm.render.AsmTemplateEngineAdapter;

/**
 * Template engine adapter for HTTL template engine (http://httl.github.io)
 *
 * @author Adamansky Anton (adamansky@gmail.com)
 */

@Singleton
public class AsmTemplateEngineHttlAdapter implements AsmTemplateEngineAdapter {

    private static final Logger log = LoggerFactory.getLogger(AsmTemplateEngineHttlAdapter.class);

    public static final String[] DEFAULT_EXTS = new String[]{"httl"};

    private final String[] exts;

    private final Engine engine;

    @Inject
    public AsmTemplateEngineHttlAdapter(NcmsEnvironment cfg) {
        Properties httlProps = new Properties();
        String extsStr = cfg.xcfg().getString("httl[@extensions]");
        if (!StringUtils.isBlank(extsStr)) {
            exts = extsStr.split(",");
            for (int i = 0; i < exts.length; ++i) {
                exts[i] = exts[i].trim();
            }
        } else {
            exts = DEFAULT_EXTS;
        }
        String httlPropsStr = cfg.xcfg().getString("httl");
        if (!StringUtils.isBlank(httlPropsStr)) {
            try {
                httlProps.load(new StringReader(httlPropsStr));
            } catch (IOException e) {
                String msg = "Failed to load <httl> properties: " + httlPropsStr;
                log.error(msg, e);
                throw new RuntimeException(msg, e);
            }
        }
        try {
            StringWriter eprops = new StringWriter();
            httlProps.store(eprops, "HTTL effective properties");
            log.info(eprops.toString());
        } catch (IOException e) {
            log.error("", e);
        }
        this.engine = Engine.getEngine(httlProps);
    }

    @Override
    public String[] getSupportedExtensions() {
        return exts;
    }

    @Override
    public void renderTemplate(String location, AsmRendererContext ctx, Writer out) throws IOException {
        try {
            Template template = engine.getTemplate(location, ctx.getLocale());
            template.render(ctx, out);
        } catch (ParseException e) {
            throw new AsmRenderingException("Failed to parse template: " + location +
                                            " for asm: " + ctx.getAsm().getName(), e);
        }
    }

    @Override
    public void renderTemplate(String location, Map<String, Object> ctx, Locale locale, Writer out) throws IOException {
        try {
            Template template = engine.getTemplate(location, locale);
            template.render(ctx, out);
        } catch (ParseException e) {
            throw new AsmRenderingException("Failed to parse template: " + location, e);
        }
    }

    @Dispose
    public void close() {

    }
}
