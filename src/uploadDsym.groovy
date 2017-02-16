import groovyx.net.http.HTTPBuilder
import net.sf.json.JSON
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.entity.mime.content.StringBody
import static groovyx.net.http.Method.POST
import com.urbancode.air.AirPluginTool;

final def workDir = new File('.').canonicalFile;
final def props = new Properties();
final def inputPropsFile = new File(args[0]);
try {
    inputPropsStream = new FileInputStream(inputPropsFile);
    props.load(inputPropsStream);
}
catch (IOException e) {
    throw new RuntimeException(e);
}

final def applicationId = props['application_id']
final def productName = props['product_name'];
final def xcArchivePath = props['archive_path'];

def dumpDwafIDs(path) {
    def dwarfDump = "xcrun dwarfdump --uuid ${path}"
    def tr1 = "tr '[:upper:]' '[:lower:]'"
    def tr2 = "tr -d '-'"
    def awk = "awk '{print \$2}'"
    def xargs = "xargs"
    def sed =  "sed 's/ /,/g'"
    def cmd = "$dwarfDump | $tr1 | $tr2 | $awk | $xargs | $sed"

    def p = ['/bin/bash', '-c', cmd].execute()
    p.waitFor()
    def processOutput =  p.text
    return processOutput
}

def uploadDSYM(zipFilePath, dSYMUUIDS, productName, applicationid) {

    println "Uploading DSYM data for: ${productName}"

    def DSYM_UPLOAD_URL = "https://mobile-symbol-upload.newrelic.com"

    def file = new File(zipFilePath)

    http = new HTTPBuilder( DSYM_UPLOAD_URL )
    http.request (POST, JSON) { multipartRequest ->

        uri.path = '/symbol'

        requestContentType = 'multipart/form-data'

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

        builder.addPart( "dsym", new FileBody(file))
        builder.addPart( "buildId", new StringBody(dSYMUUIDS))
        builder.addPart( "appName", new StringBody(productName))

        headers.'X-APP-LICENSE-KEY' = applicationid

        multipartRequest.setEntity(builder.build())

        response.success = { resp ->
            println "SUCCESS: uplodated DSYM to New Relic ${resp.statusLine}"
        }

        response.failure = {  resp ->
            println "Failure: POST response statusline: ${resp.statusLine}"
        }
    }
}

def dSYMBase = xcArchivePath + "/dSYMs"
def dSYMSRC = dSYMBase + "/${productName}.app.dSYM"
def fullFileName = new File(dSYMSRC).name
def dWARFDSYMFileName = fullFileName.take(fullFileName.lastIndexOf('.'))
def dSYMTimestamp= System.currentTimeMillis() / 1000;
def dSYMArchivePath = "/tmp/${dWARFDSYMFileName}-${dSYMTimestamp}.zip"
def dSYMUUIDS = dumpDwafIDs(dSYMSRC)

/* Zip dSYM file. */
def ant = new AntBuilder()
ant.zip(destfile: dSYMArchivePath,
        basedir: dSYMBase,
        includes: "${productName}.app.dSYM/**/",
        excludes: "")

/* Upload zip file to NewRelic servers. */
uploadDSYM(dSYMArchivePath, dSYMUUIDS, productName, applicationId)

boolean fileSuccessfullyDeleted =  new File(dSYMArchivePath).delete()

if (!fileSuccessfullyDeleted) {
    println "Failure: Failed to delete temporary file: " + DSYM_ARCHIVE_PATH
}