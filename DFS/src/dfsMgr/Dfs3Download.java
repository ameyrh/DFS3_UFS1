package dfsMgr;

import dfs3test.communication.Sender;
import dfs3Util.TLVParser;
import dfs3test.encrypt.Encrypt;
import dfs3test.encrypt.Hash;
import dfs3test.xmlHandler.InodeReader;
import dfs3test.xmlHandler.ReadInode;
import init.DFSConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

//import static Communication.ForwardXML.xmlSender;
import static dfsMgr.Download.Reassembly.stichfiles;
import static dfs3Util.file.*;
import static dfs3test.encrypt.Hash.comparehash;
import static dfs3test.xmlHandler.XMLWriter.writer;

/**
 * Class responsible for performing Download.
 * This class downloads the file from the DFS.
 * <p><b>Functions:</b> At the user end of DFS.</p>
 * <b>Note:</b> Change this file to change functionality
 * related to Download.
 * @author <a href="https://t.me/sidharthiitk">Sidharth Patra</a>
 * @since   15th Feb 2020
 */
public class Dfs3Download{
    static String fileName = null;
    static int segmentCount = 0;
    static TreeMap splitParts =null;
    static TreeMap splits= null;
    static String fileURI=null;
    /**
     * Starts the download process.
     * This method starts the download of the file from DFS.
     * It interacts with the communication manager on DFS behalf for
     * download.
     * @param fileuri fileUri of the file that user selected
     * @throws IOException for input output exception
     * @throws GeneralSecurityException In case of general security violation occurs
     */
    public static void start(String fileuri) throws IOException, GeneralSecurityException {
        //Extracting String of fileName from FileURI
        fileURI=fileuri;
        System.out.println(fileURI +" being downloaded");
        String[] parts = fileURI.split("/");
        int i = parts.length;
        fileName = parts[i-1];
        //System.out.println(fileName);
        String xmlCachePath=System.getProperty("user.dir") + System.getProperty("file.separator") +"b4dfs"+System.getProperty("file.separator")+"dfsCache"+System.getProperty("file.separator")+ fileName + "_Inode.xml";
        //check whether inode xml corresponding to the file exists in cache
        //System.out.println(xml);
        Path file = Paths.get(xmlCachePath);
        if(Files.exists(file)) {
            //if inode and file parts available in cache, stitch them from local cache itself
            System.out.println("File inode found in local cache...");
            ReadInode readInode = null;
            readInode = dfs3test.xmlHandler.InodeReader.reader(xmlCachePath);
            splitParts = new TreeMap(readInode.getSplitParts());
            System.out.println(splitParts.keySet());

            byte[] completeFile = stitchFromCache(splitParts);
            System.out.println("File Stitched");
            postDownload(fileURI, completeFile);
        }
        else
        {
            System.out.println("Being downloaded from the network...");
            //Download the inode from the network
            String inodeURI = fileURI+"_Inode.xml";

            // get path to splitindex.csv and retrieve the segment inodes

            String hashedInode = Hash.hashpath(inodeURI);
            System.out.println("Hash of inode file:" + hashedInode);
            // xml query  with fileUri.tag for download is 2
            //the data filed is blank hence "Nothing" to avoid null pointer exception
            String xmlPath = writer(2,hashedInode,"Nothing".getBytes(), true);
            // TODO - retrieve the Ip of the node responsible
            // hand over the xml query to xmlSender
            Sender.start(xmlPath, "localhost");

        }

    }//end of start

    private static byte[] stitchFromCache(TreeMap splitParts) {
        String dir = System.getProperty("user.dir") + System.getProperty("file.separator") +"b4dfs"+System.getProperty("file.separator")+"dfsCache"+System.getProperty("file.separator");
        byte[] completeFile = new byte[0];
        Set set = splitParts.keySet();
        Object[] o = set.toArray();
        String[] splits = new String[set.size()];
        for (int i =0; i<set.size(); i++)
        {
            splits[i]=dir+o[i].toString();
        }
        for(int i =0;i< splits.length  && !(splits[i]==null);i++){
            // read data of each segment
            byte[] segmentData = readdata(splits[i]);
            // concat the segment data to the completefile byte array
            completeFile = Encrypt.concat(completeFile,segmentData);
            // delete the segments once their data has been read and
            // concatenated
            File f;
            // f = new File(splits[i]);
            //f.delete();
        }
        return completeFile;
    }

