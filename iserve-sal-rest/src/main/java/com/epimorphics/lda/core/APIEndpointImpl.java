/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$
    
    File:        APIEndpointImpl.java
    Created by:  Dave Reynolds
    Created on:  31 Jan 2010
*/

package com.epimorphics.lda.core;

import com.epimorphics.jsonrdf.Context;
import com.epimorphics.lda.bindings.Bindings;
import com.epimorphics.lda.cache.Cache;
import com.epimorphics.lda.cache.Cache.Registry;
import com.epimorphics.lda.core.APIResultSet.MergedModels;
import com.epimorphics.lda.exceptions.APIException;
import com.epimorphics.lda.exceptions.EldaException;
import com.epimorphics.lda.exceptions.QueryParseException;
import com.epimorphics.lda.query.APIQuery;
import com.epimorphics.lda.query.ContextQueryUpdater;
import com.epimorphics.lda.query.WantsMetadata;
import com.epimorphics.lda.renderers.Factories.FormatNameAndType;
import com.epimorphics.lda.renderers.Renderer;
import com.epimorphics.lda.renderers.RendererFactory;
import com.epimorphics.lda.shortnames.CompleteContext;
import com.epimorphics.lda.shortnames.ShortnameService;
import com.epimorphics.lda.sources.Source;
import com.epimorphics.lda.specs.APIEndpointSpec;
import com.epimorphics.lda.specs.APISpec;
import com.epimorphics.lda.specs.EndpointDetails;
import com.epimorphics.lda.vocabularies.EXTRAS;
import com.epimorphics.util.MediaType;
import com.epimorphics.util.RDFUtils;
import com.epimorphics.util.Triad;
import com.epimorphics.util.URIUtils;
import com.epimorphics.vocabs.API;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements a single endpoint for an API.
 *
 * @author <a href="mailto:der@epimorphics.com">Dave Reynolds</a>
 * @version $Revision: $
 */
public class APIEndpointImpl implements APIEndpoint {

    protected final APIEndpointSpec spec;
    protected final boolean specWantsContext;
    protected final Cache cache;

    static Logger log = LoggerFactory.getLogger(APIEndpointImpl.class);

    public APIEndpointImpl(APIEndpointSpec spec) {
        this(spec, Registry.cacheFor(spec.getCachePolicyName(), spec.getAPISpec().getDataSource()));
        // System.err.println( ">> cache is " + cache.summary() );
    }

    public APIEndpointImpl(APIEndpointSpec spec, Cache cache) {
        this.spec = spec;
        this.cache = cache;
        // System.err.println( ">> endpoint with cache " + cache.summary() );
        this.specWantsContext = spec.wantsContext();
    }

    @Override
    public String toString() {
        return spec.toString();
    }

    static final Bindings defaults = new Bindings().put("_resourceRoot", "{_APP}/lda-assets/");

    @Override
    public Bindings defaults() {
        return defaults;
    }

    @Override
    public Triad<APIResultSet, Map<String, String>, Bindings> call(Request r) {
        Bindings b = r.context.copyWithDefaults(spec.getBindings());
        APIQuery query = spec.getBaseQuery();
        //
        if (b.getValueString("callback") != null && !"json".equals(r.format))
            EldaException.BadRequest("callback specified but format '" + r.format + "' is not JSON.");
        //
        View view = buildQueryAndView(b, query);
        //
        APIResultSet unfiltered = query.runQuery(r.c, spec.getAPISpec(), cache, b, view);
        APIResultSet filtered = unfiltered.getFilteredSet(view, query.getDefaultLanguage());
        filtered.setNsPrefixes(spec.getAPISpec().getPrefixMap());
        //
        Context context = spec.getAPISpec().getShortnameService().asContext();
        CompleteContext cc = new CompleteContext(r.mode, context, filtered.getModelPrefixes());
        createMetadata(r, cc, filtered, b, query);
        cc.include(filtered.getMergedModel());
        Triad<APIResultSet, Map<String, String>, Bindings> result = new Triad<APIResultSet, Map<String, String>, Bindings>(filtered, cc.Do(), b);
        return result;
    }

    private View buildQueryAndView(Bindings context, APIQuery query) {
        ShortnameService sns = spec.getAPISpec().getShortnameService();
        int endpointType = isListEndpoint() ? ContextQueryUpdater.ListEndpoint : ContextQueryUpdater.ItemEndpoint;
        ContextQueryUpdater cq = new ContextQueryUpdater(endpointType, context, spec, sns, query);
        try {
            return cq.updateQueryAndConstructView(query.deferredFilters);
        } catch (APIException e) {
            throw new QueryParseException("query construction failed", e);
        }
    }

    /**
     * Return a metadata description for the query that would be run by this endpoint
     */
    @Override
    public Resource getMetadata(Bindings context, URI ru, String formatName, Model metadata) {
        APIQuery query = spec.getBaseQuery();
        View _ignored = buildQueryAndView(context, query);
        metadata.setNsPrefix("api", API.getURI());
        Resource meta = metadata.createResource(URIUtils.withoutPageParameters(ru).toString());
        APISpec aSpec = spec.getAPISpec();
        meta.addProperty(EXTRAS.sparqlQuery, query.getQueryString(aSpec, context));
        aSpec.getDataSource().addMetadata(meta);
        meta.addProperty(EXTRAS.listURL, meta);
        meta.addProperty(RDFS.comment, "Metadata describing the query and source for endpoint " + spec.getURITemplate());
        return meta;
    }

