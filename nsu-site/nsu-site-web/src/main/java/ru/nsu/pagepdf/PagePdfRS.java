package ru.nsu.pagepdf;

import com.softmotions.ncms.rds.RefDataStore;
import com.softmotions.web.ResponseUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.mybatis.guice.transactional.Transactional;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.InputStream;

/**
 * @author Tyutyunkov Vyacheslav (tve@softmotions.com)
 * @version $Id$
 */
@Path("pagepdf")
@Singleton
public class PagePdfRS {

    public static final String PAGE_PDF_REF_TEMPLATE = "page.pdf:{id}";

    private final RefDataStore ds;

    @Inject
    public PagePdfRS(RefDataStore ds) {
        this.ds = ds;
    }

    @GET
    @Path("/{id}/{name}")
    @Transactional
    public Response getPagePdf(@Context HttpServletRequest req,
                               @PathParam("id") Long id,
                               @PathParam("name") String name) throws Exception {
        final Response.ResponseBuilder rb = Response.ok();
        rb.header(HttpHeaders.CONTENT_DISPOSITION, ResponseUtils.encodeContentDisposition(name, true));
        ds.getData(PAGE_PDF_REF_TEMPLATE.replace("{id}", String.valueOf(id)), (type, data) -> {
            if (type == null || data == null) {
                rb.status(Response.Status.NO_CONTENT);
            } else {
                rb.type(type);
                rb.entity((StreamingOutput) output -> IOUtils.copyLarge(data, output));
            }
        });
        return rb.build();
    }

    public void savePagePdf(Long id, InputStream data) {
        ds.saveData(PAGE_PDF_REF_TEMPLATE.replace("{id}", String.valueOf(id)), data, "application/pdf");
    }

    public boolean isPagePdfExists(Long id) {
        return ds.isDataExists(PAGE_PDF_REF_TEMPLATE.replace("{id}", String.valueOf(id)));
    }

    public void removePagePdf(Long id) {
        ds.removeData(PAGE_PDF_REF_TEMPLATE.replace("{id}", String.valueOf(id)));
    }

}