    /**
     * This method receives the downloaded segments by interacting with
     * the communication manager and processes them further.
     * This method executes all the functions related to  segment
     * downloading. It downloads the segments and sends them
     * for sequencing and stitching up to derive the complete filepluskey.
     * @param inbound byte array received from the communication manager
     * @throws IOException for input output exception
     * @throws GeneralSecurityException In case of general security violation occurs
     */
    public static void segmentDownload(byte[] inbound) throws GeneralSecurityException, IOException {
        System.out.println("so far so good!");
        // send segments for reassembly
        //String downloadPath = System.getProperty("user.dir") + System.getProperty("file.separator")+"Downloaded";
        //Files.createDirectory(Path.of(downloadPath));
        //String filePath = downloadPath+fileName;
        Reassembly.start(inbound,fileName);
        segmentCount++;
        System.out.println(segmentCount+" files have been downloaded");
        List<String> splitList = new ArrayList<String>(splits.keySet());
        String[] segmentInodes= splitList.toArray(new String[0]);
        // if all segments have been downloaded then start stitching them
        if(segmentCount == segmentInodes.length) {
            byte[] completeFile = stitchFromCache(splits);
            System.out.println("File Stitched");
            postDownload(fileURI, completeFile);
            ;
        }
        else
            System.out.println("Download in progress");

        /*
        String splitFile = System.getProperty("user.dir") + System.getProperty("file.separator")+"b4dfs"+System.getProperty("file.separator")+"dfsCache"+fileName+"_Inode.csv";

        //String[] segmentInode = csvreader(splitFile,fileName);
        // increment segment count on receiving each segment
        segmentCount++;
        System.out.println(+segmentCount+" files have been downloaded");
        // if all segments have been downloaded then start stitching them
        if(segmentCount == segmentInodes.length)
            stichfiles(fileName,segmentCount);
        else
            System.out.println("Download in progress"); */
    }
    /**
     * receives the complete filepluskey after stitching
     * from reassembly. This method checks up the completefile for
     * integrity by comparing hash with the hash stored in uploaded.csv.
     * @param completefile this is the filepluskey for further processing
     * @throws IOException for input output exception
     * @throws GeneralSecurityException In case of general security violation occurs
     */

    public static void postDownload(String fileURI, byte[] completefile) throws GeneralSecurityException, IOException {

        // compare the hash of completefile with the original filepluskey
        boolean hashMatch = comparehash(fileURI,completefile);
        // retrieve data, decrypt data after decrypting key
        // write the file on decrypting data
        if(hashMatch){
            // if the hash matches then retrieve encrypted key
            byte[] encKey = TLVParser.startParsing(completefile, 2);
            // retrieve framed data
            byte[] serialisedEncData = deconcat(completefile,encKey.length+8);
            // retrieve encrypted data after parsing the TLV
            byte[] encData = TLVParser.startParsing(serialisedEncData, 3);
            // get decrypted data
            byte[] data = Encrypt.startDec(encData,encKey);
            // write the data to original inode for the user
            String[] writePath = fileName.split("@@");
            writeData(data,writePath[0]);
        }
        else
            System.out.println("hash mismatch");
        System.out.println("The Download is complete");
    }