    private boolean isListEndpoint() {
        return spec.isListEndpoint();
    }

    private String createDefinitionURI(URI ru, Resource uriForSpec, String template, String expanded) {
        String pseudoTemplate = template.replaceAll("\\{([A-Za-z0-9]+)\\}", "_$1");
        if (pseudoTemplate.startsWith("http:")) {
            // Avoid special case from the TestAPI uriTemplates, qv.
            return pseudoTemplate + "/meta";
        }
        String other = ru.toString().replace(expanded, "/meta" + pseudoTemplate);
//		System.err.println( ">> createDefinitionURI" );
//		System.err.println( ">> ru: " + ru );
//		System.err.println( ">> pseudoTemplate: " + template );
//		System.err.println( ">> template: " + pseudoTemplate );
//		System.err.println( ">> uriForSpec: " + uriForSpec );
//		System.err.println( ">> RESULT: " + replaced );
//		System.err.println( ">> OTHER:  " + other );
//		System.err.println( ">>" );
        return other;
    }

    private void createMetadata(APIEndpoint.Request r, CompleteContext cc, APIResultSet rs, Bindings bindings, APIQuery query) {
        boolean suppress_IPTO = bindings.getAsString("_suppress_ipto", "no").equals("yes");
        boolean exceptionIfEmpty = bindings.getAsString("_exceptionIfEmpty", "yes").equals("yes");
        //
        String format = r.format;
        //
        boolean listEndpoint = isListEndpoint();
        if (rs.isEmpty() && exceptionIfEmpty && !listEndpoint) EldaException.NoItemFound();
        //
        MergedModels mergedModels = rs.getModels();
        Model metaModel = mergedModels.getMetaModel();
        cc.include(metaModel);
        //
        Resource thisMetaPage = metaModel.createResource(r.requestURI.toString());
        Resource uriForSpec = metaModel.createResource(spec.getSpecificationURI());
        //
        int page = query.getPageNumber();
        int perPage = query.getPageSize();
        //
        String template = spec.getURITemplate();
        Set<String> formatNames = spec.getRendererFactoryTable().formatNames();
        rs.setContentLocation(URIUtils.changeFormatSuffix(r.requestURI, formatNames, format));
        rs.setRoot(thisMetaPage);
        //
        List<Resource> resultList = rs.getResultList();
        boolean hasMorePages = !rs.isCompleted;
        SetsMetadata setsMeta = (SetsMetadata) rs;
        WantsMetadata wantsMeta = (WantsMetadata) query;
        String viewQuery = rs.getDetailsQuery();
        APISpec apiSpec = spec.getAPISpec();
        Source source = apiSpec.getDataSource();
        String selectQuery = query.getQueryString(apiSpec, bindings);
        Set<String> viewNames = spec.getExplicitViewNames();
        EndpointDetails details = (EndpointDetails) spec;
        Set<FormatNameAndType> formats = spec.getRendererFactoryTable().getFormatNamesAndTypes();
        URI uriForList = URIUtils.withoutPageParameters(r.requestURI);
        //
        Resource uriForDefinition = metaModel.createResource(createDefinitionURI(uriForList, uriForSpec, template, bindings.expandVariables(template)));
        EndpointMetadata.addAllMetadata
                (mergedModels
                        , r.requestURI
                        , uriForDefinition
                        , bindings
                        , cc
                        , suppress_IPTO
                        , thisMetaPage
                        , page
                        , perPage
                        , hasMorePages
                        , resultList
                        , setsMeta
                        , wantsMeta
                        , selectQuery
                        , viewQuery
                        , source
                        , viewNames
                        , formats
                        , details
                );
    }

    /**
     * The URI template at which this APIEndpoint should be attached
     */
    @Override
    public String getURITemplate() {
        return spec.getURITemplate();
    }

    /**
     * Return the specification for this endpoint
     */
    @Override
    public APIEndpointSpec getSpec() {
        return spec;
    }

    /**
     * Answer the SELECT query that would be used in the current
     * state of this endpoint to find the iterms of interest.
     */
    public String getSelectQuery() {
        return spec.getBaseQuery().assembleSelectQuery(RDFUtils.noPrefixes);
    }

    /**
     * Answer a Renderer of the format named <code>name</code>, configured
     * with the appropriate shortname service.
     */
    @Override
    public Renderer getRendererNamed(String name) {
        RendererFactory s = spec.getRendererFactoryTable().getFactoryByName(name);
        return configure(s);
    }

    /**
     * Answer a Renderer appropriate to the given MediaType <code>mt</code>,
     * configured with the appropriate shortname service.
     */
    @Override
    public Renderer getRendererByType(MediaType mt) {
        RendererFactory s = spec.getRendererFactoryTable().getFactoryByType(mt);
        return configure(s);
    }

    private Map<RendererFactory, Renderer> ready = new HashMap<RendererFactory, Renderer>();

    /**
     * Only build a renderer for a given factory once.
     */
    private Renderer configure(RendererFactory s) {
        if (s == null) return null;
        Renderer r = ready.get(s);
        if (r == null) ready.put(s, r = s.buildWith(this, getSpec().getAPISpec().getShortnameService()));
        return r;
    }
}

