package org.hadatac.entity.pojo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.ByteArrayOutputStream;

import org.apache.commons.text.WordUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetRewindable;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateProcessor;
import org.apache.jena.update.UpdateRequest;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.hadatac.utils.CollectionUtil;
import org.hadatac.utils.NameSpaces;
import org.hadatac.utils.FirstLabel;
import org.hadatac.metadata.loader.URIUtils;
import org.hadatac.entity.pojo.ObjectCollection;
import org.labkey.remoteapi.CommandException;

import org.hadatac.console.http.SPARQLUtils;
import org.hadatac.console.models.Facet;
import org.hadatac.console.models.FacetHandler;
import org.hadatac.console.models.Pivot;
import org.hadatac.metadata.loader.LabkeyDataHandler;

public class ObjectCollection extends HADatAcThing implements Comparable<ObjectCollection> {

    public static String SUBJECT_COLLECTION = "http://hadatac.org/ont/hasco/SubjectGroup";
    public static String SAMPLE_COLLECTION = "http://hadatac.org/ont/hasco/SampleCollection";
    public static String LOCATION_COLLECTION = "http://hadatac.org/ont/hasco/LocationCollection";
    public static String TIME_COLLECTION = "http://hadatac.org/ont/hasco/TimeCollection";

    public static String INDENT1 = "   ";
    public static String INSERT_LINE1 = "INSERT DATA {  ";
    public static String DELETE_LINE1 = "DELETE WHERE {  ";
    public static String LINE3 = INDENT1 + "a         hasco:ObjectCollection;  ";
    public static String DELETE_LINE3 = INDENT1 + " ?p ?o . ";
    public static String LINE_LAST = "}  ";
    private String studyUri = "";
    private String hasScopeUri = "";    
    private String hasGroundingLabel = "";
    private String hasSOCReference = "";
    private String hasRoleLabel = "";
    private List<String> spaceScopeUris = null;
    private List<String> timeScopeUris = null;
    private List<String> objectUris = new ArrayList<String>();

    public ObjectCollection() {
        this.uri = "";
        this.typeUri = "";
        this.label = "";
        this.comment = "";
        this.studyUri = "";
        this.hasScopeUri = "";
        this.hasGroundingLabel = "";
        this.hasSOCReference = "";
        this.spaceScopeUris = new ArrayList<String>();
        this.timeScopeUris = new ArrayList<String>();
    }

    public ObjectCollection(String uri,
            String typeUri,
            String label,
            String comment,
            String studyUri,
            String hasScopeUri,
            String hasGroundingLabel,
            String hasSOCReference,
            List<String> spaceScopeUris,
            List<String> timeScopeUris) {
        this.setUri(uri);
        this.setTypeUri(typeUri);
        this.setLabel(label);
        this.setComment(comment);
        this.setStudyUri(studyUri);
        this.setHasScopeUri(hasScopeUri);
        this.setGroundingLabel(hasGroundingLabel);
        this.setSOCReference(hasSOCReference);
        this.setSpaceScopeUris(spaceScopeUris);
        this.setTimeScopeUris(timeScopeUris);
    }

    public ObjectCollection(String uri,
            String typeUri,
            String label,
            String comment,
            String studyUri) {
        this.setUri(uri);
        this.setTypeUri(typeUri);
        this.setLabel(label);
        this.setComment(comment);
        this.setStudyUri(studyUri);
        this.setHasScopeUri("");
        this.setSpaceScopeUris(spaceScopeUris);
        this.setTimeScopeUris(timeScopeUris);
    }

    @Override
    public boolean equals(Object o) {
        if((o instanceof ObjectCollection) && (((ObjectCollection)o).getUri().equals(this.getUri()))) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return getUri().hashCode();
    }

    @Override
    public int compareTo(ObjectCollection another) {
        return this.getUri().compareTo(another.getUri());
    }

    public ObjectCollectionType getObjectCollectionType() {
        if (typeUri == null || typeUri.equals("")) {
            return null;
        }
        ObjectCollectionType ocType = ObjectCollectionType.find(typeUri);
        return ocType;    
    }

    public String getStudyUri() {
        return studyUri;
    }

