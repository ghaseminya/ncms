package com.softmotions.ncms.media.rest;

import com.softmotions.ncms.media.model.MediaFile;
import com.softmotions.ncms.media.model.MediaFolder;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 * Created by shu on 4/23/2014.
 */

/**
 * File manipulations API <p>
 * get 	    /file/id      - retrieve file data <br>
 * put	    /file/id      - update file data <br>
 * delete	  /file/id      - delete file <br>
 * post 	  /file/id      - create new file in specified folder <br>
 * put	    /file/id/id   - move file to another folder <br>
*/

@Path("media/file")
public class MediaFileRS extends MediaRestBase {

	@GET
	@Path("/{id}")
	@Produces("application/json")
	public Response getFile(@PathParam("id") Long id) {
		MediaFile file = ebean.find(MediaFile.class, id);
		if(file == null) return response(404, "File not found: " + id);
		return ok(file);
	}

	@PUT
	@Path("/{id}")
	@Consumes("application/json")
	@Produces("application/json")
	public Response putFile(@PathParam("id") Long id, MediaFile file) {
		MediaFile f = ebean.find(MediaFile.class, id);
		if(f == null) return response(500, "File not found: " + id);
		f.setName(file.getName());
		f.setDescription(file.getDescription());
		ebean.update(f);
		return ok(f);
	}

	@DELETE
	@Path("/{id}")
	public Response deleteFile(@PathParam("id") Long id) {
		MediaFile f = ebean.find(MediaFile.class, id);
		if(f == null) return response(500, "File not found: " + id);
		ebean.delete(f);
		return ok("deleted: " + id);
	}

	@POST
	@Path("/{folderId}")
	@Consumes("application/json")
	@Produces("application/json")
	public Response createFile(@PathParam("folderId") Long folderId, MediaFile file) {
		MediaFolder folder = ebean.find(MediaFolder.class, folderId);
		if(folder == null) return response(500, "Folder not found: " + folderId);
		file.setMediaFolder(folder);
		ebean.save(file);
		return ok(file);
	}

	@GET
	@Path("/{id}/{folderId}")
	public Response moveToFolder(@PathParam("id") Long id, @PathParam("folderId") Long folderId) {
		MediaFile f = ebean.find(MediaFile.class, id);
		if(f == null) return response(500, "File not found: " + id);
		MediaFolder folder = ebean.find(MediaFolder.class, folderId);
		if(folder == null) return response(500, "Folder not found: " + folderId);
		f.setMediaFolder(folder);
		ebean.update(f);
		return ok("moved to flder: " + folderId);
	}

}
