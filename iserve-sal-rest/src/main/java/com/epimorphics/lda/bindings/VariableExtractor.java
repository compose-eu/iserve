/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$
*/

package com.epimorphics.lda.bindings;


import com.epimorphics.lda.exceptions.EldaException;
import com.epimorphics.lda.rdfq.Value;
import com.epimorphics.vocabs.API;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.epimorphics.util.RDFUtils.getStringValue;

/**
 * Extracts and binds variables from API specifications.
 *
 * @author chris
 */
public class VariableExtractor {

    static Logger log = LoggerFactory.getLogger(VariableExtractor.class);

    public static Bindings findAndBindVariables(Resource root) {
        return findAndBindVariables(new Bindings(), root);
    }

    public static Bindings findAndBindVariables(Bindings bound, Resource root) {
        findVariables(root, bound);
        return bound;
    }

    /**
     * Find variable declarations hanging off <code>root</code>. Definitions
     * that are not literals-containing-{ are stored directly into
     * <code>bound</code>. Otherwise, the name and its literal value are
     * stored into <code>toDo</code> for later evaluation.
     */
    public static void findVariables(Resource root, Bindings bound) {
        for (Statement s : root.listProperties(API.variable).toList()) {
            Resource v = s.getResource();
            String name = getStringValue(v, API.name, null);
            String language = getStringValue(v, API.lang, "");
            String type = getStringValue(v, API.type, null);
            Statement value = v.getProperty(API.value);
            if (type == null && value != null && value.getObject().isLiteral())
                type = emptyIfNull(value.getObject().asNode().getLiteralDatatypeURI());
            if (type == null && value != null && value.getObject().isURIResource())
                type = RDFS.Resource.getURI();
            if (type == null) {
                log.warn("type mysteriously null, needs sorting soon");
                type = "";
            }
            String valueString = getValueString(v, language, type);
            Value var = new Value(valueString, language, type);
            bound.put(name, var);
        }
    }

    private static String emptyIfNull(String s) {
        return s == null ? "" : s;
    }

    private static String getValueString(Resource v, String language, String type) {
        Statement s = v.getProperty(API.value);
        if (s == null) return null;
        Node object = s.getObject().asNode();
        if (object.isURI()) return object.getURI();
        if (object.isLiteral()) return object.getLiteralLexicalForm();
        EldaException.Broken("cannot convert " + object + " to RDFQ type.");
        return null;
    }
}
