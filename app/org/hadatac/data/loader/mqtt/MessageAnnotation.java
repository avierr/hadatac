package org.hadatac.data.loader.mqtt;

import java.lang.String;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.hadatac.data.loader.MeasurementGenerator;
import org.hadatac.entity.pojo.ObjectAccessSpec;
import org.hadatac.entity.pojo.Deployment;
import org.hadatac.entity.pojo.MessageStream;
import org.hadatac.entity.pojo.MessageTopic;
import org.hadatac.entity.pojo.DataAcquisitionSchema;
import org.hadatac.entity.pojo.DataFile;
import org.hadatac.metadata.loader.URIUtils;
import org.eclipse.paho.client.mqttv3.MqttException;

public class MessageAnnotation {
	
	public MessageAnnotation() {}

    /********************************************************************************
     *                            STREAM MANAGEMENT                                 *
     ********************************************************************************/
    
    public static void initiateMessageStream(MessageStream stream) {
    	if (!stream.getStatus().equals(MessageStream.CLOSED)) {
    		return;
    	}
    	System.out.println("Initiating message stream: " + stream.getName());
		stream.setLastProcessTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
		stream.getLogger().resetLog();
		stream.getLogger().println(String.format("Initiating message stream: %s", stream.getName()));

		List<MessageTopic> topics = MessageTopic.findByStream(stream.getUri());

		if (topics != null && topics.size() > 0) {
			stream.getLogger().println(String.format("Message stream has %s topics", topics.size()));
			for (MessageTopic topic : topics) {
				startMessageTopic(topic);
			}
		}
		
		stream.setStatus(MessageStream.INITIATED);
		stream.save();

		// Stream Subscription refresh needs to occur after the stream is activated
		MessageWorker.refreshStreamSubscription();
    }
    
    public static void subscribeMessageStream(MessageStream stream) {
    	if (!stream.getStatus().equals(MessageStream.INITIATED)) {
    		return;
    	}
    	System.out.println("Subscribing message stream: " + stream.getName());
		stream.setLastProcessTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
		stream.getLogger().resetLog();
		stream.getLogger().println(String.format("Subscribing message stream: %s", stream.getName()));

    	DataFile archive;
    	if (stream.getDataFileId() == null || stream.getDataFileId().isEmpty()) {
            Date date = new Date();
    		String fileName = "DA-" + stream.getName().replaceAll("/","_").replaceAll(".", "_") + ".json";
    		archive = DataFile.create(fileName, "" , "", DataFile.PROCESSED);
            archive.setSubmissionTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(date));
            archive.save();
    		stream.setDataFileId(archive.getId());
    		stream.save();
            stream.getLogger().println(String.format("Creating archive datafile " + fileName + " with id " + archive.getId()));
    	} else {
    		archive = DataFile.findById(stream.getDataFileId());
            stream.getLogger().println("Reusing archive datafile with id " + stream.getDataFileId());
    	}
            
		try {
			//Thread t = new Thread(new SubscribeWorkerOld(stream));
			//t.start();
			Subscribe.exec(stream, null, Subscribe.SUBSCRIBE);
		} catch (Exception e) {
			stream.getLogger().println("MessageAnnotation: Error executing 'subscribe' inside startMessageStream.");
			e.printStackTrace();
		}
		stream.setStatus(MessageStream.ACTIVE);
		stream.save();

