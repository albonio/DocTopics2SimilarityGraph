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
 
 
 
 
public class DocTopics2SimilarityGraph {
	static String outputDir = "output2";
    static String inputDir  = "input";
    
    private static DecimalFormat df4;
	
    private static boolean[] connectedNode;
    private static String[] docNames;
    private static int numdocs = 0;
    private static short numtopics = 0;
    /////private static float epsylon = 0.15f;//0.07f;//cordis
    private static float epsylon = 0.15f;
    //0.20f;// setsi 10K 0.20f;
    //0.14f; //0.035f;//0.070f;// TODO param
    
    private static float epsylon_2_2sqrt = (float) (2*Math.sqrt(2*epsylon));
    private static float epsylon_2_2sqrt_short = (float) (epsylon_2_2sqrt*10000);  
    private static float epsylon_cota2_2 = (float) (Math.sqrt((-72f + 24f*Math.sqrt(9+2*epsylon)))*10000);
    private static float epsylon20000f = epsylon*20000f;
    
    
    private static float epsylonEng = 0.08f; //0.035f;//0.070f;// TODO param
    
    private static int[] englishTopics = {};
//TODO desigualdad triangualar
 
    private static String fileName  = "PlanEstatalENE.doc_topics.bin";
    private static String fileNameNodeMetadata  = "PlanEstatalENE-metadata-2004-2016.csv";
    private static boolean saveNodeFile = true;
    private static boolean bin = true;

    
    public static void main(String[] args) {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
        simbolos.setDecimalSeparator('.');    
        df4 = new DecimalFormat("#.####", simbolos);

//        deleteFolder(new File(outputDir), false);
//        File outputDirFile = new File(outputDir);
//        outputDirFile.mkdir();
                 
        if (args.length == 7) {
        	outputDir = args[0];    
        	inputDir  = args[1];	

        	try{
        		epsylon = Float.parseFloat(args[2]);
        	} catch (NumberFormatException e) {
                System.err.println("Argument " + args[2] + " must be an float.");
                System.exit(1);
            }
        	fileName = args[3];    
        	fileNameNodeMetadata  = args[4];	
        	
        	
        	
        	int saveNodeFile_int = 0;
            try {
            	saveNodeFile_int = Integer.parseInt(args[5]);
            } catch (NumberFormatException e) {
                System.err.println("Argument " + args[5] + " must be an integer.");
                System.exit(1);
            }
            if(saveNodeFile_int > 0){
            	saveNodeFile = true;
            } else {
            	saveNodeFile = false;
            }
            
        	int bin_int = 0;
            try {
            	bin_int = Integer.parseInt(args[6]);
            } catch (NumberFormatException e) {
                System.err.println("Argument " + args[6] + " must be an integer.");
                System.exit(1);
            }
            if(bin_int > 0){
            	bin = true;
            } else {
            	bin = false;
            }
        } else {
        	usage();
        	System.exit(1);
        }
        
        try {
        	if(fileName.endsWith(".bin")){
        		bin = true;
        		inspectBinTopicFile(fileName);
        	} else {
        		inspectTopicFile(fileName);
        	}

            if(numdocs == 0){
            	System.out.println("Error loading doc-topics: incorrect numdocs...");
            	return;            	
            }
            
            if(numtopics == 0){
            	System.out.println("Error loading doc-topics: incorrect numtopics...");
            	return;            	
            }
            

            docNames = new String[numdocs];  
            connectedNode = new boolean[numdocs];
            
            System.out.println("Loading doc-topics, numdocs: " + numdocs + ", numtopics: " + numtopics);
            
            if(bin){
            	short[][] docTopicValues = new short[numdocs][numtopics];
                if(loadBinTopics(fileName, numdocs, docTopicValues) == 0){
                	System.out.println("Error loading bin doc-topics...");
                	return;
                }  
//                long t1 = System.currentTimeMillis();
//                saveSimilarityMatrixBinParallel_tuning1(docTopicValues);  
                long t2 = System.currentTimeMillis();
                saveSimilarityMatrixBinParallel(docTopicValues);      
                long t3 = System.currentTimeMillis();
//                saveSimilarityMatrixBin(docTopicValues);
//                long t4 = System.currentTimeMillis();
                
//                System.out.println("saveSimilarityMatrixBinParallel_tuning1: " + (t2-t1));
                System.out.println("saveSimilarityMatrixBinParallel:         " + (t3-t2));
//                System.out.println("saveSimilarityMatrixBin:                 " + (t4-t3));
                
            } else {
            	double[][] docTopicValues = new double[numdocs][numtopics];
                if(loadTopics(fileName, numdocs, docTopicValues) == 0){
                	System.out.println("Error loading doc-topics...");
                	return;
                }             
                saveSimilarityMatrix(docTopicValues);
            } 
            
            
            // node metadata 
            if(saveNodeFile){
            	saveNodeFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



	private static void usage() {
		System.out.println("arg0: outputDir");
		System.out.println("arg1: inputDir");
		System.out.println("arg2: epsylon");
		System.out.println("arg3: fileName");
		System.out.println("arg4: fileNameNodeMetadata");
		System.out.println("arg5: saveNodeFile [0|1]");
		System.out.println("arg6: bin [0|1]");
	}



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
	    	int cnt_isciii = 0;
	    	String DEFAULT_SEPARATOR = ",";	    	
	    	
            while ((line = stdInReader.readLine()) != null) {
                if(cnt > 0){
	            	String[] result = line.split(DEFAULT_SEPARATOR);
	            	
                	String docName = result[0].replace("\"", "").replace("/","-").toUpperCase().trim();
	                
                	int iddoc = getDocId(docName);
                	if(iddoc >= 0 && connectedNode[iddoc]){
                		//String isciii = DEFAULT_SEPARATOR + " 0";
                		
//                		if(line.toUpperCase().contains("ISCIII")){
//                			isciii = DEFAULT_SEPARATOR + " 1";
//                			cnt_isciii++;
//                		}
                		//line = iddoc + DEFAULT_SEPARATOR + line + isciii + "\n";
                		line = iddoc + DEFAULT_SEPARATOR + line + "\n";
						stdWriter.write(line);
						cnt_out++;
                	}
       	        // header
                } else {
	            	if(line.split(DEFAULT_SEPARATOR).length == 1){
	            		DEFAULT_SEPARATOR = ";";
	            	}
	            	
                	//line = "\"Id\"" + DEFAULT_SEPARATOR + line + DEFAULT_SEPARATOR + "ISCIII\n";
	            	line = "\"Id\"" + DEFAULT_SEPARATOR + line + "\n";
					stdWriter.write(line);
                }
	            cnt++;
            }
            
            System.out.println("metadata nodes read:" + cnt + " active nodes:" + cnt_out + " isciii: " + cnt_isciii);
   			
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
			
//			double distanceNormalizedMax = 0;
//			double distanceNormalizedMin = 10;
			long time_ini = System.currentTimeMillis();
			
			for(int i = 0; i < numdocs; i++){
				for(int j = i+1; j < numdocs; j++){
					// TODO param select measure
					
//					double[] vector1 = cleanEnglishTopics(docTopicValues[i]);
//					double[] vector2 = cleanEnglishTopics(docTopicValues[j]);
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
					////double distance = MetricsUtils.distL2(docTopicValues[i], docTopicValues[j]);
					//double distance = MetricsUtils.distL2(vector1, vector2);
					
					// distania jensen shanon
					//float distance = MetricsUtils.jsd(docTopicValues[i], docTopicValues[j]);
					
					float distance = MetricsUtils.jsd_tuning1(docTopicValues[i], docTopicValues[j], epsylon, epsylon_2_2sqrt);
					
					//float distance = MetricsUtils.jsd(vector1, vector2);
					
					// 
					
					// getNearestTopic					
					if(distance < epsylon){
						double distanceNormalized = 0.5d*(epsylon - distance)/epsylon + 0.5d; 
//						if(distanceNormalizedMax < distanceNormalized){
//							distanceNormalizedMax = distanceNormalized;
//						}
//						if(distanceNormalizedMin > distanceNormalized){
//							distanceNormalizedMin = distanceNormalized;
//						}
						
						String str = df4.format(distanceNormalized);
						//String str = df4.format(distance);
						
						// Normalizar distancia
						stdWriter.write(i + "," + j + "," + str + ",\"Undirected\"\n");
						
						connectedNode[i] = true;
						connectedNode[j] = true;
						
						cnt_activos++;            			
					}
				}
				if(i%1000 == 0 && i > 0){
//					long time_fin = System.currentTimeMillis();
//					System.out.println("Time ms: " + (time_fin - time_ini) + " numdocs=" + i);
					
					System.out.print(".");
					stdWriter.flush();
					if(i%10000 == 0){
			    		System.out.println("numdocs=" + i);			    		
					}
				}
			} 
			
			long time_fin = System.currentTimeMillis();
			System.out.println("Time ms: " + (time_fin - time_ini));
			
//			System.out.println("distanceNormalizedMin: " + distanceNormalizedMin);
//			System.out.println("distanceNormalizedMax: " + distanceNormalizedMax);

			
			long end_time = System.currentTimeMillis();
			System.out.println("\nTime: " + (end_time - start_time)/1000 + "s" + "\n" + "Ejes activos: " + cnt_activos);
			stdWriter.flush();
			outputStream.close();
			
		} catch (IOException e) {
            System.err.println("Error writing topic distance file: ");
			e.printStackTrace();
		} 
	}
	
	private static void saveSimilarityMatrixBin(short[][] docTopicValues) {
		try {
	        FileOutputStream outputStream;
			outputStream = new FileOutputStream(new File(outputDir + File.separator + "edges.csv"));
	        BufferedWriter stdWriter = new BufferedWriter(new OutputStreamWriter(outputStream,"UTF-8"));
	
			long cnt_activos = 0;
			long start_time = System.currentTimeMillis();
			
			//header
			stdWriter.write("\"Source\",\"Target\",\"Weight\",\"Type\"\n");

			long time_ini = System.currentTimeMillis();
			
			for(int i = 0; i < numdocs; i++){
				for(int j = i+1; j < numdocs; j++){
//					boolean engDoc = false;
//					
//					for(int ii=0; ii< englishTopics.length;ii++){
//						if((docTopicValues[i][englishTopics[ii]] > epsylonEng) || (docTopicValues[j][englishTopics[ii]] > epsylonEng)){
//							engDoc = true;
//						}
//					}
//					
//					if(engDoc){
//						continue;
//					}

					float distance = MetricsUtils.jsd_tuning2(docTopicValues[i], docTopicValues[j], epsylon, epsylon_2_2sqrt_short, epsylon_cota2_2);

					
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
					long time_fin = System.currentTimeMillis();
					System.out.println("Time ms: " + (time_fin - time_ini) + " numdocs=" + i);
					
					//System.out.print(".");
					stdWriter.flush();
					if(i%10000 == 0){
			    		System.out.println("numdocs=" + i);			    		
					}
				}
			} 
			
			long time_fin = System.currentTimeMillis();
			System.out.println("Time ms: " + (time_fin - time_ini));
		
			long end_time = System.currentTimeMillis();
			System.out.println("\nTime: " + (end_time - start_time)/1000 + "s" + "\n" + "Ejes activos: " + cnt_activos);
			stdWriter.flush();
			outputStream.close();
			
		} catch (IOException e) {
            System.err.println("Error writing topic distance file: ");
			e.printStackTrace();
		} 
	}	
     
	
	private static void saveSimilarityMatrixBinParallel(short[][] docTopicValues) {
		try {
	        FileOutputStream outputStream;
			outputStream = new FileOutputStream(new File(outputDir + File.separator + "edgesParallel.csv"));
	        BufferedWriter stdWriter = new BufferedWriter(new OutputStreamWriter(outputStream,"UTF-8"));
	
			long cnt_activos = 0;
			long start_time = System.currentTimeMillis();
			
			//header
			stdWriter.write("\"Source\",\"Target\",\"Weight\",\"Type\"\n");

			
			
			// parallel
		    IntStream.range(0, numdocs) 
             .parallel() 
             .forEach( id -> {
            	long time_ini = System.currentTimeMillis();
				try {
					productSequence(id, docTopicValues, epsylon, epsylon_2_2sqrt_short, epsylon_cota2_2, stdWriter);
					if(id%1000==0){
						long time_fin = System.currentTimeMillis();
						System.out.println("numdocs: " + id + ", time ms: " + (time_fin - time_ini));
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
			System.out.println("\nTime: " + (end_time - start_time) + " ms" + "\n" + "Ejes activos: " + cnt_activos);
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
				double distanceNormalized = 0.5d*(epsylon - distance)/epsylon + 0.5d; 
				String str = df4.format(distanceNormalized);
				
				// Normalizar distancia
				stdWriter.write(i + "," + j + "," + str + ",\"Undirected\"\n");
				
				connectedNode[i] = true;
				connectedNode[j] = true;
				
				//cnt_activos++;            			
			}
		}
		//return cnt_activos;
	}
	
	private static void saveSimilarityMatrixBinParallel_tuning1(short[][] docTopicValues) {
		try {
			FileOutputStream outputStream = new FileOutputStream(new File(outputDir + File.separator + "edgesParallel_tuning1.csv"));
	        BufferedWriter stdWriter = new BufferedWriter(new OutputStreamWriter(outputStream,"UTF-8"));
	
			long cnt_activos = 0;
			long start_time = System.currentTimeMillis();
			
			//header
			stdWriter.write("\"Source\",\"Target\",\"Weight\",\"Type\"\n");

			long time_ini = System.currentTimeMillis();
			
			// calculate log matrix
			float[][] logDocTopicValues = calculateLogMatrix(docTopicValues);
			
			// parallel
		    IntStream.range(0, numdocs) 
             .parallel() 
             .forEach( id -> {
				try {
					productSequenceLog(id, docTopicValues, epsylon, epsylon_2_2sqrt_short, epsylon_cota2_2, stdWriter, logDocTopicValues);
					if(id%1000==0){
						long time_fin = System.currentTimeMillis();
						System.out.println("numdocs: " + id + ", time ms: " + (time_fin - time_ini));
						stdWriter.flush();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}); 
		    
			long end_time = System.currentTimeMillis();
			System.out.println("\nTime: " + (end_time - start_time) + " ms" + "\n" + "Ejes activos: " + cnt_activos);
			stdWriter.flush();
			outputStream.close();
			
		} catch (IOException e) {
            System.err.println("Error writing topic distance file: ");
			e.printStackTrace();
		} 
	}	


	private static void productSequenceLog(int i, short[][] docTopicValues, float epsylon, float epsylon_2_2sqrt_short, float epsylon_cota2_1, BufferedWriter stdWriter, float[][] logDocTopicValues) throws IOException{
		//int cnt_activos = 0;
//		int cnt_distancias = 0;
//		int cnt_distancias_epsylon = 0;
		
		for(int j = i+1; j < numdocs; j++){
			//float distance = MetricsUtils.jsd_tuning4(docTopicValues[i], docTopicValues[j], logDocTopicValues[i], logDocTopicValues[j], epsylon, epsylon_2_2sqrt_short);
			//float distance = MetricsUtils.jsd_tuning5(docTopicValues[i], docTopicValues[j], logDocTopicValues[i], logDocTopicValues[j], epsylon, epsylon_2_2sqrt_short, epsylon_cota2_2);
			float distance = MetricsUtils.jsd_tuning6(docTopicValues[i], docTopicValues[j], logDocTopicValues[i], logDocTopicValues[j], epsylon, epsylon_2_2sqrt_short, epsylon_cota2_2);
//			cnt_distancias++;
			
			if(distance < epsylon20000f){///20000f
//				cnt_distancias_epsylon++;
				double distanceNormalized = 0.5d*(epsylon - distance/20000f)/epsylon + 0.5d; 
				String str = df4.format(distanceNormalized);
				
				// Normalizar distancia
				stdWriter.write(i + "," + j + "," + str + ",\"Undirected\"\n");
				
				connectedNode[i] = true;
				connectedNode[j] = true;
				
				//cnt_activos++;            			
			}
		}


//		System.out.println("cnt_distancias: " + cnt_distancias + ", cnt_distancias_epsylon: " + cnt_distancias_epsylon);
		//return cnt_activos;
	}	
	
    private static float[][] calculateLogMatrix(short[][] docTopicValues) {
		float[][] logDocTopicValues = new float[numdocs][numtopics];

		// parallel
	    IntStream.range(0, numdocs) 
         .parallel() 
         .forEach( id -> {
        	 logDocTopicValues[id] = calculateLog(docTopicValues[id]);
		}); 
		return logDocTopicValues;
	}

	private static float[] calculateLog(short[] topicValues) {
		float[] logTopicVector = new float[numtopics];
		for(int i=0; i<numtopics; i++){
			logTopicVector[i] = (float) Math.log(topicValues[i]);
		}
		return logTopicVector;
	}



	private static double[] cleanEnglishTopics(double[] ds) {
		for(int i=0; i< ds.length; i++){
			for(int j=0; j < englishTopics.length; j++){
				if(englishTopics[j] == i){
					ds[i] = 0;
				}
			}
		}
		return ds;
	}
    
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
    	//DataInputStream data_in = new DataInputStream(inputStream); 

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
			
			//docTopicValues[numdoc][numTopic] = (double)((double)(valueTopic)/10000.0d);
			docTopicValues[numdoc][numTopic] = valueTopic;
			
			
			//System.out.println("numdoc: " + numdoc + "\tnumTopic: " + numTopic);
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