package com.softmotions.ncms.media.rest;

import com.softmotions.ncms.media.db.MediaRestTest;
import com.softmotions.ncms.media.model.MediaFolder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;

/**
 * Created by shu on 4/24/2014.
 */
public class FoldersRestTestBase extends MediaRestTest {

	public FoldersRestTestBase(String basePath) {
		super(basePath);
	}

	protected ResteasyWebTarget target(long id) {
		return getWebTarget("/" + id);
	}

	protected ResteasyWebTarget target(MediaFolder folder) {
		return target(folder.getId());
	}

	protected Entity<MediaFolder> entity(MediaFolder folder) {
		return Entity.entity(folder, "application/json");
	}

	protected MediaFolder createAndCheck(MediaFolder root, MediaFolder folder) {
		ResteasyWebTarget target = getWebTarget("/folder", root == null? "/" : "/" + root.getId());
		Response response = target.request().post(entity(folder));
		assertEquals(200, response.getStatus());
		MediaFolder f = response.readEntity(MediaFolder.class);
		response.close();
		assertEquals(folder.getName(), f.getName());
		assertEquals(folder.getName(), f.getName());
		return f;
	}

	protected MediaFolder createAndCheck(MediaFolder folder) {
		return createAndCheck(null, folder);
	}

	protected MediaFolder requestAndCheck(MediaFolder folder) {
		Response response = target(folder).request().get();
		assertEquals(200, response.getStatus());
		MediaFolder f = response.readEntity(MediaFolder.class);
		response.close();
		assertEquals(folder.getName(), f.getName());
		assertEquals(folder.getName(), f.getName());
		return f;
	}

}
