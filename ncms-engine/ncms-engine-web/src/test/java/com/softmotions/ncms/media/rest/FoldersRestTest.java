package com.softmotions.ncms.media.rest;

import com.softmotions.ncms.media.db.MediaRestTest;
import com.softmotions.ncms.media.model.MediaFolder;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;

/**
 * Created by shu on 4/24/2014.
 */
public class FoldersRestTest extends FoldersRestTestBase {

	public FoldersRestTest() {
		super("/folders");
	}

	@Test
	public void testCreateHierarchy() throws Exception {
		MediaFolder root1 = MediaFolder.of("root1", "desc-root1");
		MediaFolder root2 = MediaFolder.of("root1", "desc-root1");

		ResteasyWebTarget target = getWebTarget("/show");//client.target(address + "/ncms/rs/media/folders/show");
		Response response = target.request().get();
		assertEquals(200, response.getStatus());
		response.close();
	}

}