    public Study getStudy() {
        if (studyUri == null || studyUri.equals("")) {
            return null;
        }
        return Study.find(studyUri);
    }

    public boolean isDomainCollection() {
        if (typeUri == null || typeUri.equals("")) {
            return false;
        }
        return (typeUri.equals(SUBJECT_COLLECTION) || typeUri.equals(SAMPLE_COLLECTION));
    }

    public boolean isLocationCollection() {
        if (typeUri == null || typeUri.equals("")) {
            return false;
        }
        return typeUri.equals(LOCATION_COLLECTION);
    }

    public boolean isTimeCollection() {
        if (typeUri == null || typeUri.equals("")) {
            return false;
        }
        return typeUri.equals(TIME_COLLECTION);
    }

    public void setStudyUri(String studyUri) {
        this.studyUri = studyUri;
    }

    public List<String> getObjectUris() {
        return objectUris;
    }

    public String getUriFromOriginalId(String originalId) {
        if (originalId == null || originalId.equals("")) {
            return "";
        }
        for (StudyObject obj : this.getObjects()) {
            if (originalId.equals(obj.getOriginalId())) {
                return obj.getUri();
            }
        }
        return "";
    }
    public List<StudyObject> getObjects() {
        List<StudyObject> resp = new ArrayList<StudyObject>();
        if (objectUris == null || objectUris.size() <=0) {
            return resp;
        }
        for (String uri : objectUris) {
            StudyObject obj = StudyObject.find(uri);
            if (obj != null) {
                resp.add(obj);
            }
        }
        return resp;
    }

    public void setObjectUris(List<String> objectUris) {
        this.objectUris = objectUris;
    }

    public String getHasScopeUri() {
        return hasScopeUri;
    }

    public ObjectCollection getHasScope() {
        if (hasScopeUri == null || hasScopeUri.equals("")) {
            return null;
        }
        return ObjectCollection.find(hasScopeUri);
    }

    public void setHasScopeUri(String hasScopeUri) {
        this.hasScopeUri = hasScopeUri;
    }

    public void setSOCReference(String hasSOCReference) {
        this.hasSOCReference = hasSOCReference;
    }

    public String getSOCReference() {
        return hasSOCReference;
    }

    public void setGroundingLabel(String hasGroundingLabel) {
        this.hasGroundingLabel = hasGroundingLabel;
    }

    public String getGroundingLabel() {
        return hasGroundingLabel;
    }

    public void setRoleLabel(String roleLabel) {
        this.hasRoleLabel = roleLabel;
    }

    public String getRoleLabel() {
        return hasRoleLabel;
    }

    public List<String> getSpaceScopeUris() {
        return spaceScopeUris;
    }

    public List<ObjectCollection> getSpaceScopes() {
        if (spaceScopeUris == null || spaceScopeUris.isEmpty()) {
            return null;
        }
        List<ObjectCollection> spaceScopes = new ArrayList<ObjectCollection>();
        for (String scopeUri : spaceScopeUris) {
            ObjectCollection oc = ObjectCollection.find(scopeUri);
            if (oc != null) {
                spaceScopes.add(oc);
            }
        }
        return spaceScopes;
    }

    public void setSpaceScopeUris(List<String> spaceScopeUris) {
        this.spaceScopeUris = spaceScopeUris;
    }

    public List<String> getTimeScopeUris() {
        return timeScopeUris;
    }

    public List<ObjectCollection> getTimeScopes() {
        if (timeScopeUris == null || timeScopeUris.equals("")) {
            return null;
        }
        List<ObjectCollection> timeScopes = new ArrayList<ObjectCollection>();
        for (String scopeUri : timeScopeUris) {
            ObjectCollection oc = ObjectCollection.find(scopeUri);
            if (oc != null) {
                timeScopes.add(oc);
            }
        }
        return timeScopes;
    }

    public void setTimeScopeUris(List<String> timeScopeUris) {
        this.timeScopeUris = timeScopeUris;
    }

