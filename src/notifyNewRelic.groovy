/**
 * UrbanCode Deploy Plug-in for NewRelic
 * 
 * This plugin posts deployment notifications to NewRelic
 * @version 1.1
 * @author cooperc
 */
import com.urbancode.air.AirPluginTool;
import com.urbancode.air.CommandHelper;

import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.StringRequestEntity

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

// properties
final def newrelic_url = props['newrelic_url'];
final def api_key = props['api_key']
final def app_name = props['app_name'];
final def description = props['description'];
final def user = props['user'];
final def version = props['version'];

def commandHelper = new CommandHelper(workDir);

// Setup path
try {
	def curPath = System.getenv("PATH");
	def pluginHome = new File(System.getenv("PLUGIN_HOME"))
	println "Setup of path using plugin home: " + pluginHome;
	def binDir = new File(pluginHome, "bin")
	def newPath = curPath+":"+binDir.absolutePath;
	commandHelper.addEnvironmentVariable("PATH", newPath);
} catch(Exception e){
	println "ERROR setting path: ${e.message}";
	System.exit(1);
}

// Construct request and post
try {
	
	if (!description) {
		description = app_name;
	}


    def content = "deployment[app_name]=${app_name}&deployment[description]=${description}&deployment[user]=${user}&deployment[revision]=${version}";

    def requestEntity = new StringRequestEntity(
            content,
            "application/x-www-form-urlencoded",
            "UTF-8"
    );
    def http = new HttpClient();
    def post = new PostMethod(newrelic_url);

    post.setRequestHeader("x-api-key", api_key);
    post.setRequestEntity(requestEntity);

    def status = http.executeMethod(post);
	
    if (status == 201){
		println "Success: ${status}";
		System.exit(0);
	} else {
		println "Failure: ${status}";
		System.exit(2);
	}		
} catch(Exception e){
	println "ERROR setting path: ${e.message}";
	System.exit(3);
}