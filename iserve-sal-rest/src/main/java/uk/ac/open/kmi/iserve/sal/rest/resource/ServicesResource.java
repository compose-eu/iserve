/*
 * Copyright (c) 2013. Knowledge Media Institute - The Open University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.open.kmi.iserve.sal.rest.resource;

import com.google.inject.Inject;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.open.kmi.iserve.sal.exception.SalException;
import uk.ac.open.kmi.iserve.sal.exception.ServiceException;
import uk.ac.open.kmi.iserve.sal.manager.RegistryManager;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

/**
 * Services Resource
 * This Resource is effectively disabled for now given that {@link ReadWriteRouterServlet}
 * catches all requests. It should be re-enabled in the REST Application configuration.
 *
 * @author Dong Liu (Knowledge Media Institute - The Open University)
 * @author Carlos Pedrinaci (Knowledge Media Institute - The Open University)
 */
//@Path("/id/services")
public class ServicesResource {

    private static final Logger log = LoggerFactory.getLogger(ServicesResource.class);

    @Context
    UriInfo uriInfo;

    @Context
    SecurityContext security;

    private final RegistryManager manager;

    @Inject
    public ServicesResource(RegistryManager registryManager) {
        this.manager = registryManager;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces({MediaType.TEXT_HTML})
    public Response addService(@FormDataParam("file") FormDataBodyPart bodyPart,
                               @FormDataParam("file") InputStream file,
                               @HeaderParam("Content-Location") String locationUri) {

        log.debug("Invocation to addService - bodyPart {}, file {}, content-location {}",
                bodyPart, file, locationUri);

        // Check first that the user is allowed to upload a service
        Subject currentUser = SecurityUtils.getSubject();
        if (!currentUser.isPermitted("services:create")) {
            log.warn("User without the appropriate permissions attempted to create a service: " + currentUser.getPrincipal());

            String htmlString = "<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n" +
                    "  <body>\n You have not got the appropriate permissions for creating a service. Please login and ensure you have the correct permissions. </body>\n</html>";

            return Response.status(Status.FORBIDDEN).entity(htmlString).build();
        }

        // The user is allowed to create services
        String mediaType = null;
        List<URI> servicesUris;

        if (bodyPart != null) {
            mediaType = bodyPart.getMediaType().toString();
        } else {
            // TODO: we should obtain/guess the media type
        }

        try {
            if ((locationUri != null) && (!"".equalsIgnoreCase(locationUri))) {
                // There is a location. Just register, don't import
                log.info("Registering the services from {} ", locationUri);
                servicesUris = manager.registerServices(URI.create(locationUri), mediaType);
            } else {
                // There is no location. Import the entire service
                log.info("Importing the services");
                servicesUris = manager.importServices(file, mediaType);
            }
            //		String oauthConsumer = ((SecurityFilter.Authorizer) security).getOAuthConsumer();

            StringBuilder responseBuilder = new StringBuilder()
                    .append("<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n")
                    .append("<body>\n")
                    .append(servicesUris.size()).append(" services added.");

            for (URI svcUri : servicesUris) {
                responseBuilder.append("Service created at <a href='").append(svcUri).append("'>").append(svcUri).append("</a>\n");
            }

            responseBuilder.append("</body>\n</html>");

            return Response.status(Status.CREATED).entity(responseBuilder.toString()).build();
        } catch (ServiceException e) {
            String error = "<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n" +
                    "  <body>\nThere was an error while transforming the service descriptions: " + e.getMessage() + "\n  </body>\n</html>";

            // TODO: Add logging
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build();
        } catch (SalException e) {
            String error = "<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n" +
                    "  <body>\nThere was an error while storing the service: " + e.getMessage() + "\n  </body>\n</html>";

            // TODO: Add logging
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build();
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    log.error("Problems while close the input stream with the service content", e);
                }
            }
        }
    }

    /**
     * Delete a given service
     * <p/>
     * From HTTP Method definition
     * 9.7 DELETE
     * A successful response SHOULD be
     * 200 (OK) if the response includes an entity describing the status,
     * 202 (Accepted) if the action has not yet been enacted, or
     * 204 (No Content) if the action has been enacted but the response does not include an entity.
     *
     * @return
     */
    @DELETE
    @Path("/{uniqueId}/{serviceName}")
    @Produces({MediaType.TEXT_HTML})
    public Response deleteService() {

        // Check first that the user is allowed to upload a service
        Subject currentUser = SecurityUtils.getSubject();
        if (!currentUser.isPermitted("services:delete")) {
            log.warn("User without the appropriate permissions attempted to delete a service: " + currentUser.getPrincipal());

            String htmlString = "<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n" +
                    "  <body>\n You have not got the appropriate permissions for deleting a service. Please login and ensure you have the correct permissions. </body>\n</html>";

            return Response.status(Status.FORBIDDEN).entity(htmlString).build();
        }

        URI serviceUri = uriInfo.getRequestUri();

        String response;
        try {
            if (!manager.getServiceManager().serviceExists(serviceUri)) {
                // The service doesn't exist
                response = "<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n" +
                        "  <body>\n The service " + serviceUri + " is not present in the registry.\n  </body>\n</html>";

                return Response.status(Status.NOT_FOUND).contentLocation(serviceUri).entity(response).build();
            }

            if (manager.unregisterService(serviceUri)) {
                // The service was deleted
                response = "<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n" +
                        "  <body>\n The service <a href='" + serviceUri + "'>" + serviceUri + "</a> has been deleted from the server.\n  </body>\n</html>";

                return Response.status(Status.OK).contentLocation(serviceUri).entity(response).build();
            } else {
                // The service was not deleted
                response = "<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n" +
                        "  <body>\n The service <a href='" + serviceUri + "'>" + serviceUri + "</a> could not be deleted from the server. Try again or contact a server administrator.\n  </body>\n</html>";

                return Response.status(Status.NOT_MODIFIED).contentLocation(serviceUri).entity(response).build();
            }
        } catch (SalException e) {
            response = "<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n" +
                    "  <body>\nThere was an error while deleting the service. Contact the system administrator. \n  </body>\n</html>";

            // TODO: Add logging
            log.error("SAL Exception while deleting service", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(response).build();
        }

    }

    /**
     * Clears the services registry
     * <p/>
     * From HTTP Method definition
     * 9.7 DELETE
     * A successful response SHOULD be
     * 200 (OK) if the response includes an entity describing the status,
     * 202 (Accepted) if the action has not yet been enacted, or
     * 204 (No Content) if the action has been enacted but the response does not include an entity.
     *
     * @return
     */
    @DELETE
    @Produces({MediaType.TEXT_HTML})
    public Response clearServices() {

        // Check first that the user is allowed to upload a service
        Subject currentUser = SecurityUtils.getSubject();
        if (!currentUser.isPermitted("services:delete")) {
            log.warn("User without the appropriate permissions attempted to clear the services: " + currentUser.getPrincipal());

            String htmlString = "<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n" +
                    "  <body>\n You have not got the appropriate permissions for clearing the services. Please login and ensure you have the correct permissions. </body>\n</html>";

            return Response.status(Response.Status.FORBIDDEN).entity(htmlString).build();
        }

        URI serviceUri = uriInfo.getRequestUri();

        String response;
        try {
            if (manager.getServiceManager().clearServices()) {
                // The registry was cleared
                response = "<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n" +
                        "  <body>\n The services have been cleared.\n  </body>\n</html>";

                return Response.status(Response.Status.OK).contentLocation(serviceUri).entity(response).build();
            } else {
                // The registry was not cleared
                response = "<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n" +
                        "  <body>\n The services could not be cleared. Try again or contact a server administrator.\n  </body>\n</html>";

                return Response.status(Response.Status.NOT_MODIFIED).contentLocation(serviceUri).entity(response).build();
            }
        } catch (SalException e) {
            response = "<html>\n  <head>\n    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n  </head>\n" +
                    "  <body>\nThere was an error while clearing the services. Contact the system administrator. \n  </body>\n</html>";

            // TODO: Add logging
            log.error("SAL Exception while deleting service", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(response).build();
        }

    }

}