    public boolean isConnected(ObjectCollection oc) {

        // Check if oc is valid
        if (oc.getUri() == null || oc.getUri().equals("")) {
            return false;
        }

        // Check if oc is in scope of current object collection
        if (this.hasScopeUri != null && !this.hasScopeUri.equals("")) {
            ObjectCollection domainScope = ObjectCollection.find(this.hasScopeUri);
            if (oc.equals(domainScope)) {
                return true;
            }
        }
        if (this.getTimeScopes() != null && this.getTimeScopes().size() > 0) {
            List<ObjectCollection> timeScopes = this.getTimeScopes();
            if (timeScopes.contains(oc)) {
                return true;
            }
        }

        // Check if current is in scope of oc
        if (oc.getHasScopeUri() != null && !oc.getHasScopeUri().equals("")) {
            ObjectCollection ocDomainScope = ObjectCollection.find(oc.hasScopeUri);
            if (this.equals(ocDomainScope)) {
                return true;
            }
        }
        if (oc.getTimeScopes() != null && oc.getTimeScopes().size() > 0) {
            List<ObjectCollection> ocTimeScopes = oc.getTimeScopes();
            if (ocTimeScopes.contains(this)) {
                return true;
            }
        }

        // otherwise there is no connection
        return false;
    }

    public boolean inUriList(List<String> selected) {
        String uriAdjusted = uri.replace("<","").replace(">","");
        for (String str : selected) {
            if (uriAdjusted.equals(str)) {
                return true;
            }
        }
        return false;
    }

    public int getCollectionSize(){
        String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() + 
                "SELECT (count(*) as ?count) WHERE { " + 
                "   ?uri hasco:isMemberOf  <" + this.getUri() + "> . " +
                " } ";

        ResultSetRewindable resultsrw = SPARQLUtils.select(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), queryString);

