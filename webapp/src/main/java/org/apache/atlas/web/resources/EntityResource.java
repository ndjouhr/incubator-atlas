/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.atlas.web.resources;

import com.google.common.base.Preconditions;
import org.apache.atlas.AtlasClient;
import org.apache.atlas.AtlasException;
import org.apache.atlas.ParamChecker;
import org.apache.atlas.TypeNotFoundException;
import org.apache.atlas.repository.EntityNotFoundException;
import org.apache.atlas.services.MetadataService;
import org.apache.atlas.web.util.Servlets;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;


/**
 * Entity management operations as REST API.
 *
 * An entity is an "instance" of a Type.  Entities conform to the definition
 * of the Type they correspond with.
 */
@Path("entity")
@Singleton
public class EntityResource {

    private static final Logger LOG = LoggerFactory.getLogger(EntityResource.class);
    private static final String TRAIT_NAME = "traitName";

    private final MetadataService metadataService;

    @Context
    UriInfo uriInfo;

    /**
     * Created by the Guice ServletModule and injected with the
     * configured MetadataService.
     *
     * @param metadataService metadata service handle
     */
    @Inject
    public EntityResource(MetadataService metadataService) {
        this.metadataService = metadataService;
    }


    /**
     * Fetch the complete definition of an entity given its GUID.
     *
     * @param guid GUID for the entity
     */
    @GET
    @Path("{guid}")
    @Produces(Servlets.JSON_MEDIA_TYPE)
    public Response getEntityDefinition(@PathParam("guid") String guid) {
        try {
            LOG.debug("Fetching entity definition for guid={} ", guid);
            ParamChecker.notEmpty(guid, "guid cannot be null");
            final String entityDefinition = metadataService.getEntityDefinition(guid);

            JSONObject response = new JSONObject();
            response.put(AtlasClient.REQUEST_ID, Servlets.getRequestId());
            response.put(AtlasClient.GUID, guid);

            Response.Status status = Response.Status.NOT_FOUND;
            if (entityDefinition != null) {
                response.put(AtlasClient.DEFINITION, entityDefinition);
                status = Response.Status.OK;
            } else {
                response.put(AtlasClient.ERROR,
                        Servlets.escapeJsonString(String.format("An entity with GUID={%s} does not exist", guid)));
            }

            return Response.status(status).entity(response).build();

        } catch (EntityNotFoundException e) {
            LOG.error("An entity with GUID={} does not exist", guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.NOT_FOUND));
        } catch (AtlasException | IllegalArgumentException e) {
            LOG.error("Bad GUID={}", guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.BAD_REQUEST));
        } catch (Throwable e) {
            LOG.error("Unable to get instance definition for GUID {}", guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Fetch the complete definition of an entity given its qualified name.
     *
     * @param entityType
     * @param attribute
     * @param value
     */
    @GET
    @Produces(Servlets.JSON_MEDIA_TYPE)
    public Response getEntityDefinitionByAttribute(@QueryParam("type") String entityType,
                                                   @QueryParam("property") String attribute,
                                                   @QueryParam("value") String value) {
        try {
            LOG.debug("Fetching entity definition for type={}, qualified name={}", entityType, value);
            ParamChecker.notEmpty(entityType, "type cannot be null");
            ParamChecker.notEmpty(attribute, "attribute name cannot be null");
            ParamChecker.notEmpty(value, "attribute value cannot be null");

            final String entityDefinition = metadataService.getEntityDefinition(entityType, attribute, value);

            JSONObject response = new JSONObject();
            response.put(AtlasClient.REQUEST_ID, Servlets.getRequestId());

            Response.Status status = Response.Status.NOT_FOUND;
            if (entityDefinition != null) {
                response.put(AtlasClient.DEFINITION, entityDefinition);
                status = Response.Status.OK;
            } else {
                response.put(AtlasClient.ERROR, Servlets.escapeJsonString(String.format("An entity with type={%s}, " +
                        "qualifiedName={%s} does not exist", entityType, value)));
            }

            return Response.status(status).entity(response).build();

        } catch (EntityNotFoundException e) {
            LOG.error("An entity with type={} and qualifiedName={} does not exist", entityType, value, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.NOT_FOUND));
        } catch (AtlasException | IllegalArgumentException e) {
            LOG.error("Bad type={}, qualifiedName={}", entityType, value, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.BAD_REQUEST));
        } catch (Throwable e) {
            LOG.error("Unable to get instance definition for type={}, qualifiedName={}", entityType, value, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Adds property to the given entity id
     * @param guid entity id
     * @param property property to add
     * @param value property's value
     * @return response payload as json
     */
    @PUT
    @Path("{guid}")
    @Consumes(Servlets.JSON_MEDIA_TYPE)
    @Produces(Servlets.JSON_MEDIA_TYPE)
    public Response update(@PathParam("guid") String guid, @QueryParam("property") String property,
            @QueryParam("value") String value) {
        try {
            Preconditions.checkNotNull(property, "Entity property cannot be null");
            Preconditions.checkNotNull(value, "Entity value cannot be null");

            metadataService.updateEntity(guid, property, value);

            JSONObject response = new JSONObject();
            response.put(AtlasClient.REQUEST_ID, Thread.currentThread().getName());
            return Response.ok(response).build();
        } catch (EntityNotFoundException e) {
            LOG.error("An entity with GUID={} does not exist", guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.NOT_FOUND));
        } catch (AtlasException | IllegalArgumentException e) {
            LOG.error("Unable to add property {} to entity id {}", property, guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.BAD_REQUEST));
        } catch (Throwable e) {
            LOG.error("Unable to add property {} to entity id {}", property, guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    // Trait management functions

    /**
     * Gets the list of trait names for a given entity represented by a guid.
     *
     * @param guid globally unique identifier for the entity
     * @return a list of trait names for the given entity guid
     */
    @GET
    @Path("{guid}/traits")
    @Produces(Servlets.JSON_MEDIA_TYPE)
    public Response getTraitNames(@PathParam("guid") String guid) {
        try {
            LOG.debug("Fetching trait names for entity={}", guid);
            final List<String> traitNames = metadataService.getTraitNames(guid);

            JSONObject response = new JSONObject();
            response.put(AtlasClient.REQUEST_ID, Servlets.getRequestId());
            response.put(AtlasClient.GUID, guid);
            response.put(AtlasClient.RESULTS, new JSONArray(traitNames));
            response.put(AtlasClient.COUNT, traitNames.size());

            return Response.ok(response).build();
        } catch (EntityNotFoundException e) {
            LOG.error("An entity with GUID={} does not exist", guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.NOT_FOUND));
        } catch (AtlasException | IllegalArgumentException e) {
            LOG.error("Unable to get trait names for entity {}", guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.BAD_REQUEST));
        } catch (Throwable e) {
            LOG.error("Unable to get trait names for entity {}", guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Adds a new trait to an existing entity represented by a guid.
     *
     * @param guid globally unique identifier for the entity
     */
    @POST
    @Path("{guid}/traits")
    @Consumes(Servlets.JSON_MEDIA_TYPE)
    @Produces(Servlets.JSON_MEDIA_TYPE)
    public Response addTrait(@Context HttpServletRequest request, @PathParam("guid") String guid) {
        try {
            final String traitDefinition = Servlets.getRequestPayload(request);
            LOG.debug("Adding trait={} for entity={} ", traitDefinition, guid);
            metadataService.addTrait(guid, traitDefinition);

            UriBuilder ub = uriInfo.getAbsolutePathBuilder();
            URI locationURI = ub.path(guid).build();

            JSONObject response = new JSONObject();
            response.put(AtlasClient.REQUEST_ID, Servlets.getRequestId());
            response.put(AtlasClient.GUID, guid);

            return Response.created(locationURI).entity(response).build();
        } catch (EntityNotFoundException | TypeNotFoundException e) {
            LOG.error("An entity with GUID={} does not exist", guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.NOT_FOUND));
        } catch (AtlasException | IllegalArgumentException e) {
            LOG.error("Unable to add trait for entity={}", guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.BAD_REQUEST));
        } catch (Throwable e) {
            LOG.error("Unable to add trait for entity={}", guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Deletes a given trait from an existing entity represented by a guid.
     *
     * @param guid      globally unique identifier for the entity
     * @param traitName name of the trait
     */
    @DELETE
    @Path("{guid}/traits/{traitName}")
    @Consumes(Servlets.JSON_MEDIA_TYPE)
    @Produces(Servlets.JSON_MEDIA_TYPE)
    public Response deleteTrait(@Context HttpServletRequest request, @PathParam("guid") String guid,
            @PathParam(TRAIT_NAME) String traitName) {
        LOG.debug("Deleting trait={} from entity={} ", traitName, guid);
        try {
            metadataService.deleteTrait(guid, traitName);

            JSONObject response = new JSONObject();
            response.put(AtlasClient.REQUEST_ID, Servlets.getRequestId());
            response.put(AtlasClient.GUID, guid);
            response.put(TRAIT_NAME, traitName);

            return Response.ok(response).build();
        } catch (EntityNotFoundException | TypeNotFoundException e) {
            LOG.error("An entity with GUID={} does not exist", guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.NOT_FOUND));
        } catch (AtlasException | IllegalArgumentException e) {
            LOG.error("Unable to delete trait name={} for entity={}", traitName, guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.BAD_REQUEST));
        } catch (Throwable e) {
            LOG.error("Unable to delete trait name={} for entity={}", traitName, guid, e);
            throw new WebApplicationException(Servlets.getErrorResponse(e, Response.Status.INTERNAL_SERVER_ERROR));
        }
    }
}
