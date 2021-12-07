import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class DEFTestFileTransfer {

    public static final String CONFIG_PES_REMOTECACHE = "remoteCache";
    public static final String CONFIG_PES_LOCALCACHE = "localCache";

    public static void main( String[] args ) {


        if (args.length !=2){
            System.out.println("\nDEFTestFileTransfer takes two params\n\t - location to copy from \n\t location to write to\n");
        }  else {

            System.out.println(args[0]);
            System.out.println(args[1]);
        Logger logger = new Logger();
        Logger statusLogger = new Logger();
        doRun(logger,statusLogger,args[0],args[1]);
        }
    }

    public static class Logger {
        public void debug(String _msg){
            output("DEBUG: ",_msg);
        }
        public void error(String _msg){
            output("ERROR: ",_msg);
        }
        public void warn(String _msg){
            output("WARN: ",_msg);
        }
        public void info(String _msg){
            output("INFO: ",_msg);
        }
        private void output(String _type,String _msg){
            System.out.println(_type + ": " + _msg);
        }
    }

    public static void doRun(Logger logger, Logger statusLogger_, String remoteCache_, String localCache_) {
        // get list of docs in remoteDir
        // for each doc in list...
        //    copy doc in list to local cache
        //    if copy successful post on queue
        //    if post successful, delete doc  from remoteDir
        if (null == remoteCache_){
            logger.warn(String.format("Remote cache missing or undefined in configuration. check value of '%s'", CONFIG_PES_REMOTECACHE));
            return;
        }
        if (null == localCache_){
            logger.warn(String.format("Local cache missing or undefined in configuration. check value of '%s'",CONFIG_PES_LOCALCACHE));
            return;
        }
        // get list of all the files in the remote cache directory.
        List<String> remoteFilesList = new ArrayList<>();
        File dirFolder = new File(remoteCache_ );
        logger.debug(String.format("Listing remote files"));
        File[] files = dirFolder.listFiles();
        if (null != files && files.length >0) {
            logger.debug(String.format("Listed remote files. count = " + files.length));
            for (final File f : files) {
                if (f.isFile()) {
                    remoteFilesList.add(f.getAbsolutePath());
                }
            }
        } else {
            logger.debug("remote directory was empty (files.length==0)");
        }

        if (remoteFilesList.size() > 0) {
            logger.info(String.format("PES Doc Transfer: Transferring  %s docs",remoteFilesList.size() ));
            int transferCount = 0;
            int deleteCount = 0;
            // now, for this batch of files process each one in turn, copying locally
            for (String fileName : remoteFilesList) {
                // get the local file name
                String localFileName = localCache_ + File.separator + fileName.substring(fileName.lastIndexOf(File.separator));
                logger.debug(String.format("PES Doc Transfer: (%d) Transferring  %s to %s",transferCount, fileName,localFileName ));
                FileChannel sourceChannel = null;
                FileChannel destChannel = null;
                boolean copied = false;
                try {
                    logger.debug("opening source channel");
                    sourceChannel = new FileInputStream(new File(fileName)).getChannel();
                    logger.debug("opening destination channel");
                    destChannel = new FileOutputStream(new File(localFileName)).getChannel();
                    logger.debug("performing transfer");
                    destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
                    logger.debug(String.format("PES Doc Transfer: (%d) Transferred  %s",transferCount, fileName ));
                    copied = true;
                } catch (IOException e) {
                    logger.warn(String.format("Failed to copy file %s to %s",fileName,localFileName));
                } finally {
                    try {
                        logger.debug("closing source channel");
                        sourceChannel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        logger.debug("closing destination stchannelream");
                        destChannel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (copied) {
                    boolean queued = true;
                    transferCount++;
                    // post on queue
                    /*
                    try {
                        // create an idoc with an id and a source and call toJson and put that on the queue
                        // ... processor stage will throw this away, create a new idoc from on disk file, and add filepatch as metadata
                        iDocument doc = new DEFDocument(localFileName,"file");
                        doc.addField("filepath",localFileName);

                        queue_.postTextMessage(doc.toJson());
                        queued = true;
                    } catch (DEFQueueException e) {
                        logger.warn(String.format("Failed to post message '%s' on queue '%s'", localFileName, queue_.getName()));
                    }

                     */
                    if (queued) {
                        deleteCount++;
                        // copied and queued - now we can delete to remote file

                        File deleteFile = new File(fileName);
                        try {
                            logger.debug("deleting remote file");
                            deleteFile.delete();
                            logger.debug("deleted remote file");
                        } catch (Exception e) {
                            logger.warn(String.format("Failed to delete message '%s'. %s", localFileName,e.getLocalizedMessage() ));
                            statusLogger_.warn(String.format("PES Doc Transfer: Failed to remove %d of %d docs",deleteCount,remoteFilesList.size() ));
                        }
                    }
                }


            }
            if (transferCount == remoteFilesList.size()){
                statusLogger_.info(String.format("PES Doc Transfer: Transferred  %s docs",remoteFilesList.size() ));
            } else {
                statusLogger_.warn(String.format("PES Doc Transfer: Failed to transfer %d of %d docs",transferCount,remoteFilesList.size() ));
            }
            if (deleteCount == remoteFilesList.size()){
                statusLogger_.info(String.format("PES Doc Transfer: Removed  %s remote docs",remoteFilesList.size() ));
            } else {
                statusLogger_.warn(String.format("PES Doc Transfer: Failed to remove %d of %d docs",deleteCount,remoteFilesList.size() ));
            }
        }
    }


}