        int count = 0;
        while (resultsrw.hasNext()) {
            QuerySolution soln = resultsrw.next();
            if (soln != null && soln.getLiteral("count") != null) {
                count += soln.getLiteral("count").getInt();
            }
            else {
                System.out.println("[ObjectCollection] getCollectionSize(): Error!");
                return -1;
            }
        }
        return count;
    }// /getCollectionSize()

    private static List<String> retrieveSpaceScope(String oc_uri) {
        List<String> scopeUris = new ArrayList<String>();
        String scopeUri = ""; 
        String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() + 
                "SELECT ?spaceScopeUri WHERE { \n" + 
                " <" + oc_uri + "> hasco:hasSpaceScope ?spaceScopeUri . \n" + 
                "}";

        ResultSetRewindable resultsrw = SPARQLUtils.select(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), queryString);

        while (resultsrw.hasNext()) {
            QuerySolution soln = resultsrw.next();
            if (soln != null) {
                try {
                    if (soln.getResource("spaceScopeUri") != null && soln.getResource("spaceScopeUri").getURI() != null) {
                        scopeUri = soln.getResource("spaceScopeUri").getURI();
                        if (scopeUri != null && !scopeUri.equals("")) {
                            scopeUris.add(scopeUri);
                        }
                    }
                } catch (Exception e1) {
                }
            }
        }

        return scopeUris;
    }

    private static List<String> retrieveTimeScope(String oc_uri) {
        List<String> scopeUris = new ArrayList<String>();
        String scopeUri = "";
        String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() + 
                "SELECT  ?timeScopeUri WHERE { " + 
                " <" + oc_uri + "> hasco:hasTimeScope ?timeScopeUri . " + 
                "}";

        ResultSetRewindable resultsrw = SPARQLUtils.select(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), queryString);

        while (resultsrw.hasNext()) {
            QuerySolution soln = resultsrw.next();
            if (soln != null) {
                try {
                    if (soln.getResource("timeScopeUri") != null && soln.getResource("timeScopeUri").getURI() != null) {
                        scopeUri = soln.getResource("timeScopeUri").getURI();
                        if (scopeUri != null && !scopeUri.equals("")) {
                            scopeUris.add(scopeUri);
                        }
                    }
                } catch (Exception e1) {
                }
            }
        }

        return scopeUris;
    }

    public static ObjectCollection find(String oc_uri) {
        ObjectCollection oc = null;

        String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() + 
                "SELECT ?ocType ?comment ?studyUri ?hasScopeUri ?hasSOCReference ?hasGroundingLabel ?spaceScopeUri ?timeScopeUri WHERE { \n" + 
                "    <" + oc_uri + "> a ?ocType . \n" + 
                "    <" + oc_uri + "> hasco:isMemberOf ?studyUri . \n" + 
                "    OPTIONAL { <" + oc_uri + "> rdfs:comment ?comment } . \n" + 
                "    OPTIONAL { <" + oc_uri + "> hasco:hasScope ?hasScopeUri } . \n" + 
                "    OPTIONAL { <" + oc_uri + "> hasco:hasSOCReference ?hasSOCReference } . \n" + 
                "    OPTIONAL { <" + oc_uri + "> hasco:hasGroundingLabel ?hasGroundingLabel } . \n" + 
                "}";

        ResultSetRewindable resultsrw = SPARQLUtils.select(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), queryString);

        if (!resultsrw.hasNext()) {
            System.out.println("[WARNING] ObjectCollection. Could not find OC with URI: <" + oc_uri + ">");
            return oc;
        }

        String typeStr = "";
        String labelStr = "";
        String studyUriStr = "";
        String commentStr = "";
        String hasScopeUriStr = "";
        String hasGroundingLabelStr = "";
        String hasSOCReferenceStr = "";
        List<String> spaceScopeUrisStr = new ArrayList<String>();
        List<String> timeScopeUrisStr = new ArrayList<String>();

        while (resultsrw.hasNext()) {
            QuerySolution soln = resultsrw.next();
            if (soln != null) {

                try {
                    if (soln.getResource("ocType") != null && soln.getResource("ocType").getURI() != null) {
                        typeStr = soln.getResource("ocType").getURI();
                    }
                } catch (Exception e1) {
                    typeStr = "";
                }

                labelStr = FirstLabel.getLabel(oc_uri);

                try {
                    if (soln.getResource("studyUri") != null && soln.getResource("studyUri").getURI() != null) {
                        studyUriStr = soln.getResource("studyUri").getURI();
                    }
                } catch (Exception e1) {
                    studyUriStr = "";
                }

                try {
                    if (soln.getLiteral("comment") != null && soln.getLiteral("comment").getString() != null) {
                        commentStr = soln.getLiteral("comment").getString();
                    }
                } catch (Exception e1) {
                    commentStr = "";
                }

                try {
                    if (soln.getResource("hasScopeUri") != null && soln.getResource("hasScopeUri").getURI() != null) {
                        hasScopeUriStr = soln.getResource("hasScopeUri").getURI();
                    }
                } catch (Exception e1) {
                    hasScopeUriStr = "";
                }

                try {
                    if (soln.getLiteral("hasGroundingLabel") != null && soln.getLiteral("hasGroundingLabel").getString() != null) {
                        hasGroundingLabelStr = soln.getLiteral("hasGroundingLabel").getString();
                    }
                } catch (Exception e1) {
                    hasGroundingLabelStr = "";
                }

                try {
                    if (soln.getLiteral("hasSOCReference") != null && soln.getLiteral("hasSOCReference").getString() != null) {
                        hasSOCReferenceStr = soln.getLiteral("hasSOCReference").getString();
                    }
                } catch (Exception e1) {
                    hasSOCReferenceStr = "";
                }

                spaceScopeUrisStr = retrieveSpaceScope(oc_uri);

                timeScopeUrisStr = retrieveTimeScope(oc_uri);

                oc = new ObjectCollection(oc_uri, typeStr, labelStr, commentStr, studyUriStr, hasScopeUriStr, hasGroundingLabelStr, hasSOCReferenceStr, spaceScopeUrisStr, timeScopeUrisStr);
            }
        }

        // retrieve URIs of objects that are member of the collection
        String queryMemberStr = NameSpaces.getInstance().printSparqlNameSpaceList() + 
                "SELECT  ?uriMember WHERE { \n" + 
                "    ?uriMember hasco:isMemberOf <" + oc_uri + "> . \n" + 
                "}";

        ResultSetRewindable resultsrwMember = SPARQLUtils.select(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), queryMemberStr);

        if (resultsrwMember.hasNext()) {
            String uriMemberStr = "";

            while (resultsrwMember.hasNext()) {
                QuerySolution soln = resultsrwMember.next();
                if (soln != null) {
                    try {
                        if (soln.getResource("uriMember") != null && soln.getResource("uriMember").getURI() != null) {
                            uriMemberStr = soln.getResource("uriMember").getURI();
                            oc.getObjectUris().add(uriMemberStr);
                        }
                    } catch (Exception e1) {
                        uriMemberStr = "";
                    }
                }
            }
        }

        return oc;
    }

    public static List<ObjectCollection> findAll() {
        List<ObjectCollection> oc_list = new ArrayList<ObjectCollection>();

        String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() + 
                "SELECT ?uri WHERE { " + 
                "   ?ocType rdfs:subClassOf+ hasco:ObjectCollection . " +
                "   ?uri a ?ocType . } ";

        ResultSetRewindable resultsrw = SPARQLUtils.select(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), queryString);

        while (resultsrw.hasNext()) {
            QuerySolution soln = resultsrw.next();
            if (soln != null && soln.getResource("uri").getURI() != null) { 
                ObjectCollection sc = ObjectCollection.find(soln.getResource("uri").getURI());
                oc_list.add(sc);
            }
        }

        return oc_list;
    }

    public static List<ObjectCollection> findDomainByStudyUri(String studyUri) {
        List<ObjectCollection> ocList = new ArrayList<ObjectCollection>();
        for (ObjectCollection oc : ObjectCollection.findByStudyUri(studyUri)) {
            if (oc.isDomainCollection()) {
                ocList.add(oc);
            }
        }
        return ocList;
    }

    public static List<ObjectCollection> findByStudyUri(String studyUri) {
        if (studyUri == null) {
            return null;
        }
        List<ObjectCollection> ocList = new ArrayList<ObjectCollection>();

        String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() + 
                "SELECT ?uri WHERE { \n" + 
                "   ?ocType rdfs:subClassOf+ hasco:ObjectCollection . \n" +
                "   ?uri a ?ocType . \n" +
                "   ?uri hasco:isMemberOf <" + studyUri + "> . \n" +
                " } ";

        ResultSetRewindable resultsrw = SPARQLUtils.select(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), queryString);

        while (resultsrw.hasNext()) {
            QuerySolution soln = resultsrw.next();
            if (soln != null && soln.getResource("uri").getURI() != null) { 
                ObjectCollection oc = ObjectCollection.find(soln.getResource("uri").getURI());
                ocList.add(oc);
            }
        }
        return ocList;
    }

    public static Map<String, String> labelsByStudyUri(String studyUri) {
        if (studyUri == null) {
            return null;
        }
        Map<String, String> labelsMap = new HashMap<String, String>();
        List<ObjectCollection> ocList = findByStudyUri(studyUri);

        for (ObjectCollection oc : ocList) {
            if (oc.getGroundingLabel() != null && !oc.getGroundingLabel().equals("")) {
                labelsMap.put(oc.getSOCReference(), oc.getGroundingLabel());
            } else {
            }
        }

        return labelsMap;
    }

    public static String findByStudyUriJSON(String studyUri) {
        if (studyUri == null) {
            return null;
        }
        String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() + 
                "SELECT ?uri ?label WHERE { \n" + 
                "   ?ocType rdfs:subClassOf+ hasco:ObjectCollection . \n" +
                "   ?uri a ?ocType . \n" +
                "   ?uri hasco:isMemberOf <" + studyUri + "> . \n" +
                "   OPTIONAL { ?uri rdfs:label ?label } . \n" +
                " } ";

        Query query = QueryFactory.create(queryString);

        QueryExecution qexec = QueryExecutionFactory.sparqlService(
                CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), query);
        ResultSet results = qexec.execSelect();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ResultSetFormatter.outputAsJSON(outputStream, results);
        qexec.close();

        try {
            return outputStream.toString("UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
    
    @Override
    public Map<HADatAcThing, List<HADatAcThing>> getTargetFacets(
            Facet facet, FacetHandler facetHandler) {
        return getTargetFacetsFromSolr(facet, facetHandler);
    }

    @Override
    public Map<HADatAcThing, List<HADatAcThing>> getTargetFacetsFromSolr(
            Facet facet, FacetHandler facetHandler) {

        SolrQuery query = new SolrQuery();
        String strQuery = facetHandler.getTempSolrQuery(facet);
        // System.out.println("ObjectCollection strQuery: " + strQuery);
        query.setQuery(strQuery);
        query.setRows(0);
        query.setFacet(true);
        query.setFacetLimit(-1);
        query.setParam("json.facet", "{ "
                + "object_collection_type_str:{ "
                + "type: terms, "
                + "field: object_collection_type_str, "
                + "limit: 1000}}");

        try {
            SolrClient solr = new HttpSolrClient.Builder(
                    CollectionUtil.getCollectionPath(CollectionUtil.Collection.DATA_ACQUISITION)).build();
            QueryResponse queryResponse = solr.query(query, SolrRequest.METHOD.POST);
            solr.close();
            Pivot pivot = Pivot.parseQueryResponse(queryResponse);
            return parsePivot(pivot, facet);
        } catch (Exception e) {
            System.out.println("[ERROR] ObjectCollection.getTargetFacetsFromSolr() - Exception message: " + e.getMessage());
        }

        return null;
    }

    private Map<HADatAcThing, List<HADatAcThing>> parsePivot(Pivot pivot, Facet facet) {
        Map<HADatAcThing, List<HADatAcThing>> results = new HashMap<HADatAcThing, List<HADatAcThing>>();
        for (Pivot child : pivot.children) {
            if (child.getValue().isEmpty()) {
                continue;
            }

            ObjectCollection oc = new ObjectCollection();
            oc.setUri(child.getValue());
            Entity entity = Entity.find(child.getValue());
            System.out.println("child.getValue(): " + child.getValue());
            if (entity == null || entity.getLabel().isEmpty()) {
                oc.setLabel(WordUtils.capitalize(URIUtils.getBaseName(child.getValue())));
            } else {
                oc.setLabel(WordUtils.capitalize(entity.getLabel())); 
            }
            oc.setCount(child.getCount());
            oc.setField("object_collection_type_str");

            if (!results.containsKey(oc)) {
                List<HADatAcThing> children = new ArrayList<HADatAcThing>();
                results.put(oc, children);
            }

            Facet subFacet = facet.getChildById(oc.getUri());
            subFacet.putFacet("object_collection_type_str", oc.getUri());
        }

        return results;
    }

    private void saveObjectUris(String oc_uri) {
        if (objectUris == null || objectUris.size() == 0) {
            return;
        }

        String insert = "";

        insert += NameSpaces.getInstance().printSparqlNameSpaceList();
        insert += INSERT_LINE1;
        for (String uri : objectUris) {
            if (uri != null && !uri.equals("")) {
                if (uri.startsWith("http")) {
                    insert += "  <" + uri + "> hasco:isMemberOf  " + oc_uri + " . ";
                } else {
                    insert += "  " + uri + " hasco:isMemberOf  " + oc_uri + " . ";
                }
            }
        }
        insert += LINE_LAST;
        UpdateRequest request = UpdateFactory.create(insert);
        UpdateProcessor processor = UpdateExecutionFactory.createRemote(
                request, CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_UPDATE));
        processor.execute();
    }

    @Override
    public boolean saveToTripleStore() {
        String insert = "";

        String oc_uri = "";
        if (this.getUri().startsWith("<")) {
            oc_uri = this.getUri();
        } else {
            oc_uri = "<" + this.getUri() + ">";
        }

        insert += NameSpaces.getInstance().printSparqlNameSpaceList();
        insert += INSERT_LINE1;

        if (!getNamedGraph().isEmpty()) {
            insert += " GRAPH <" + getNamedGraph() + "> { ";
        }

        insert += oc_uri + " a <" + typeUri + "> . ";
        insert += oc_uri + " rdfs:label  \"" + this.getLabel() + "\" . ";
        if (this.getStudyUri().startsWith("http")) {
            insert += oc_uri + " hasco:isMemberOf  <" + this.getStudyUri() + "> . ";
        } else {
            insert += oc_uri + " hasco:isMemberOf  " + this.getStudyUri() + " . ";
        }
        if (this.getComment() != null && !this.getComment().equals("")) {
            insert += oc_uri + " rdfs:comment  \"" + this.getComment() + "\" . ";
        }
        if (this.getHasScopeUri() != null && !this.getHasScopeUri().equals("")) {
            if (this.getHasScopeUri().startsWith("http")) {
                insert += oc_uri + " hasco:hasScope  <" + this.getHasScopeUri() + "> . ";
            } else {
                insert += oc_uri + " hasco:hasScope  " + this.getHasScopeUri() + " . ";
            }
        }
        insert += oc_uri + " hasco:hasGroundingLabel  \"" + this.getGroundingLabel() + "\" . ";
        insert += oc_uri + " hasco:hasSOCReference  \"" + this.getSOCReference() + "\" . ";
        if (this.getSpaceScopeUris() != null && this.getSpaceScopeUris().size() > 0) {
            for (String spaceScope : this.getSpaceScopeUris()) {
                if (spaceScope.length() > 0){
                    if (spaceScope.startsWith("http")) {
                        insert += oc_uri + " hasco:hasSpaceScope  <" + spaceScope + "> . ";
                    } else {
                        insert += oc_uri + " hasco:hasSpaceScope  " + spaceScope + " . ";
                    }
                }
            }
        }
        if (this.getTimeScopeUris() != null && this.getTimeScopeUris().size() > 0) {
            for (String timeScope : this.getTimeScopeUris()) {
                if (timeScope.length() > 0){
                    if (timeScope.startsWith("http")) {
                        insert += oc_uri + " hasco:hasTimeScope  <" + timeScope + "> . ";
                    } else {
                        insert += oc_uri + " hasco:hasTimeScope  " + timeScope + " . ";
                        System.out.println(oc_uri + " hasco:hasTimeScope  " + timeScope + " . ");
                    }
                }
            }
        }

        if (!getNamedGraph().isEmpty()) {
            insert += " } ";
        }

        insert += LINE_LAST;

        try {
            UpdateRequest request = UpdateFactory.create(insert);
            UpdateProcessor processor = UpdateExecutionFactory.createRemote(
                    request, CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_UPDATE));
            processor.execute();
        } catch (QueryParseException e) {
            System.out.println("QueryParseException due to update query: " + insert);
            throw e;
        }

        saveObjectUris(oc_uri);

        return true;
    }

    public void saveRoleLabel(String label) {
        if (uri == null || uri.equals("")) {
            return;
        }

        String insert = "";

        insert += NameSpaces.getInstance().printSparqlNameSpaceList();
        insert += INSERT_LINE1;
        if (uri.startsWith("http")) {
            insert += "  <" + uri + "> hasco:hasRoleLabel \"" + label + "\" . ";

        } else {
            insert += "  " + uri + " hasco:hasRoleLabel \"" + label + "\" . ";
        }
        insert += LINE_LAST;
        UpdateRequest request = UpdateFactory.create(insert);
        UpdateProcessor processor = UpdateExecutionFactory.createRemote(
                request, CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_UPDATE));
        processor.execute();
    }

    @Override
    public int saveToLabKey(String user_name, String password) {
        LabkeyDataHandler loader = LabkeyDataHandler.createDefault(user_name, password);
        List< Map<String, Object> > rows = new ArrayList< Map<String, Object> >();
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("hasURI", URIUtils.replaceNameSpaceEx(getUri()));
        row.put("a", URIUtils.replaceNameSpaceEx(getTypeUri()));
        row.put("rdfs:label", getLabel());
        row.put("hasco:isMemberOf", getStudyUri());
        rows.add(row);

        int totalChanged = 0;
        try {
            totalChanged = loader.insertRows("ObjectCollection", rows);
        } catch (CommandException e) {
            try {
                totalChanged = loader.updateRows("ObjectCollection", rows);
            } catch (CommandException e2) {
                System.out.println("[ERROR] Could not insert or update ObjectCollection(s)");
            }
        }
        return totalChanged;
    }

    @Override
    public int deleteFromLabKey(String user_name, String password) {
        LabkeyDataHandler loader = LabkeyDataHandler.createDefault(user_name, password);
        List< Map<String, Object> > rows = new ArrayList< Map<String, Object> >();
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("hasURI", URIUtils.replaceNameSpaceEx(getUri().replace("<","").replace(">","")));
        rows.add(row);

        try {
            return loader.deleteRows("ObjectCollection", rows);
        } catch (CommandException e) {
            System.out.println("[ERROR] Failed to delete Object Collections From LabKey!");
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public void deleteFromTripleStore() {
        String query = "";

        String oc_uri = "";
        if (this.getUri().startsWith("<")) {
            oc_uri = this.getUri();
        } else {
            oc_uri = "<" + this.getUri() + ">";
        }

        query += NameSpaces.getInstance().printSparqlNameSpaceList();
        query += DELETE_LINE1;
        query += " " + oc_uri + "  ";
        query += DELETE_LINE3;
        query += LINE_LAST;

        UpdateRequest request = UpdateFactory.create(query);
        UpdateProcessor processor = UpdateExecutionFactory.createRemote(
                request, CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_UPDATE));
        processor.execute();
    }

    @Override
    public boolean saveToSolr() {
        return false;
    }

    @Override
    public int deleteFromSolr() {
        return 0;
    }

    public static String computeRouteLabel (ObjectCollection oc, List<ObjectCollection> studyOCs) {
        if (oc.getGroundingLabel() != null && !oc.getGroundingLabel().equals("")) {
            return oc.getGroundingLabel();
        } else {
            List<ObjectCollection> ocList = new ArrayList<ObjectCollection>();
            List<ObjectCollection> allList = new ArrayList<ObjectCollection>();
            List<ObjectCollection> inspectedList = new ArrayList<ObjectCollection>();
            ocList.add(oc);
            for (ObjectCollection receivedOC : studyOCs) {
                if (!receivedOC.equals(oc)) {
                    allList.add(receivedOC);
                    inspectedList.add(receivedOC);
                }
            } 
            return traverseRouteLabel(ocList, allList, inspectedList);
        }
    }

    private static String traverseRouteLabel(List<ObjectCollection> path, List<ObjectCollection> inspectedList, List<ObjectCollection> allList) {
        //System.out.println("Path " + path);
        //System.out.println("StudyOCs " + inspectedList);
        for (ObjectCollection oc : inspectedList) {
            //System.out.println("    - oc " + oc.getUri());
            //System.out.println("    - current.domain " + path.get(path.size() - 1).getHasScopeUri());
            //System.out.println("    - current.time " + path.get(path.size() - 1).getTimeScopes());
            //System.out.println("    - oc.domain " + oc.getHasScopeUri());
            //System.out.println("    - oc.time " + oc.getTimeScopes());
            if (path.get(path.size() - 1).isConnected(oc)) {
                System.out.println(oc.getUri() + " is connected to " + path.get(path.size() - 1).getUri());
                if (oc.getGroundingLabel() != null && !oc.getGroundingLabel().equals("")) {
                    String finalLabel = oc.getGroundingLabel();
                    for (int i = path.size() - 1; i >= 0; i--) {
                        finalLabel = finalLabel + " " + path.get(i).getLabel();
                    }
                    //System.out.println(" final label ==> <" + finalLabel + ">");
                    return finalLabel;
                } else {
                    path.add(oc);
                    List<ObjectCollection> newList = new ArrayList<ObjectCollection>();
                    for (ObjectCollection ocFromAllList : allList) {
                        if (!path.contains(ocFromAllList)) {
                            newList.add(ocFromAllList);
                        }
                    }
                    return traverseRouteLabel(path, newList, allList);
                }
            } else {
                //System.out.println("next iteration of traverseRouteLabel");
                inspectedList.remove(oc);
                return traverseRouteLabel(path, inspectedList, allList);
            }
        }
        System.out.println("Could not find path for " + path.get(0).getSOCReference());
        return null;
    }

    public String toString() {
        return this.getUri();
    } 

}
