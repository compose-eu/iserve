package com.epimorphics.lda.core;

import com.hp.hpl.jena.rdf.model.Model;

/**
 * Insterface for classes that accept named metadata.
 */
public interface SetsMetadata {

    public void setMetadata(String type, Model meta);

}
