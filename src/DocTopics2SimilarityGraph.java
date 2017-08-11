import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.stream.IntStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
 
 
 
 
public class DocTopics2SimilarityGraph {
	static String outputDir = "output";
    static String inputDir  = "input";
    
    private static DecimalFormat df4;
	
    private static boolean[] connectedNode;
    private static String[] docNames;
    private static int numdocs = 0;
    private static short numtopics = 0;
    private static float epsylon = 0.15f;

    
    private static float epsylon_2_2sqrt = (float) (2*Math.sqrt(2*epsylon));
    private static float epsylon_2_2sqrt_short = (float) (epsylon_2_2sqrt*10000);  
    private static float epsylon_cota2_2 = (float) (Math.sqrt((-72f + 24f*Math.sqrt(9+2*epsylon)))*10000);
    private static float epsylon20000f = epsylon*20000f;
    
    
    private static float epsylonEng = 0.08f; //0.035f;//0.070f;
    
    private static int[] englishTopics = {};
 
    private static String fileName  = "PlanEstatalENE.doc_topics.bin";
    private static String fileNameNodeMetadata  = "PlanEstatalENE-metadata-2004-2016.csv";
    private static boolean saveNodeFile = true;
    private static boolean bin = true;

    
    public static void main(String[] args) throws ParseException {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
        simbolos.setDecimalSeparator('.');    
        df4 = new DecimalFormat("#.####", simbolos);
                 
	
    	// parse CLI options
        Options options = createCLIoptions();
        
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse( options, args);
     		
        // outputDir, get -o option value
        String outputDircli = cmd.getOptionValue("o");
        if(outputDircli == null) {
        	System.out.println("No output dir specified. Default: " + outputDir);
        } else {
        	System.out.println("Output dir specified: " + outputDircli);
        	outputDir = outputDircli;
        }
        
        // inputDir, get -i option value
        String inputDircli = cmd.getOptionValue("i");
        if(inputDircli == null) {
        	 System.out.println("No input dir specified. Default: " + inputDir);
        } else {
        	System.out.println("Input dir specified: " + inputDircli);
        	inputDir = inputDircli;
        }
        
        // doc_topics file, get -d option value
        String docTopicscli = cmd.getOptionValue("d");
        if(docTopicscli == null) {
        	 System.out.println("No DocTopics file specified.");
        	 usage(options);
        	 System.exit(1);
        } else {
        	System.out.println("DocTopics file specified: " + docTopicscli);
        	fileName = docTopicscli;
        }
        
        // node metadata file, get -m option value
        String fileNameNodeMetadatacli = cmd.getOptionValue("m");
        if(fileNameNodeMetadatacli == null) {
        	 System.out.println("No node metadata file specified.");
        } else {
        	System.out.println("Node metadata file specified: " + fileNameNodeMetadatacli);
        	fileNameNodeMetadata = fileNameNodeMetadatacli;
        }
        
        // save node metadata file, get -ns option value
        String saveNodeFile_cli = cmd.getOptionValue("ns");
        if(saveNodeFile_cli != null) {
        	System.out.println("Specified not to save node metadata file.");
        	saveNodeFile = false;
        } 
        
        // binary doc topic file format, get -nb option value
        String bin_cli = cmd.getOptionValue("nb");
        if(bin_cli != null) {
        	System.out.println("No binary doc topic file format specified.");
        	bin = false;
        }         
        
        // epsylon, get -e option value
        String epsyloncli = cmd.getOptionValue("e");
        if(epsyloncli == null) {
        	 System.out.println("No epsylon specified, min distance. Default: " + epsylon);

        } else {
        	System.out.println("Epsylon, min distance, specified: " + epsylon);
        	epsylon = Float.parseFloat(epsyloncli);
        }  

        
        // read doc topic file
        try {
        	if(bin){
        		inspectBinTopicFile(fileName);
        	} else {
        		inspectTopicFile(fileName);
        	}

            if(numdocs == 0){
            	System.out.println("Error loading doc-topics: incorrect numdocs...");
            	System.exit(1);            	
            }
            
            if(numtopics == 0){
            	System.out.println("Error loading doc-topics: incorrect numtopics...");
            	System.exit(1);            	
            }
            

            docNames = new String[numdocs];  
            connectedNode = new boolean[numdocs];
            
            System.out.println("\nLoading doc-topics, numdocs: " + numdocs + ", numtopics: " + numtopics);
            
            if(bin){
            	short[][] docTopicValues = new short[numdocs][numtopics];
                if(loadBinTopics(fileName, numdocs, docTopicValues) == 0){
                	System.out.println("Error loading bin doc-topics...");
                	return;
                }  
                saveSimilarityMatrixBinParallel(docTopicValues);      
            } else {
            	double[][] docTopicValues = new double[numdocs][numtopics];
                if(loadTopics(fileName, numdocs, docTopicValues) == 0){
                	System.out.println("Error loading doc-topics...");
                	return;
                }             
                saveSimilarityMatrix(docTopicValues);//TODO parallel, like saveSimilarityMatrixBinParallel
            } 
            
            // node metadata 
            if(saveNodeFile){
            	saveNodeFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


	private static Options createCLIoptions() {
		//  samaple params
		//		-o output
		//		-i input
		//		-d cordis-projects_100.doc_topics.bin
		//		-m cordis-projects-metadata.csv
		//		//-ns
		//		//-nb
		//		-e 0.15		
		
		// create Options object
		Options options = new Options();

		// add options
		options.addOption("o", true, "output directory");
		options.addOption("i", true, "input directory");
		options.addOption("d", true, "doctopics bin file");
		options.addOption("m", true, "node metadata file");
		options.addOption("ns", false, "dont save node metadata file");
		options.addOption("nb", false, "not binary doc topic file format");
		options.addOption("e", true, "epsylon, min distance to draw edge between nodes");

		return options;
	}

	private static void usage(Options options) {
		// automatically generate the help statement
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( "DocTopics2SimilarityGraph", options );
	}

//	private static void usage() {
//		System.out.println("arg0: outputDir");
//		System.out.println("arg1: inputDir");
//		System.out.println("arg2: epsylon");
//		System.out.println("arg3: fileName");
//		System.out.println("arg4: fileNameNodeMetadata");
//		System.out.println("arg5: saveNodeFile [0|1]");
//		System.out.println("arg6: bin [0|1]");
//	}



	private static void saveNodeFile() {
		try {		
	        FileOutputStream outputStream;
			outputStream = new FileOutputStream(new File(outputDir + File.separator + "nodes.csv"));
	        BufferedWriter stdWriter = new BufferedWriter(new OutputStreamWriter(outputStream,"UTF-8"));

	        FileInputStream inputStream = new FileInputStream(new File(inputDir + File.separator + fileNameNodeMetadata));
	    	BufferedReader stdInReader = new BufferedReader(new InputStreamReader(inputStream,"UTF-8"));

	        
	    	String line;

	    	int cnt = 0;
	    	int cnt_out = 0;
	    	String DEFAULT_SEPARATOR = ",";	    	
	    	
            while ((line = stdInReader.readLine()) != null) {
                if(cnt > 0){
	            	String[] result = line.split(DEFAULT_SEPARATOR);
	            	
                	String docName = result[0].replace("\"", "").replace("/","-").toUpperCase().trim();
	                
                	int iddoc = getDocId(docName);
                	if(iddoc >= 0 && connectedNode[iddoc]){
                		line = iddoc + DEFAULT_SEPARATOR + line + "\n";
						stdWriter.write(line);
						cnt_out++;
                	}
       	        // header
                } else {
	            	if(line.split(DEFAULT_SEPARATOR).length == 1){
	            		DEFAULT_SEPARATOR = ";";
	            	}
	            	line = "\"Id\"" + DEFAULT_SEPARATOR + line + "\n";
					stdWriter.write(line);
                }
	            cnt++;
            }
            
            System.out.println("Metadata nodes read: " + cnt + ", active nodes: " + cnt_out);
   			
			stdWriter.flush();
			outputStream.close();	
			stdInReader.close();
		} catch (IOException e) {
	        System.err.println("Error writing topic distance file: ");
			e.printStackTrace();
		}         
	}

	private static int getDocId(String docName) {
		// TODO: hashtable
		for(int i=0; i < numdocs; i++){
			if(docNames[i].equalsIgnoreCase(docName)){
				return i;
			}
		}	
		return -1;
	}

	private static void saveSimilarityMatrix(double[][] docTopicValues) {
		try {
	        FileOutputStream outputStream;
			outputStream = new FileOutputStream(new File(outputDir + File.separator + "edges.csv"));
	        BufferedWriter stdWriter = new BufferedWriter(new OutputStreamWriter(outputStream,"UTF-8"));
	
			long cnt_activos = 0;
			
			long start_time = System.currentTimeMillis();
			
			stdWriter.write("\"Source\",\"Target\",\"Weight\",\"Type\"\n");
			
			long time_ini = System.currentTimeMillis();
			
			for(int i = 0; i < numdocs; i++){
				for(int j = i+1; j < numdocs; j++){
					// TODO param select measure
					
					boolean engDoc = false;
					
					for(int ii=0; ii< englishTopics.length;ii++){
						if((docTopicValues[i][englishTopics[ii]] > epsylonEng) || (docTopicValues[j][englishTopics[ii]] > epsylonEng)){
							engDoc = true;
						}
					}
					
					if(engDoc){
						continue;
					}
					
					// distancia euclidea
					//double distance = MetricsUtils.distL2(vector1, vector2);
					
					// distania jensen shanon
					//float distance = MetricsUtils.jsd(docTopicValues[i], docTopicValues[j]);
					
					float distance = MetricsUtils.jsd_tuning1(docTopicValues[i], docTopicValues[j], epsylon, epsylon_2_2sqrt);
					
					// getNearestTopic					
					if(distance < epsylon){
						double distanceNormalized = 0.5d*(epsylon - distance)/epsylon + 0.5d; 
						String str = df4.format(distanceNormalized);
						
						// Normalizar distancia
						stdWriter.write(i + "," + j + "," + str + ",\"Undirected\"\n");
						
						connectedNode[i] = true;
						connectedNode[j] = true;
						
						cnt_activos++;            			
					}
				}
				if(i%1000 == 0 && i > 0){
					System.out.print(".");
					stdWriter.flush();
					if(i%10000 == 0){
			    		System.out.println("numdocs=" + i);			    		
					}
				}
			} 
			
			long time_fin = System.currentTimeMillis();
			System.out.println("Time ms: " + (time_fin - time_ini));
			
			long end_time = System.currentTimeMillis();
			System.out.println("\nTime: " + (end_time - start_time)/1000 + "s" + "\n");
			stdWriter.flush();
			outputStream.close();
		} catch (IOException e) {
            System.err.println("Error writing topic distance file: ");
			e.printStackTrace();
		} 
	}
	
//	private static void saveSimilarityMatrixBin(short[][] docTopicValues) {
//		try {
//	        FileOutputStream outputStream;
//			outputStream = new FileOutputStream(new File(outputDir + File.separator + "edges.csv"));
//	        BufferedWriter stdWriter = new BufferedWriter(new OutputStreamWriter(outputStream,"UTF-8"));
//	
//			long cnt_activos = 0;
//			long start_time = System.currentTimeMillis();
//			
//			//header
//			stdWriter.write("\"Source\",\"Target\",\"Weight\",\"Type\"\n");
//
//			long time_ini = System.currentTimeMillis();
//			
//			for(int i = 0; i < numdocs; i++){
//				for(int j = i+1; j < numdocs; j++){
//					float distance = MetricsUtils.jsd_tuning2(docTopicValues[i], docTopicValues[j], epsylon, epsylon_2_2sqrt_short, epsylon_cota2_2);
//
//					
//					if(distance < epsylon){
//						double distanceNormalized = 0.5d*(epsylon - distance)/epsylon + 0.5d; 
//						String str = df4.format(distanceNormalized);
//						
//						// Normalizar distancia
//						stdWriter.write(i + "," + j + "," + str + ",\"Undirected\"\n");
//						
//						connectedNode[i] = true;
//						connectedNode[j] = true;
//						
//						cnt_activos++;            			
//					}
//				}
//				if(i%1000 == 0 && i > 0){
//					long time_fin = System.currentTimeMillis();
//					System.out.println("Time ms: " + (time_fin - time_ini) + " numdocs=" + i);
//					
//					//System.out.print(".");
//					stdWriter.flush();
//					if(i%10000 == 0){
//			    		System.out.println("numdocs=" + i);			    		
//					}
//				}
//			} 
//			
//			long time_fin = System.currentTimeMillis();
//			System.out.println("Time ms: " + (time_fin - time_ini));
//		
//			long end_time = System.currentTimeMillis();
//			System.out.println("\nTime: " + (end_time - start_time)/1000 + "s" + "\n");
//			stdWriter.flush();
//			outputStream.close();
//			
//		} catch (IOException e) {
//            System.err.println("Error writing topic distance file: ");
//			e.printStackTrace();
//		} 
//	}	
     
	
	private static void saveSimilarityMatrixBinParallel(short[][] docTopicValues) {
		try {
	        FileOutputStream outputStream;
			outputStream = new FileOutputStream(new File(outputDir + File.separator + "edges.csv"));
	        BufferedWriter stdWriter = new BufferedWriter(new OutputStreamWriter(outputStream,"UTF-8"));
	
			long start_time = System.currentTimeMillis();
			
			//header
			stdWriter.write("\"Source\",\"Target\",\"Weight\",\"Type\"\n");
			
			// parallel
		    IntStream.range(0, numdocs) 
             .parallel() 
             .forEach( id -> {
            	long time_ini = System.currentTimeMillis();
				try {
					//TODO param, choose in memory or write to disk after each operation
					productSequence(id, docTopicValues, epsylon, epsylon_2_2sqrt_short, epsylon_cota2_2, stdWriter);
					if(id%1000==0){
						long time_fin = System.currentTimeMillis();
						System.out.println("numdocs: " + id + ", time (ms): " + (time_fin - time_ini));
						time_ini = System.currentTimeMillis();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}); 

		    
			try {
				stdWriter.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}

			long end_time = System.currentTimeMillis();
			System.out.println("\nTime: " + (end_time - start_time) + " ms" + "\n");
			stdWriter.flush();
			outputStream.close();
			
		} catch (IOException e) {
            System.err.println("Error writing topic distance file: ");
			e.printStackTrace();
		} 
	}		
	
	private static void productSequence(int i, short[][] docTopicValues, float epsylon, float epsylon_2_2sqrt_short, float epsylon_cota2_1, BufferedWriter stdWriter) throws IOException{
		for(int j = i+1; j < numdocs; j++){
			// va mejor tuning 2 que 3 !!
			float distance = MetricsUtils.jsd_tuning2(docTopicValues[i], docTopicValues[j], epsylon, epsylon_2_2sqrt_short, epsylon_cota2_2);
	
			if(distance < epsylon){
				// Normalize distance
				double distanceNormalized = 0.5d*(epsylon - distance)/epsylon + 0.5d; 
				String str = df4.format(distanceNormalized);
				
				stdWriter.write(i + "," + j + "," + str + ",\"Undirected\"\n");
				
				connectedNode[i] = true;
				connectedNode[j] = true;         			
			}
		}
	}
	



//	private static void productSequenceLog(int i, short[][] docTopicValues, float epsylon, float epsylon_2_2sqrt_short, float epsylon_cota2_1, BufferedWriter stdWriter, float[][] logDocTopicValues) throws IOException{
//		for(int j = i+1; j < numdocs; j++){
//			float distance = MetricsUtils.jsd_tuning6(docTopicValues[i], docTopicValues[j], logDocTopicValues[i], logDocTopicValues[j], epsylon, epsylon_2_2sqrt_short, epsylon_cota2_2);
//			
//			if(distance < epsylon20000f){///20000f
//				double distanceNormalized = 0.5d*(epsylon - distance/20000f)/epsylon + 0.5d; 
//				String str = df4.format(distanceNormalized);
//				
//				// Normalizar distancia
//				stdWriter.write(i + "," + j + "," + str + ",\"Undirected\"\n");
//				
//				connectedNode[i] = true;
//				connectedNode[j] = true;        			
//			}
//		}
//	}	
	
//    private static float[][] calculateLogMatrix(short[][] docTopicValues) {
//		float[][] logDocTopicValues = new float[numdocs][numtopics];
//
//		// parallel
//	    IntStream.range(0, numdocs) 
//         .parallel() 
//         .forEach( id -> {
//        	 logDocTopicValues[id] = calculateLog(docTopicValues[id]);
//		}); 
//		return logDocTopicValues;
//	}

//	private static float[] calculateLog(short[] topicValues) {
//		float[] logTopicVector = new float[numtopics];
//		for(int i=0; i<numtopics; i++){
//			logTopicVector[i] = (float) Math.log(topicValues[i]);
//		}
//		return logTopicVector;
//	}



//	private static double[] cleanEnglishTopics(double[] ds) {
//		for(int i=0; i< ds.length; i++){
//			for(int j=0; j < englishTopics.length; j++){
//				if(englishTopics[j] == i){
//					ds[i] = 0;
//				}
//			}
//		}
//		return ds;
//	}
    
	private static int loadBinTopics(String fileName, int numdocs, short[][] docTopicValues) throws UnsupportedEncodingException, IOException {
        FileInputStream inputStream = new FileInputStream(new File(inputDir + File.separator + fileName));
 
        byte[] lengthFieldBytes = new byte[2];
        
        int numread = 0;
        int lines = 0;
         
        try { 
            DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
            simbolos.setDecimalSeparator('.');                         
            
            while (numread >= 0) {            	
        		// read record length from header
    	    	numread = inputStream.read(lengthFieldBytes,0,2); 
    	    	
    	    	// read data
    	    	if(numread > 0){	
    	    		short dataLength = (short)( ((lengthFieldBytes[1] & 0xFF)<<8) | (lengthFieldBytes[0] & 0xFF) );
    	    		dataLength-=2;// minus length field
    	    		byte[] dataBytes = new byte[dataLength];
    	    		
    	    		numread = inputStream.read(dataBytes, 0, dataLength); 
    	    		if(numread == dataLength){
    	    			if(lines == 10105){
    	    				System.out.println("");
    	    			}
    	    			readContentBinLine(dataBytes, dataLength, lines, docTopicValues);
    	    			lines++;
    	    		} else {
    	    			numread = -1;
    	    			System.out.println("Error: incomplete line.");        	
    	    		}
    	    	}
            }
            return lines;
            
        } catch (Exception e) {
            System.err.println("Error reading topic file. ");
            e.printStackTrace();
        } finally{
        	inputStream.close();
        }
        return 0;
    }    

	private static int loadTopics(String fileName, int numdocs, double[][] docTopicValues) throws UnsupportedEncodingException, IOException {
        FileInputStream inputStream = new FileInputStream(new File(inputDir + File.separator + fileName));
    	BufferedReader stdInReader = new BufferedReader(new InputStreamReader(inputStream,"UTF-8"));
 
        int cnt = 0;
         
        try {
            String line;
 
            DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
            simbolos.setDecimalSeparator('.');                         
            
            while ((line = stdInReader.readLine()) != null) {
                if(cnt > 0){
	            	String[] result = line.split("\\t");
                	docNames[cnt-1] = result[1].replace("\"", "").replace("/","-").toUpperCase().trim();
	                
	                for (int i=0; i<result.length; i++){ 
	                    if(i%2 == 1 && i > 1){
	                        int pos = Integer.parseInt(result[i-1]);
	                        docTopicValues[cnt-1][pos] = Double.valueOf(result[i]).doubleValue();
	                    }                       
	                }
                }
	            cnt++;
            }
            return cnt;
            
        } catch (Exception e) {
            System.err.println("Error reading topic file: ");
            e.printStackTrace();
        } finally{
        	inputStream.close();
        	stdInReader.close();
        }
        return 0;
    }
     
    private static int inspectBinTopicFile(String fileName) throws IOException {
    	File file = new File(inputDir + File.separator + fileName);
    	InputStream inputStream = new FileInputStream(file);

    	int lines = 0;    	
    	short maxtopic = 0;
    	int numread = 0;
    	
		byte[] lengthFieldBytes = new byte[2];
   	
    	// reg size
    	while(numread >= 0) {
    		// read record length from header
	    	numread = inputStream.read(lengthFieldBytes,0,2);   
	    	
	    	// read data
	    	if(numread > 0){		
	    		short dataLength = (short)( ((lengthFieldBytes[1] & 0xFF)<<8) | (lengthFieldBytes[0] & 0xFF) );
	    		dataLength-=2;// minus length field
	    		byte[] dataBytes = new byte[dataLength];
	    		
	    		numread = inputStream.read(dataBytes, 0, dataLength); 
	    		if(numread == dataLength){
	    			maxtopic = readBinLine(dataBytes, dataLength, maxtopic);
	    			lines++;
	    		} else {
	    			numread = -1;
	    			System.out.println("Error: incomplete line.");
	    		}
	    	}	
    	}

        numdocs = lines;
        numtopics = (short) (maxtopic+1);
    	
    	// close
        inputStream.close();
        
    	return numdocs;
	}


    private static int inspectTopicFile(String fileName) throws IOException {
    	File file = new File(inputDir + File.separator + fileName);
    	InputStream inputStream  = new FileInputStream(file);
    	BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream,"UTF-8"));;

    	int lines = 0;
    	String line;
    	short maxtopic = 0;

        while ((line = reader.readLine()) != null) {
        	maxtopic = readLine(line, maxtopic);
    		lines++;
        }
    	reader.close();    	
    	inputStream.close();

        numdocs = lines-1;
        numtopics = (short) (maxtopic+1);
        
    	return numdocs;
	}
    
	private static void readContentBinLine(byte[] lineBytes, short dataLength, int numdoc, short[][] docTopicValues) {
		
		// num non zero topics
		short num_non_zero_topics=(short)( ((lineBytes[1] & 0xFF)<<8) | (lineBytes[0] & 0xFF) );		
		short offset = 2;
		
		for (int i=0; i<num_non_zero_topics; i++){ 
			// topic number
			byte loNumTopic = lineBytes[i*4 + offset];
			byte hiNumTopic = lineBytes[i*4 + 1 + offset];
			short numTopic = (short)( ((hiNumTopic & 0xFF)<<8) | (loNumTopic & 0xFF) );		
			
			//topic value
			byte loTopicValue = lineBytes[i*4 + 2 + offset];
			byte hiTopicValue = lineBytes[i*4 + 3 + offset];
			short valueTopic = (short)( ((hiTopicValue & 0xFF)<<8) | (loTopicValue & 0xFF) );	
			
			docTopicValues[numdoc][numTopic] = valueTopic;
		}
		short name_offset = (short) (num_non_zero_topics*4 + offset);
		byte [] nameBytes = Arrays.copyOfRange(lineBytes, name_offset, dataLength);
		String nameDoc = new String(nameBytes);
		
		docNames[numdoc] = nameDoc;		
	}    
    
	private static short readBinLine(byte[] lineBytes, short dataLength, short maxtopic) {
		// num non zero topics
		short num_non_zero_topics=(short)( ((lineBytes[1] & 0xFF)<<8) | (lineBytes[0] & 0xFF) );
		
		short offset = 2;
		for (int i=0; i<num_non_zero_topics; i++){ 
			byte loNumTopic = lineBytes[i*4 + offset];
			byte hiNumTopic = lineBytes[i*4 +1 + offset];
			short numTopic = (short)( ((hiNumTopic & 0xFF)<<8) | (loNumTopic & 0xFF) );		
	        if(numTopic > maxtopic){
	        	maxtopic = numTopic;
	        }			
		}
		return maxtopic;
	}

	private static short readLine(String line, short maxtopic) {
		String[] result = line.split("\\t");
		
		for (int i=0; i<result.length; i++){ 
		    if(i%2 == 1 && i > 1){
		    	short pos = Short.parseShort(result[i-1]);
		        if(pos > maxtopic){
		        	maxtopic = pos;
		        }
		    }                       
		}
		return maxtopic;
	}

    
    public static void deleteFolder(File folder, boolean recursive) {
        File[] files = folder.listFiles();
        if(files!=null) { //some JVMs return null for empty dirs
            for(File f: files) {
            	if(recursive){
	                if(f.isDirectory()) {
	                    deleteFolder(f, recursive);
	                } else {
	                    f.delete();
	                }
            	} else {
            		deleteFolder(f,recursive);
            	}
            }
        }
        folder.delete();
    }       
 
}