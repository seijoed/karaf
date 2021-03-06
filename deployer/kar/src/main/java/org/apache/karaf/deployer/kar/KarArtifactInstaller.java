/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.karaf.deployer.kar;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.felix.fileinstall.ArtifactInstaller;
import org.apache.karaf.features.FeaturesService;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class KarArtifactInstaller implements ArtifactInstaller {

	private static Log logger = LogFactory.getLog(KarArtifactInstaller.class);
	
	private static final String KAR_PREFIX = ".kar";
	private static final String ZIP_PREFIX = ".zip";

	private String localRepoPath = "./local-repo";

	private String timestampPath;
	
	private byte[] buffer = new byte[5 * 1024];
	
	private DocumentBuilderFactory dbf;
	
	private FeaturesService featuresService;
	
	public void init() {         
		dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
    
		timestampPath = localRepoPath + File.separator + ".timestamps";
		if (new File(timestampPath).mkdirs()) { 
			logger.warn("Unable to create directory for Karaf Archive timestamps. Results may vary...");
		}
		
		if (logger.isInfoEnabled()) { 
			logger.info("Karaf archives will be extracted to " + localRepoPath); 
			logger.info("Timestamps for Karaf archives will be extracted to " + timestampPath); 
			
		} 
	}

	public void destroy() { 
		logger.info("Karaf archive installer destroyed.");
	}


	public void install(File file) throws Exception {
		// Check to see if this file has already been extracted. For example, on restart of Karaf,
		// we don't necessarily want to re-extract all the Karaf Archives!
		//
		if (alreadyExtracted(file)) { 
			logger.info("Ignoring '" + file + "'; timestamp indicates it's already been deployed.");
			return; 
		}
		
		if (logger.isInfoEnabled()) 
			logger.info("Installing " + file);
		
		ZipFile zipFile = new ZipFile(file);
		
		Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) zipFile.entries();
		while (entries.hasMoreElements()) { 
			ZipEntry entry = (ZipEntry) entries.nextElement();

			if (! entry.getName().startsWith("META-INF")) { 
				if (entry.isDirectory()) { 
					java.io.File directory = new File(localRepoPath + File.separator + entry.getName());
					if (logger.isDebugEnabled())
						logger.debug("Creating directory '" + directory.getName());
					directory.mkdirs();
				} else { 
					File extract = new File(localRepoPath + File.separator + entry.getName());
			        BufferedOutputStream bos = new BufferedOutputStream(
			                new FileOutputStream(extract));
			        
		            int count = 0;
		            int totalBytes = 0;
		            InputStream inputStream = zipFile.getInputStream(entry);
		            while ((count = inputStream.read(buffer)) > 0)
		            {
		            	bos.write(buffer, 0, count);
		            	totalBytes += count;
		            }
		            
		            if (logger.isDebugEnabled()) 
		            	logger.debug("Extracted " + totalBytes + " bytes to " + extract);
		            
		            bos.close();	
		            inputStream.close();
		            
		            if (isFeaturesRepository(extract)) { 
						addToFeaturesRepositories(extract);
		            }
				}
			}
		}
		
		zipFile.close();
		
		updateTimestamp(file);
	}

	public void uninstall(File file) throws Exception {
		logger.warn("Karaf archive '" + file + "' has been removed; however, it's feature URLs have not been deregistered, and it's bundles are still available in '" + localRepoPath + "'.");
	}

	public void update(File file) throws Exception {
		logger.warn("Karaf archive " + file + " has been updated; redeploying.");
		install(file);
	}
	
	protected void updateTimestamp(File karafArchive) throws Exception {
		File timestamp = getArchiveTimestampFile(karafArchive);
		
		if (timestamp.exists()) {
			if (logger.isDebugEnabled())
				logger.debug("Deleting old timestamp file '" + timestamp + "");
			
			if (!timestamp.delete()) { 
				throw new Exception("Unable to delete archive timestamp '" + timestamp + "'");
			}
		}
		
		logger.debug("Creating timestamp file '" + timestamp + "'");
		timestamp.createNewFile();		
	}
	
	protected boolean alreadyExtracted(File karafArchive) {
		File timestamp = getArchiveTimestampFile(karafArchive);
		if (timestamp.exists()) { 
			return timestamp.lastModified() >= karafArchive.lastModified();
		}
		return false;
	}

	protected File getArchiveTimestampFile(File karafArchive) {
		return new File(localRepoPath + File.separator + ".timestamps" + File.separator + karafArchive.getName());	
	}
	
	protected boolean isFeaturesRepository(File artifact)  { 
        try {
			if (artifact.isFile() && artifact.getName().endsWith(".xml")) {
			    Document doc = parse(artifact);
			    String name = doc.getDocumentElement().getLocalName();
			    String uri  = doc.getDocumentElement().getNamespaceURI();
			    if ("features".equals(name) && (uri == null || "".equals(uri))) {
			        return true;
			    }
			}
		} catch (Exception e) {
			if (logger.isDebugEnabled()) 
				logger.debug("File " + artifact.getName() + " is not a features file.", e);
		}
		return false;
	}
	
    protected Document parse(File artifact) throws Exception {
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException exception) throws SAXException {
            }
            public void error(SAXParseException exception) throws SAXException {
            }
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return db.parse(artifact);
    }	
	
	private void addToFeaturesRepositories(File file)  {
		try {
			featuresService.addRepository(file.toURI());
			if (logger.isInfoEnabled())  
				logger.info("Added feature repository '" + file.toURI() + "'.");
		} catch (Exception e) {
			logger.error("Unable to add repository '" + file.getName() + "'", e);
		}
	}
	
	public boolean canHandle(File file) {
		// If the file ends with .kar, then we can handle it!
		//
		if (file.isFile() && file.getName().endsWith(KAR_PREFIX)) {
			logger.info("Found a .kar file to deploy.");
			return true;
		}	
		// Otherwise, check to see if it's a zip file containing a META-INF/KARAF.MF manifest.
		//
		else if (file.isFile() && file.getName().endsWith(ZIP_PREFIX)) {		
			logger.debug("Found a .zip file to deploy; checking contents to see if it's a Karaf archive.");
			try {
				if (new ZipFile(file).getEntry("META-INF/KARAF.MF") != null) { 
					logger.info("Found a Karaf archive with .zip prefix; will deploy.");
					return true;
				}
			} catch (Exception e) {
				logger.warn("Problem extracting zip file '" + file.getName() + "'; ignoring.", e);
			}
		}
				
		return false;	
	}

	public boolean deleteLocalRepository() {
		return deleteDirectory(new File(localRepoPath));
	}
	
	private boolean deleteDirectory(File path) {
		if (path.exists()) {
			File[] files = path.listFiles();
			for (int i = 0; i < files.length; i++) {
				if (files[i].isDirectory()) {
					deleteDirectory(files[i]);
				} else {
					files[i].delete();
				}
			}
		}
		return (path.delete());
	}

	public void setLocalRepoPath(String localRepoPath) {
		this.localRepoPath = localRepoPath;
	}

	public void setFeaturesService(FeaturesService featuresService) {
		this.featuresService = featuresService;
	}
	
}
