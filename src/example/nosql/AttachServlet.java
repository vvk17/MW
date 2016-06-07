package example.nosql;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import com.cloudant.client.api.Database;
import com.google.gson.JsonObject;

@WebServlet("/attach")
@MultipartConfig()
public class AttachServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Part part = request.getPart("file");

        String id = request.getParameter("id");
        String name = request.getParameter("name");
        String value = request.getParameter("value");
        String fileName = request.getParameter("filename");

        Database db = null;
        try {
            db = CloudantClientMgr.getDB();
        } catch (Exception re) {
            re.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        ResourceServlet servlet = new ResourceServlet();

        JsonObject resultObject = servlet.create(db, id, name, value, part, fileName);

        System.out.println("Upload completed.");

        response.getWriter().println(resultObject.toString());
    }

}