		// Stream Subscription refresh needs to occur after the stream is activated
		MessageWorker.refreshStreamSubscription();
    
    }
    
    public static void unsubscribeMessageStream(MessageStream stream) {
    	if (!stream.getStatus().equals(MessageStream.ACTIVE)) {
    		return;
    	}
    	System.out.println("Unsubscribing message stream: " + stream.getName());
		stream.setLastProcessTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
		stream.getLogger().resetLog();
		stream.getLogger().println(String.format("Unsubscribing message stream: %s", stream.getName()));
		if (MessageWorker.getInstance().currentClient == null) {
			stream.getLogger().println("Could not stop message stream: " + stream.getName() + ". Reason: currentClient is null");
		} else {
			try {
			   MessageWorker.getInstance().currentClient.unsubscribe("#");
			} catch (MqttException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}

		stream.setStatus(MessageStream.INITIATED);
		stream.save();

		// Stream Subscription refresh needs to occur after the stream is activated
		MessageWorker.refreshStreamSubscription();
    }
    
    public static void stopMessageStream(MessageStream stream) {
    	stream.getLogger().println("Stopping message stream: " + stream.getName());
		List<MessageTopic> topics = MessageTopic.findByStream(stream.getUri());

		if (topics != null && topics.size() > 0) {
			stream.getLogger().println(String.format("Message stream has issued command to stop %s topics", topics.size()));
			for (MessageTopic topic : topics) {
				stopMessageTopic(topic);
			}
		}
		
		stream.setTotalMessages(0);
		stream.setIngestedMessages(0);
        stream.setStatus(MessageStream.CLOSED);
		stream.setCompletionTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
		stream.getLogger().println(String.format("Stopped processing of message stream: %s", stream.getName()));
		stream.save();		
		/*
		List<MessageTopic> topics = MessageTopic.findByStream(stream.getUri());
		if (topics != null && topics.size() > 0) {
			for (MessageTopic topic : topics) {
				stopMessageTopic(topic);
			}
		}
		*/
    }

    /********************************************************************************
     *                            TOPIC MANAGEMENT                                  *
     ********************************************************************************/
    
    public static void startMessageTopic(MessageTopic topic) {
		System.out.println("Starting message topic: " + topic.getLabel());
		topic.setLastProcessTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
		topic.getLogger().resetLog();
		topic.getLogger().println(String.format("Started processing of message topic: %s", topic.getLabel()));

        List<ObjectAccessSpec> oasList = null;
        ObjectAccessSpec oas = null;
        Deployment dpl = null;
        String oas_uri = null;
        String deployment_uri = null;
        String schema_uri = null;
        boolean isValid = true;

        if (topic != null) {
        	deployment_uri = URIUtils.replacePrefixEx(topic.getDeploymentUri());
        	dpl = Deployment.find(deployment_uri);
            oasList = ObjectAccessSpec.find(dpl, true);
            
            if (oasList != null && oasList.size() > 0) {
            	oas = oasList.get(0);
            	oas_uri = oas.getUri();
            }
            if (oas != null) {
                if (!oas.isComplete()) {
                    topic.getLogger().printWarningByIdWithArgs("DA_00003", oas_uri);
                    isValid = false;
                } else {
                    topic.getLogger().println(String.format("Stream specification is complete: <%s>", oas_uri));
                }
                deployment_uri = oas.getDeploymentUri();
                schema_uri = oas.getSchemaUri();
            } else {
                topic.getLogger().printWarningByIdWithArgs("DA_00004", oas_uri);
                isValid = false;
            }
        }

        if (isValid) {
	        if (schema_uri == null || schema_uri.isEmpty()) {
	            topic.getLogger().printExceptionByIdWithArgs("DA_00005", oas_uri);
	        } else {
	            topic.getLogger().println(String.format("Schema <%s> specified for message topic: <%s>", schema_uri, topic.getLabel()));
	        }
        }

        if (isValid) {
	        if (deployment_uri == null || deployment_uri.isEmpty()) {
	            topic.getLogger().printExceptionByIdWithArgs("DA_00006", oas_uri);
	        } else {
	            try {
	                deployment_uri = URLDecoder.decode(deployment_uri, "UTF-8");
	            } catch (UnsupportedEncodingException e) {
	                topic.getLogger().printException(String.format("URL decoding error for deployment uri <%s>", deployment_uri));
	            }
	            topic.getLogger().println(String.format("Deployment <%s> specified for message topic <%s>", deployment_uri, topic.getLabel()));
	        }
        }

        if (isValid) {
	        if (oas != null) {
	            //topic.setStudyUri(oas.getStudyUri());

	        	// Learn Headers
	        	List<String> headers = Subscribe.testLabels(topic.getStream(), topic); 

	        	if (headers == null || headers.size() == 0) {
	                topic.getLogger().printException(String.format("Could not retrieve column headers for stream"));
	                isValid = false;
	        	} else {
	        		topic.setHeaders(headers);
		            topic.getLogger().println(String.format("Message topic <%s> has labels", topic.getLabel()));
		            topic.save();
	        	}
	        }
        }
        
        DataAcquisitionSchema schema = null;
        if (isValid) {
            schema = DataAcquisitionSchema.find(oas.getSchemaUri());
            if (schema == null) {
                topic.getLogger().printExceptionByIdWithArgs("DA_00007", oas.getSchemaUri());
                isValid = false;
            }
        }

        if (isValid) {
            if (!oas.hasCellScope()) {
            	// Need to be fixed here by getting codeMap and codebook from sparql query
            	//DASOInstanceGenerator dasoInstanceGen = new DASOInstanceGenerator(
            	//		stream, oas.getStudyUri(), oas.getUri(), 
            	//		schema, stream.getName());
            	//chain.addGenerator(dasoInstanceGen);	
            	//chain.addGenerator(new MeasurementGenerator(MeasurementGenerator.MSGMODE, null, topic, oas, schema, dasoInstanceGen));
                topic.getLogger().printException(String.format("Message annotation requires cell scope"));
                isValid = false;
            } 
        }
        
        if (isValid) {
            MeasurementGenerator gen = new MeasurementGenerator(MeasurementGenerator.MSGMODE, null, topic, oas, schema, null);
            MessageWorker.getInstance().topicsGen.put(topic.getLabel(),gen);
            if (MessageWorker.getInstance().topicsGen.get(topic.getLabel()) == null) { 
            	topic.getLogger().printException(String.format("MeasurementGenerator is null in message annotation"));
            	isValid = false;
            }
        }
        if (isValid) {
            topic.setNamedGraphUri(URIUtils.replacePrefixEx(topic.getDeploymentUri()));
        	topic.setStreamSpecUri(oas_uri);
        	topic.setStatus(MessageTopic.ACTIVE);
        
        } else {
        	topic.setStatus(MessageTopic.FAIL);
        }
		topic.save();
		
    }
    
    public static void stopMessageTopic(MessageTopic topic) {
    	if (!topic.getStatus().equals(MessageTopic.INACTIVE)) {
    		System.out.println("Stopping message topic: " + topic.getLabel());
    		topic.setStatus(MessageTopic.INACTIVE);
    		topic.setCompletionTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
    		topic.getLogger().println(String.format("Stopped processing of message topic: %s", topic.getLabel()));
    		topic.save();		
    	}
    }

}
