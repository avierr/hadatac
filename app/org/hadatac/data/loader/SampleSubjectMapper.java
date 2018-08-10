package org.hadatac.data.loader;

import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSetRewindable;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.hadatac.utils.CollectionUtil;
import org.hadatac.utils.ConfigProp;
import org.hadatac.utils.NameSpaces;
import org.hadatac.utils.Templates;
import org.hadatac.console.controllers.annotator.AnnotationLog;
import org.hadatac.console.http.SPARQLUtils;
import org.hadatac.entity.pojo.HADatAcThing;
import org.hadatac.entity.pojo.ObjectCollection;
import org.hadatac.entity.pojo.StudyObject;
import org.hadatac.metadata.loader.URIUtils;


public class SampleSubjectMapper extends BasicGenerator {

    final String kbPrefix = ConfigProp.getKbPrefix();
    private int counter = 1;
    private Map<String, String> mapIdUriCache = new HashMap<String, String>();
    String study_id;
    String file_name;

    public SampleSubjectMapper(RecordFile file) {
        super(file);
        mapIdUriCache = getMapIdUri();
        file_name = file.getFile().getName();
        study_id = file.getFile().getName().replaceAll("MAP-", "").replaceAll(".xlsx", "").replaceAll(".csv", "");
    }

    @Override
    public void initMapping() {
        mapCol.clear();
        mapCol.put("type", Templates.OBJECTTYPE);
        mapCol.put("originalPID", Templates.ORIGINALPID);
        mapCol.put("originalSID", Templates.ORIGINALSID);
        try{
            mapCol.put("pilotNum", Templates.MAPSTUDYID);
        } catch (QueryExceptionHTTP e) {
            e.printStackTrace();
            System.out.println("This sheet or MAP file contains no CHEAR_Project_ID column");
        }
        try{
            mapCol.put("timeScopeID", Templates.TIMESCOPEID);
        } catch (QueryExceptionHTTP e) {
            e.printStackTrace();
            System.out.println("This sheet or MAP file contains no timeScopeID column");
        }
    }

    private Map<String, String> getMapIdUri() {
        Map<String, String> mapIdUri = new HashMap<String, String>();

        String queryString = NameSpaces.getInstance().printSparqlNameSpaceList() +
                "SELECT ?uri ?id WHERE { \n" +
                " ?uri hasco:originalID ?id . \n" +
                "}";

        try {
            ResultSetRewindable resultsrw = SPARQLUtils.select(
                    CollectionUtil.getCollectionsName(CollectionUtil.METADATA_SPARQL), queryString);

            while (resultsrw.hasNext()) {
                QuerySolution soln = resultsrw.next();
                if(soln.get("id") != null && soln.get("uri") != null) {
                    mapIdUri.put(soln.get("id").toString(), soln.get("uri").toString());
                }
            }
        } catch (QueryExceptionHTTP e) {
            e.printStackTrace();
        }

        return mapIdUri;
    }

    private String getUri(Record rec) {
        return kbPrefix + "SPL-" + getOriginalSID(rec);
    }

    private String getType(Record rec) {
        return "sio:Sample";
    }

    private String getLabel(Record rec) {
        return "Sample " + rec.getValueByColumnName(mapCol.get("originalSID"));
    }

    private String getOriginalSID(Record rec) {
        if(!rec.getValueByColumnName(mapCol.get("originalSID")).equalsIgnoreCase("NULL")){
            return rec.getValueByColumnName(mapCol.get("originalSID"));
        } else {
            return "";
        }
    }

    private String getOriginalPID(Record rec) {
        if(!rec.getValueByColumnName(mapCol.get("originalPID")).equalsIgnoreCase("NULL")){
            return rec.getValueByColumnName(mapCol.get("originalPID")).replaceAll("(?<=^\\d+)\\.0*$", "");
        } else {
            return "";
        }
    }

    private String getPilotNum(Record rec) {
        return rec.getValueByColumnName(mapCol.get("pilotNum"));
    }

    private String getStudyId(Record rec) {
        return getPilotNum(rec);
    }

    private String getCollectionUri(Record rec) {
        return kbPrefix + "SOC-" + getStudyId(rec) + "-SSAMPLES";
    }

    private String getCollectionLabel(Record rec) {
        return "Sample Collection of Study " + getStudyId(rec);
    }

    private String getTimeScopeUri(Record rec) {
        String ans = rec.getValueByColumnName(mapCol.get("timeScopeID"));
        return ans;
    }

    private String getSpaceScopeUri(Record rec) {
        String ans = rec.getValueByColumnName(mapCol.get("spaceScopeID"));
        return ans;
    }

    public StudyObject createStudyObject(Record record) throws Exception {
        List<String> scopeUris = new ArrayList<String>();
        List<String> timeScopeUris = new ArrayList<String>();
        List<String> spaceScopeUris = new ArrayList<String>();
        String pid = getOriginalPID(record);
        if (!pid.isEmpty()) {
            scopeUris.add(kbPrefix + "SBJ-" + pid + "-" + study_id);
        }
        if (!getTimeScopeUri(record).isEmpty()){
            timeScopeUris.add(kbPrefix + "TIME-" + getTimeScopeUri(record) + "-" + study_id);
        }
        if (!getSpaceScopeUri(record).isEmpty()){
            timeScopeUris.add(kbPrefix + "LOC-" + getTimeScopeUri(record) + "-" + study_id);
        }

        System.out.println("scopeUris :" + scopeUris);

        StudyObject obj = new StudyObject(getUri(record), getType(record), getOriginalSID(record), 
                getLabel(record), getCollectionUri(record), getLabel(record), scopeUris, timeScopeUris, spaceScopeUris);
        return obj;
    }

    public ObjectCollection createObjectCollection(Record record) throws Exception {
        ObjectCollection oc = new ObjectCollection(
                getCollectionUri(record),
                "http://hadatac.org/ont/hasco/SampleCollection",
                getCollectionLabel(record),
                getCollectionLabel(record),
                kbPrefix + "STD-" + getStudyId(record));

        if (!getStudyId(record).isEmpty()) {
            setStudyUri(URIUtils.replacePrefixEx(kbPrefix + "STD-" + getStudyId(record)));
        }

        AnnotationLog.println("ObjectCollection:" + getCollectionUri(record) + 
                " has been created as a hasco:SampleCollection by createObjectCollection().", file_name);
        return oc;
    }

    @Override
    public void preprocess() throws Exception {
        mapIdUriCache = getMapIdUri();

        if (!records.isEmpty()) {
            objects.add(createObjectCollection(records.get(0)));
        }
    }

    @Override
    public HADatAcThing createObject(Record rec, int row_number) throws Exception {
        System.out.println("counter: " + counter);
        counter++;
        return createStudyObject(rec);
    }

    @Override
    public String getTableName() {
        return "StudyObject";
    }

    @Override
    public String getErrorMsg(Exception e) {
        return "Error in SampleSubjectMapper: " + e.getMessage();
    }
}
