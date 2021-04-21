package com.dahu.qbe.utils;


        import java.io.File;
        import java.nio.ByteBuffer;
        import java.nio.file.Files;
        import java.nio.file.Paths;
        import java.util.ArrayList;
        import java.util.HashSet;
        import java.util.List;
        import java.util.Set;


/**
 * Created by :
 * Chris Bartlett, Dahu
 * chris@dahu.co.uk
 * on 13/02/2019
 * copyright Dahu Ltd 2019
 * <p>
 * Changed by :
 */

public class fileSharder {

    /**
     * Helper method to create a random folder structure to store  files in, deterministically
     * Given a filename, this method always returns the same folder path, in which it is safe to store the file
     * @param _rootFolder root folder in which the sub-folders are created
     * @param _filename name of a file to locate within this sharded file structure
     * @param _depth number of levels of folders to create
     * @return path to the folder in which a file should be stored, generating any sub-folders if required. If any folders along the path do not exist, it returns the original root folder
     */
    public static String createShardedDirectories(String _rootFolder, String _filename, int _depth){
        // given a prefix, filename and a sharded path depth, we check for the
        // presence of the directory structure, and create it if its not there.
        if (null != _filename ) {

            String checkPath = getCleanPrefix(_rootFolder).concat(File.separator);

            checkPath = checkPath.concat(getPathAsString(getSubDirectories(_filename,_depth)));

            if (!Files.exists(Paths.get(checkPath))){
                new File(checkPath).mkdirs();
            }
            if (checkPath.indexOf(_filename) > 0){
                return checkPath.substring(0,checkPath.indexOf(_filename)-1);
            } else {
                return checkPath;
            }
        }
        return _rootFolder;
    }


    public static String getShardedPath(String _prefix,String _filename,int _depth){
        // given a prefix, filename and a sharded path depth, we create a string of the
        // full path to this files sharded path.

        if (null != _filename ){

            String returnVal = getCleanPrefix(_prefix).concat(File.separator);

            // get the list of directories for this path and build a string

            returnVal = returnVal.concat(getPathAsString(getSubDirectories(_filename,_depth)));


            return returnVal.concat(_filename);
        } else {
            return null;
        }
    }


    public static List<String> getSubDirectories(String _filename, int _depth){

        // we want to generate a deterministic directory path for this file.
        // This means that we can always, given the filename, generate the same
        // path to the file.
        // we do this by considering the byte value of the string for N levels,
        // and at each level, we use the value MOD width to determine how many directories
        // to split into. So for example, if width = 5, at each level we create
        // up to five  directories. So with level set to 4, we have a total of 5^4 directories
        // or 625 directories
        //
        // this does mean that we need at least 'N' bytes from the filename - so if the
        // filename length is less than level, we have to pad with zero bytes.
        // This shouldn't be a big issue - simply means that short file names like 'aa'
        // in a four-level hierarchy will all be in the same directory and sub-directory at the
        // third and fourth level.

        // to give us as even a balance of content across all the sharded directories
        // we use the end of the string rather than the front of the string as its likely to be
        // statistically more diverse than the front of the string. We also check and remove any
        // file types first.


        final int WEIGHT = 5;
        final String[] DIRNAMES = {"SHA","SHB","SHC","SHD","SHE"};


        List<String> returnArray = null;
        if (null != _filename){
            String reverseFilename="";
            for(int i = _filename.length()-1;i>=0;i--){
                reverseFilename = reverseFilename.concat(_filename.substring(i,i+1));
            }

            returnArray = new ArrayList<String>();
            // check we have enough bytes to do the sums we need
            String pathToParse = reverseFilename;
            if (reverseFilename.length() < _depth){
                for (int i = reverseFilename.length(); i < _depth; i++ ) {
                    pathToParse = pathToParse.concat(" ");
                }
            }

            ByteBuffer buff = ByteBuffer.wrap(pathToParse.getBytes());

            for (int i = 0; i <_depth; i++){
                returnArray.add(DIRNAMES[buff.array()[i]% WEIGHT]);
            }
        }
        return returnArray;
    }

    private static String getCleanPrefix(String _prefix){
        // we might have been passed a prefix with or without a separator. Deal with it.
        String prefix = "";
        if (null != _prefix){
            if (_prefix.endsWith(File.separator)){
                prefix = _prefix.substring(0,_prefix.length()-1);
            } else {
                prefix = _prefix;
            }
        }
        return prefix;
    }

    private static String getPathAsString(List<String> _directories){
        // get the list of directories for this path and build a string
        String returnPath = "";

        for (String directory : _directories){
            returnPath = returnPath.concat(directory).concat(File.separator);
        }
        return returnPath;
    }

    public static Set<String> getHtmlFilesAtPath(String _rootPath, String _id, int _depth){
        Set<String> returnFiles = new HashSet<>();
        String dirName = fileSharder.getShardedPath(_rootPath,_id,_depth);
        File dir = new File(dirName);
        File[] filesList = dir.listFiles();
        if (null != filesList && filesList.length >0) {
            for (File file : filesList) {
                if (!file.isDirectory()) {
                    if (file.getName().toLowerCase().endsWith(".html")) {
                        //returnFiles.add(file.getAbsolutePath());
                        returnFiles.add(file.getPath());
                    }
                }
            }
        }
        return returnFiles;
    }

}