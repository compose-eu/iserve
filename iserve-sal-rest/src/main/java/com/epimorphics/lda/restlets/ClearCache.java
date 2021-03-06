/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$
*/

package com.epimorphics.lda.restlets;

import com.epimorphics.lda.cache.Cache;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Path("/control/clear-cache")
public class ClearCache {
    @GET
    @Produces("text/plain")
    public Response clearCache() { // should really not use GET for this
        Cache.Registry.clearAll();
        return RouterRestlet.returnAs("caches cleared.", "text/plain");
    }
}
