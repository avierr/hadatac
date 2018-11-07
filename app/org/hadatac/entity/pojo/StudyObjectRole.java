package org.hadatac.entity.pojo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSetRewindable;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.hadatac.console.http.SPARQLUtils;
import org.hadatac.console.models.Facet;
import org.hadatac.console.models.FacetHandler;
import org.hadatac.console.models.Pivot;
import org.hadatac.utils.CollectionUtil;
import org.hadatac.utils.NameSpaces;


public class StudyObjectRole extends HADatAcThing implements Comparable<StudyObjectRole> {

	static String className = "sio:Object";

	public StudyObjectRole() {}
	
	@Override
	public boolean equals(Object o) {;
		if((o instanceof StudyObjectRole) && (((StudyObjectRole)o).getUri().equals(this.getUri()))) {
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
    public long getNumber(Facet facet, FacetHandler facetHandler) {
        return getNumberFromSolr(facet, facetHandler);
    }
	
	@Override
	public long getNumberFromSolr(Facet facet, FacetHandler facetHandler) {
        SolrQuery query = new SolrQuery();
        String strQuery = facetHandler.getTempSolrQuery(facet);
        System.out.println("StudyObjectRole strQuery: " + strQuery);
        query.setQuery(strQuery);
        query.setRows(0);
        query.setFacet(false);

        try {
            SolrClient solr = new HttpSolrClient.Builder(
                    CollectionUtil.getCollectionPath(CollectionUtil.Collection.DATA_ACQUISITION)).build();
            QueryResponse queryResponse = solr.query(query, SolrRequest.METHOD.POST);
            solr.close();
            SolrDocumentList results = queryResponse.getResults();
            return results.getNumFound();
        } catch (Exception e) {
            System.out.println("[ERROR] StudyObjectRole.getNumberFromSolr() - Exception message: " + e.getMessage());
        }

        return -1;
    }
	
	@Override
    public long getNumberFromTripleStore(Facet facet, FacetHandler facetHandler) {
        Map<String, List<String>> constraints = facetHandler.getTempSparqlConstraints(facet);
        System.out.println("StudyObjectRole constraints: " + constraints);
        
        String valueConstraint = "";
        for (String field : constraints.keySet()) {
            valueConstraint += " VALUES ?" + field + " { " + stringify(constraints.get(field), true) + " } \n ";
        }

        String query = "";
        query += NameSpaces.getInstance().printSparqlNameSpaceList();
        query += "SELECT DISTINCT (COUNT(?role) AS ?count) WHERE { \n"
                + valueConstraint + " \n"
                + "?measurement hadatac:ownedByStudyObject ?studyObj . \n"
                + "?studyObj rdf:type ?studyObjType . \n"
                + "?studyObj hasco:isMemberOf ?objectCollection . \n"
                + "?objectCollection rdf:type ?object_collection_type_str . \n"
                + "?objectCollection hasco:hasRoleLabel ?role . \n"
                + "?studyObjType rdfs:label ?studyObjTypeLabel . \n"
                + "}";

        System.out.println("StudyObjectRole query: \n" + query);

        try {            
            ResultSetRewindable resultsrw = SPARQLUtils.select(
                    CollectionUtil.getCollectionPath(CollectionUtil.Collection.METADATA_SPARQL), query);

            if (resultsrw.hasNext()) {
                QuerySolution soln = resultsrw.next();
                return Long.valueOf(soln.getLiteral("count").getValue().toString()).longValue();
            }
        } catch (QueryExceptionHTTP e) {
            e.printStackTrace();
        }

        return 0;
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
        query.setQuery(strQuery);
        query.setRows(0);
        query.setFacet(true);
        query.setFacetLimit(-1);
        query.setParam("json.facet", "{ "
                + "role_str:{ "
                + "type: terms, "
                + "field: role_str, "
                + "limit: 1000}}");

        try {
            SolrClient solr = new HttpSolrClient.Builder(
                    CollectionUtil.getCollectionPath(CollectionUtil.Collection.DATA_ACQUISITION)).build();
            QueryResponse queryResponse = solr.query(query, SolrRequest.METHOD.POST);
            solr.close();
            Pivot pivot = Pivot.parseQueryResponse(queryResponse);
            return parsePivot(pivot, facet);
        } catch (Exception e) {
            System.out.println("[ERROR] StudyObjectRole.getTargetFacetsFromSolr() - Exception message: " + e.getMessage());
        }

        return null;
    }

    private Map<HADatAcThing, List<HADatAcThing>> parsePivot(Pivot pivot, Facet facet) {
        facet.clearFieldValues("role_uri_str");

        Map<HADatAcThing, List<HADatAcThing>> results = new HashMap<HADatAcThing, List<HADatAcThing>>();
        for (Pivot pivot_ent : pivot.children) {
            StudyObjectRole role = new StudyObjectRole();
            role.setUri(pivot_ent.getValue());
            //role.setLabel(WordUtils.capitalize(Entity.find(pivot_ent.getValue()).getLabel()));
            //Comment from PP: this is a temporary hack since role_uri has changed to be the label itself
            role.setLabel(pivot_ent.getValue());
            role.setCount(pivot_ent.getCount());
            role.setField("role_str");

            if (!results.containsKey(role)) {
                List<HADatAcThing> children = new ArrayList<HADatAcThing>();
                results.put(role, children);
            }

            Facet subFacet = facet.getChildById(role.getUri());
            subFacet.putFacet("role_str", role.getUri());
        }

        return results;
    }

	@Override
	public int compareTo(StudyObjectRole another) {
		return this.getUri().compareTo(another.getUri());
	}

    @Override
    public boolean saveToTripleStore() {
        return false;
    }

    @Override
    public void deleteFromTripleStore() {
    }

    @Override
    public boolean saveToSolr() {
        return false;
    }

    @Override
    public int deleteFromSolr() {
        return 0;
    }

    @Override
    public int saveToLabKey(String userName, String password) {
        return 0;
    }

    @Override
    public int deleteFromLabKey(String userName, String password) {
        return 0;
    }
}