    public static void inodeDownload(byte[] decoded) {
        int sequenceNo = 0;
        // parse the down loaded segements by Type, Length and value
        // here sequenced means the parsed byte array
        byte[] sequenced = TLVParser.startParsing(decoded, 4);
        // if three Zeros follow the value right at start then the value
        // is sequence number
        if(sequenced[1]==0 && sequenced[2]==0 &&sequenced[3]==0)
            sequenceNo = sequenced[0];
        //byte[] data = TLVParser.startParsing(sequenced,4);

        // after retrieving the sequence number retain only value
        // discard type and length
        byte[] data = deconcat(decoded,16);
        String xmlCachePath=System.getProperty("user.dir") + System.getProperty("file.separator") + fileName + "_Inode.xml";
        writeData(data,xmlCachePath);
        ReadInode inodeReader = null;
        try{
            inodeReader = InodeReader.reader(xmlCachePath);
            System.out.println("File Name:" + inodeReader.getFileName());
            HashMap<String, String> splitList = inodeReader.getSplitParts();
            splits= new TreeMap(splitList);
            for(String splitPart : splitList.keySet()) {
                String hashedInode = Hash.hashpath(DFSConfig.getRootinode() + splitPart);
                // xml query  with inode.tag for download is 2
                //the data filed is blank hence "Nothing" to avoid null pointer exception
                String xmlPath = writer(2, hashedInode, "localhost".getBytes(), false);
                System.out.println("Query for "+DFSConfig.getRootinode() + splitPart + " sent to network");
                System.out.println("Query sent: "+ hashedInode);
                // TODO - retrieve the Ip of the node responsible
                // hand over the xml query to xmlSender
                Sender.start(xmlPath, "localhost");
            }


        }
        catch(Exception e){
            System.out.println("This is not an inode file");
        }

    }

    public static class Reassembly {

        private static final String dir = System.getProperty("user.dir") +
                System.getProperty("file.separator")+"b4dfs"+System.getProperty("file.separator")+"dfsCache"+System.getProperty("file.separator");
        public static void start(byte[] inbound,String fileName) throws IOException {
            // send the file for sequencing
            sequencing(inbound,fileName);
        }
        /**
         * Split a file into multiples files.
         *
         * @param fileName  Name of file to be split.
         * @param inbound number of kilo bytes per chunk.
         * @throws IOException
         */
        public static void sequencing(byte[] inbound,String fileName) throws IOException {
            int sequenceNo = 0;
            // parse the down loaded segements by Type, Length and value
            // here sequenced means the parsed byte array
            byte[] sequenced = TLVParser.startParsing(inbound, 4);
            // if three Zeros follow the value right at start then the value
            // is sequence number
            if(sequenced[1]==0 && sequenced[2]==0 &&sequenced[3]==0)
                sequenceNo = sequenced[0];
            //byte[] data = TLVParser.startParsing(sequenced,4);
            System.out.println("Segment No: "+sequenceNo+"under progress");
            // after retrieving the sequence number retain only value
            // discard type and length
            byte[] data = deconcat(inbound,16);
            // access the splitIndex to retrieve the inode for the segment

            //String splitFile = dir + "SplitIndex.csv";
            //String[] segmentInode = csvreader(splitFile,fileName);
            List<String> splitList = new ArrayList<String>(splits.keySet());
            String[] segmentInodes= splitList.toArray(new String[0]);

            for(int i=0; i<=segmentInodes.length; i++)
            {
             if(sequenceNo==i) {
                 String segmentInode = segmentInodes[i-1];
                 System.out.println("Writing segmentInode:" + segmentInode);
                 String writePath = dir + segmentInode;
                 // write the segmentdata to the segment inode
                 writeData(data, writePath);
             }

            }


        }


        /**
         * Split a file into multiples files.
         *
         * @param fileName  Name of file to be split.
         * @param fileCount number of kilo bytes per chunk.
         * @throws IOException
         */
        public static void stichfiles (String fileName,int fileCount) throws IOException, GeneralSecurityException {

            String[] segmentInode = csvreader(dir + "SplitIndex.csv",fileName);
            // create a byte array where the all the segments will be merged
            byte[] completeFile = new byte[0];
            // loop runs till all the segments are addressed
            // the null condition is there as csv reader returns array with null values and
            // inodes both if this is fixed the condition can be removed
            for(int i =0;i<segmentInode.length  && !(segmentInode[i]==null);i++){
                // read data of each segment
                byte[] segmentData = readdata(segmentInode[i]);
                // concat the segment data to the completefile byte array
                completeFile = Encrypt.concat(completeFile,segmentData);
                // delete the segments once their data has been read and
                // concatenated
                File f;
                f = new File(segmentInode[i]);
                f.delete();
            }
            // pass the complete file byte array for post download processing
            // such as matching the hash and writing to disk
            //postDownload(completeFile);
        }
    }
}