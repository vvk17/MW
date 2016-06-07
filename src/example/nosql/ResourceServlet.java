package example.nosql;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Part;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.cloudant.client.api.Database;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;

@Path("/favorites")
/**
 * CRUD service of todo list table. It uses REST style.
 */
public class ResourceServlet {

	private static final long DAY_MILLISECS = 86400000;
	private static final String API_ROOT = "/api/favorites";

	private static boolean schedulerInitialized = false;

	public ResourceServlet() {
		initializeScheduler();
	}

	@POST
	public Response create(@QueryParam("id") Long id, @FormParam("name") String name, @FormParam("value") String value)
			throws Exception {
		Database db = null;
		try {
			db = getDB();
		} catch (Exception re) {
			re.printStackTrace();
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		}

		String idString = id == null ? null : id.toString();
		JsonObject resultObject = create(db, idString, name, value, null, null);

		System.out.println("Create Successful.");

		return Response.ok(resultObject.toString()).build();
	}

	protected JsonObject create(Database db, String id, String name, String value, Part part, String fileName)
			throws IOException {

		// check if document exist
		HashMap<String, Object> obj = (id == null) ? null : db.find(HashMap.class, id);

		if (obj == null) {
			// if new document

			id = String.valueOf(System.currentTimeMillis());

			// create a new document
			System.out.println("Creating new document with id : " + id);
			Map<String, Object> data = new HashMap<String, Object>();
			data.put("name", name);
			data.put("_id", id);
			data.put("value", value);
			data.put("creation_date", new Date().toString());
			data.put("modification_date", System.currentTimeMillis());
			db.save(data);

			// attach the attachment object
			obj = db.find(HashMap.class, id);
			saveAttachment(db, id, part, fileName, obj);
		} else {
			// if existing document
			// attach the attachment object
			saveAttachment(db, id, part, fileName, obj);

			// update other fields in the document
			obj = db.find(HashMap.class, id);
			obj.put("name", name);
			obj.put("value", value);
			obj.put("modification_date", System.currentTimeMillis());
			db.update(obj);
		}

		obj = db.find(HashMap.class, id);

		JsonObject resultObject = toJsonObject(obj);

		return resultObject;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response get(@QueryParam("id") Long id, @QueryParam("cmd") String cmd) throws Exception {

		Database db = null;
		try {
			db = getDB();
		} catch (Exception re) {
			re.printStackTrace();
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		}

		JsonObject resultObject = new JsonObject();
		JsonArray jsonArray = new JsonArray();

		if (id == null) {
			try {
				// get all the document present in database
				List<HashMap> allDocs = db.view("_all_docs").query(HashMap.class);

				if (allDocs.size() == 0) {
					allDocs = initializeSampleData(db);
				}

				for (HashMap doc : allDocs) {
					HashMap<String, Object> obj = db.find(HashMap.class, doc.get("id") + "");
					JsonObject jsonObject = new JsonObject();
					LinkedTreeMap<String, Object> attachments = (LinkedTreeMap<String, Object>) obj.get("_attachments");

					if (attachments != null && attachments.size() > 0) {
						JsonArray attachmentList = getAttachmentList(attachments, obj.get("_id") + "");
						jsonObject.addProperty("id", obj.get("_id") + "");
						jsonObject.addProperty("name", obj.get("name") + "");
						jsonObject.addProperty("value", obj.get("value") + "");
						jsonObject.add("attachements", attachmentList);

					} else {
						jsonObject.addProperty("id", obj.get("_id") + "");
						jsonObject.addProperty("name", obj.get("name") + "");
						jsonObject.addProperty("value", obj.get("value") + "");
					}

					jsonArray.add(jsonObject);
				}
			} catch (Exception dnfe) {
				System.out.println("Exception thrown : " + dnfe.getMessage());
			}

			resultObject.addProperty("id", "all");
			resultObject.add("body", jsonArray);

			return Response.ok(resultObject.toString()).build();
		}

		// check if document exists
		HashMap<String, Object> obj = db.find(HashMap.class, id + "");
		if (obj != null) {
			JsonObject jsonObject = toJsonObject(obj);
			return Response.ok(jsonObject.toString()).build();
		} else {
			return Response.status(Response.Status.NOT_FOUND).build();
		}
	}

	@DELETE
	public Response delete(@QueryParam("id") long id) {

		Database db = null;
		try {
			db = getDB();
		} catch (Exception re) {
			re.printStackTrace();
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		}

		// check if document exist
		HashMap<String, Object> obj = db.find(HashMap.class, id + "");

		if (obj == null) {
			return Response.status(Response.Status.NOT_FOUND).build();
		} else {
			db.remove(obj);

			System.out.println("Delete Successful.");

			return Response.ok("OK").build();
		}
	}

	@PUT
	public Response update(@QueryParam("id") long id, @QueryParam("name") String name,
			@QueryParam("value") String value) {

		Database db = null;
		try {
			db = getDB();
		} catch (Exception re) {
			re.printStackTrace();
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		}

		// check if document exist
		HashMap<String, Object> obj = db.find(HashMap.class, id + "");

		if (obj == null) {
			return Response.status(Response.Status.NOT_FOUND).build();
		} else {
			obj.put("name", name);
			obj.put("value", value);

			db.update(obj);

			System.out.println("Update Successful.");

			return Response.ok("OK").build();
		}
	}

	// This method is periodically called by the scheduler to cleanup the DB
	// from older categories
	@GET
	@Path("/cleanup")
	public Response cleanup() throws Exception {
		Database db = null;
		try {
			db = getDB();
		} catch (Exception re) {
			re.printStackTrace();
			return Response.status(javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR).build();
		}

		JsonArray jsonArray = new JsonArray();

		try {
			// get all the document present in database
			List<HashMap> allDocs = db.view("_all_docs").query(HashMap.class);

			// calculate min modification time, as now - 2 days
			long minModTime = System.currentTimeMillis() - 2 * DAY_MILLISECS;

			System.out.println("Cleanup - Min modification time: " + minModTime);

			for (HashMap doc : allDocs) {

				String doc_id = doc.get("id") + "";
				HashMap<String, Object> obj = db.find(HashMap.class, doc_id);
				double mod_time = (Double) obj.get("modification_date");

				System.out.println("Checking " + obj.get("name") + " modified on " + mod_time);
				if (mod_time < minModTime) {
					// If current doc is older, delete the doc.
					db.remove(obj);
					System.out.println("Delete Successful of obj " + doc_id);
					JsonObject jsonObject = new JsonObject();
					jsonObject.addProperty("id", doc_id);
					jsonObject.addProperty("name", obj.get("name") + "");
					jsonObject.addProperty("value", obj.get("value") + "");
					jsonArray.add(jsonObject);
				}
			}
		} catch (Exception dnfe) {
			System.out.println("Exception thrown : " + dnfe.getMessage());
			dnfe.printStackTrace();
		}

		System.out.println("Cleanup completed, deleted " + jsonArray.size() + " categories");
		return Response.ok(jsonArray.toString()).build();

	}

	private JsonArray getAttachmentList(LinkedTreeMap<String, Object> attachmentList, String docID) {
		JsonArray attachmentArray = new JsonArray();
		String URLTemplate = CloudantClientMgr.getDatabaseURL();

		for (Object key : attachmentList.keySet()) {
			LinkedTreeMap<String, Object> attach = (LinkedTreeMap<String, Object>) attachmentList.get(key);

			JsonObject attachedObject = new JsonObject();
			// set the content type of the attachment
			attachedObject.addProperty("content_type", attach.get("content_type").toString());
			// append the document id and attachment key to the URL
			attachedObject.addProperty("url", URLTemplate + docID + "/" + key);
			// set the key of the attachment
			attachedObject.addProperty("key", key + "");

			// add the attachment object to the array
			attachmentArray.add(attachedObject);
		}

		return attachmentArray;
	}

	private JsonObject toJsonObject(HashMap<String, Object> obj) {
		JsonObject jsonObject = new JsonObject();
		LinkedTreeMap<String, Object> attachments = (LinkedTreeMap<String, Object>) obj.get("_attachments");
		if (attachments != null && attachments.size() > 0) {
			JsonArray attachmentList = getAttachmentList(attachments, obj.get("_id") + "");
			jsonObject.add("attachements", attachmentList);
		}
		jsonObject.addProperty("id", obj.get("_id") + "");
		jsonObject.addProperty("name", obj.get("name") + "");
		jsonObject.addProperty("value", obj.get("value") + "");
		return jsonObject;
	}

	private void saveAttachment(Database db, String id, Part part, String fileName, HashMap<String, Object> obj)
			throws IOException {
		if (part != null) {
			InputStream inputStream = part.getInputStream();
			try {
				db.saveAttachment(inputStream, fileName, part.getContentType(), id, (String) obj.get("_rev"));
			} finally {
				inputStream.close();
			}
		}
	}

	/*
	 * Create a document and Initialize with sample data/attachments
	 */
	private List<HashMap> initializeSampleData(Database db) throws Exception {

		long id = System.currentTimeMillis();
		String name = "Sample category";
		String value = "List of sample files";

		// create a new document
		System.out.println("Creating new document with id : " + id);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("name", name);
		data.put("_id", id + "");
		data.put("value", value);
		data.put("creation_date", new Date().toString());
		data.put("modification_date", System.currentTimeMillis());
		db.save(data);

		// attach the object
		HashMap<String, Object> obj = db.find(HashMap.class, id + "");

		// attachment#1
		File file = new File("Sample.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.write("This is a sample file...");
		writer.flush();
		writer.close();
		FileInputStream fileInputStream = new FileInputStream(file);
		db.saveAttachment(fileInputStream, file.getName(), "text/plain", id + "", (String) obj.get("_rev"));
		fileInputStream.close();

		List<HashMap> allDocs = db.view("_all_docs").query(HashMap.class);
		return allDocs;

	}

	private Database getDB() {
		return CloudantClientMgr.getDB();
	}

	private synchronized void initializeScheduler() {
		if (!schedulerInitialized) {
			String baseURL = getBaseURL();

			try {
				WorkloadSchedulerClient wsClient = new WorkloadSchedulerClient();
				wsClient.setupProcess("JavaCloudant", "Cleanup-"+getAppURI(), "Java Cloudant Sample periodic cleanup",
						baseURL + "/cleanup");

				schedulerInitialized = true;
			} catch (Exception e) {
				System.out.println("Got Exception: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private String getBaseURL() {
		String baseURL = null;

		String uri = getAppURI();
		if (uri!=null) {
			baseURL = "https://" + uri + API_ROOT;
		}

		System.out.println("Base URL: " + baseURL);
		return baseURL;
	}

	private String getAppURI() {
		String uri=null;
		// Get Base URL starting from VCAP_APPLICATION env variable
		String vcapJSONString = System.getenv("VCAP_APPLICATION");
		if (vcapJSONString != null) {
			// parse the VCAP JSON structure
			JsonObject app = new JsonParser().parse(vcapJSONString).getAsJsonObject();
			JsonArray uris = app.get("application_uris").getAsJsonArray();
			uri = uris.get(0).getAsString();
		}
		return uri;
	}

}
