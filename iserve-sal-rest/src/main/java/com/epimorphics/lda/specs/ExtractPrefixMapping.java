/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$
*/

package com.epimorphics.lda.specs;

import com.epimorphics.vocabs.API;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.shared.PrefixMapping;

import static com.epimorphics.util.RDFUtils.getStringValue;

public class ExtractPrefixMapping {

    /**
     * Answer a prefix mapping containing (a) all the prefixes from the model of the
     * specification resource (b) all the prefixes P of namespace X where
     * <p/>
     * specification prefixMapping [api:prefix P; api:namespace N]
     */
    public static PrefixMapping from(Resource specification) {
        PrefixMapping pm = PrefixMapping.Factory.create();
        Model model = specification.getModel();
        pm.setNsPrefixes(model);
        NodeIterator ni = model.listObjectsOfProperty(specification, API.prefixMapping);
        while (ni.hasNext()) {
            RDFNode n = ni.next();
            if (n.isResource()) {
                Resource pmr = (Resource) n;
                String prefix = getStringValue(pmr, API.prefix);
                String uri = getStringValue(pmr, API.namespace);
                if (prefix != null && uri != null) {
                    pm.setNsPrefix(prefix, uri);
                } else {
                    APISpec.log.error("Ignoring ill-structured prefix mapping " + prefix + " :: " + uri);
                }
            } else {
                APISpec.log.error("Ignoring non-structured prefix mapping: " + n);
            }
        }
        return pm;
    }

}
