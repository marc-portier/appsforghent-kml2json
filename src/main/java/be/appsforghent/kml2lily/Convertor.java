package be.appsforghent.kml2lily;

import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class Convertor extends DefaultHandler{

    
    private final File srcDir;
    private final File tgtDir;
    private final SAXParser parser;
    private final JsonFactory jsonFact = new JsonFactory();
    
    private Writer output;
    private JsonGenerator jsonGen;

    public Convertor(final File indir, final File outdir, final SAXParser sp) {
        assertDirectoryExists(indir);
        assertDirectoryExists(outdir);
        srcDir = indir;
        tgtDir = outdir;
        parser = sp;
    }

    public void assertDirectoryExists(final File dir) {
        if (!dir.exists() || !dir.isDirectory()) 
            throw new IllegalArgumentException("input directory not valid: " + dir.getAbsolutePath());
    }

    
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        
        final File indir = new File("../data/IN-kml");
        final File outdir = new File("../data/OUT-json");
        final SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        final SAXParser sp = spf.newSAXParser();

        Convertor me = new Convertor(indir, outdir, sp);
        me.workFiles();
    }

    private void workFiles() throws SAXException, IOException {
        System.out.println("will work on files in " + srcDir + " and produce output to " + tgtDir);
        final File[] files = srcDir.listFiles((FileFilter)new SuffixFileFilter(".kml"));
        
        for (File file : files) {
            acceptInputFile(file);
        }

        System.out.println(String.format("\n\n--\ncompleted processing of #%d files", files.length));
    }


    private void acceptInputFile(final File srcFile) throws SAXException, IOException {
        System.out.println(String.format("starting on file %s", srcFile.getAbsolutePath()));
        
        final File tgtFile = new File(tgtDir, FilenameUtils.getBaseName(srcFile.getName()) + ".json");
        System.out.println(String.format("replacing output in file %s", tgtFile.getAbsolutePath()));
        FileUtils.deleteQuietly(tgtFile);
        
        try {
            newOutput(tgtFile);
            parser.parse(srcFile, this);
        } finally {
            doneOutput();
        }
    }

    private void newOutput(File f) throws IOException {
        output = new FileWriter(f);
        jsonGen = jsonFact.createJsonGenerator(output);
        jsonGen.writeStartArray();
    }
    private void doneOutput() throws IOException {
        try {
            jsonGen.writeEndArray();
            jsonGen.close();
        } finally {
            IOUtils.closeQuietly(output);
            output = null;
            jsonGen = null;
        }
    }

    
    private static final String KML_NS = "http://earth.google.com/kml/2.2";
    private static final String FOLDER_ELM = "Folder";
    private static final String NAME_ELM = "name";
    private static final String PLACE_ELM = "Placemark";
    private static final String DESCR_ELM = "description";
    private static final String COORD_ELM = "coordinates";
    private static final String SIMPLEDATA_ELM = "SimpleData";
    private static final String NAME_ATTR = "name";

    private StringBuilder chars;
    private Stack<ContextNode> tree = new Stack<ContextNode>();
    protected String type; 

    private void newChars() {
        if (tree.size() > 0) // no need to grab anything that will not get reported
            chars = new StringBuilder();
    }

    private String doneChars() {
        if (chars == null) return null;
        
        final String c = chars.toString();
        chars = null;
        return c;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (chars != null) 
            chars.append(ch, start, length);
    }

    @Override
    public void startElement(String uri, String lName, String qName, Attributes atts)
            throws SAXException {
        if (!KML_NS.equals(uri)) return;
        
        if (SIMPLEDATA_ELM.equals(lName)) {
            newSimpleData(atts.getValue(NAME_ATTR));
        } else if (PLACE_ELM.equals(lName)) {
            newPlaceMark();
        } else if (DESCR_ELM.equals(lName)) {
            newChars();
        } else if (COORD_ELM.equals(lName)) {
            newChars();
        } else if (FOLDER_ELM.equals(lName)) {
            newFolder();
        } else if (NAME_ELM.equals(lName)) {
            newChars(); // grab the chars
        }
    }

    @Override
    public void endElement(String uri, String lName, String qName) throws SAXException {
        if (!KML_NS.equals(uri)) return;
        final String content = doneChars();

        if (SIMPLEDATA_ELM.equals(lName)) {
            doneSimpleData(content);
        } else if (PLACE_ELM.equals(lName)) {
            donePlaceMark();
        } else if (DESCR_ELM.equals(lName)) {
            tree.peek().setDescription(content);
        } else if (COORD_ELM.equals(lName)) {
            tree.peek().setCoordinate(longLat2LatLong(content));
        } else if (FOLDER_ELM.equals(lName)) {
            doneFolder();
        } else if (NAME_ELM.equals(lName)) {
            if (content != null)
                tree.peek().setName(content); 
        } 
    }



    class ContextNode {
        private String description;
        private String name;
        private String coordinate;
        private String address;
        private String nodeType;
        private String dataType;
        
        private String partStreet;
        private String partNumber;

        public ContextNode() {
            this(null);
        }
        public ContextNode(String nt) {
            setNodeType(nt);
            dataType = type;
        }
        private void setNodeType(String nt) {
            nodeType = nt;
        }
        String getNodeType() {
            return nodeType;
        }
        String getDataType() {
            return dataType;
        }
        void setName(String n) {
            name = n;
        }
        String getName() {
            return name;
        }
        void setDescription(String d) {
            description = d;
        }
        String getDescription() {
            return description;
        }
        void setCoordinate(String latLong) {
            coordinate = latLong;
        }
        String getCoordinate(){
            return coordinate;
        }
        String getAddress() {
            return address;
        }
        @Override
        public String toString() {
            return String.format("(%15s) %-40s @(%35s)// %s", dataType, '"' + name + '"', coordinate, address);
        }
        
        void addData(final String k, final String v) {
            if (k== null || k.length() == 0 || v ==null || v.length() == 0 ) return;
            
            if (k.equalsIgnoreCase("Naam") || k.equals("NAAM1") || k.equals("AFDELING") || k.equals("Code")
                    || k.equals("ORGANISATI") || k.equals("NAAMGZC")) {
                setName(v);
            } else if (k.equals("Ligging") || k.equals("LOCATIE") || k.equals("Plaats") || k.equals("ADRES")){
                address = v;
            } else if (k.equalsIgnoreCase("Straat")) {
                partStreet = v;
                address = partStreet + " " + partNumber;
            } else if (k.equals("NR") || k.equals("HUISNR") || k.equals("huisnummer")) {
                partNumber = v;
                address = partStreet + " " + partNumber;
            } // else ignore
        }
    }
    
    private void jsonGeneratePlaceMark(ContextNode cn) throws SAXException {
        try {
            jsonGen.writeStartObject();
            jsonGen.writeStringField("name", cn.getName());
            jsonGen.writeStringField("type", cn.getDataType());
            jsonGen.writeStringField("description", cn.getDescription());
            jsonGen.writeStringField("coordinate", cn.getCoordinate());
            jsonGen.writeStringField("address", cn.getAddress());
            jsonGen.writeEndObject();
        } catch (IOException e) {
            throw new SAXException("error while trying to dump json output for "+ cn , e);
        }
    }

    private ContextNode reusableFolderContextNode = new ContextNode(FOLDER_ELM){
        public void setName(String n) {
            type = n; // Folder tag is used to find the type of things to create.
        }
    };
    
    private void newFolder() {
        tree.push(reusableFolderContextNode);
    }

    private void doneFolder() {
        final ContextNode cn = tree.pop();
        if (cn != reusableFolderContextNode) 
            throw new IllegalStateException("wrong contextnode for folder !");
    }
    
    
   
    private void newPlaceMark() {
        tree.push(new ContextNode(PLACE_ELM)); 
    }

    private void donePlaceMark() throws SAXException {
        final ContextNode cn = tree.pop();
        if ( !(PLACE_ELM.equals(cn.getNodeType()) ) )
            throw new IllegalStateException("wrong contextnode for placemark !");
        
        jsonGeneratePlaceMark(cn);
    }
    

    private static final Pattern LONGLAT_REGEX = Pattern.compile("^(\\d+\\.\\d+),(\\d+\\.\\d+),\\d+ ?$");
    private String longLat2LatLong(String longLat) {
        final Matcher matcher = LONGLAT_REGEX.matcher(longLat);
        //ignore non matching and return null >> this erases all polygon shapes for the moment
        if (!matcher.matches()) return null;
        
        return matcher.group(2) + "," + matcher.group(1);
    }

    

    private void newSimpleData(final String name) {
        tree.push(new ContextNode(SIMPLEDATA_ELM){
            {
                setName(name);
            }
        });
        newChars();
    }
    private void doneSimpleData(final String value) {
        final ContextNode cn = tree.pop();
        if ( !(SIMPLEDATA_ELM.equals(cn.getNodeType()) ) )
            throw new IllegalStateException("wrong contextnode for simpledata !");
        tree.peek().addData(cn.getName(), value);
    }
}
