@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
import groovyx.net.http.*
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import groovy.json.JsonSlurper
import groovy.text.SimpleTemplateEngine

def http = new HTTPBuilder('https://api.github.com')
def latestReleaseDate = ""
def latestReleaseName = ""
def org = properties['org']
def repo = properties['repo']
def ln = System.getProperty("line.separator")

def slurper = new JsonSlurper()

// Get latest release date
http.request(GET){
    uri.path = '/repos/'+org+'/'+repo+'/releases'
    headers.'User-Agent' = 'Apache HTTPClient'
    response.success = { resp, json ->
        json.each {
            if(it.prerelease == false) {
                def releaseDate =  it.created_at
                if(releaseDate > latestReleaseDate) {
                    latestReleaseDate = releaseDate
                    latestReleaseName = it.tag_name
                }
            }
        }
    }
    response.failure = { resp ->
        println "Unexpected error: ${resp.statusLine.statusCode} : ${resp.statusLine.reasonPhrase}"
    }
}

// Get issue since latest release date
def outputFile = new File(properties['outputFile'])
outputFile.write("#Closed Issues "+ln+"from **"+latestReleaseName+"** until **current release**"+ ln)
outputFile << ln
outputFile << "|Author|Issue|Title|Created Date|Closed Date| $ln"
outputFile << "| ------ | ---- | ---- | ----------- | ---------- | $ln"
http.request( GET, JSON ) {
    uri.path = '/repos/'+org+'/'+repo+'/issues'
    uri.query = [ state:'closed', since: latestReleaseDate]
    headers.'User-Agent' = 'Apache HTTPClient'
    response.success = { resp, json ->
        json.each {
            def text = '| $login | [$issueNumber] | $issueTitle | $createdDate | $closedDate | $ln'
            def binding = ["login":it.user.login, "issueNumber":it.number, "issueTitle":it.title, "createdDate":it.created_at, "closedDate":it.closed_at, "ln":ln]
            def engine = new SimpleTemplateEngine()
            template = engine.createTemplate(text).make(binding)
            outputFile << template.toString()
        }
        outputFile << ln
        json.each {
            def text2 = '[$issueNumber]: $htmlURL $ln'
            def binding2 = ["issueNumber":it.number, "htmlURL":it.html_url, "ln":ln]
            def engine2 = new SimpleTemplateEngine()
            template2 = engine2.createTemplate(text2).make(binding2)
            outputFile << template2.toString()
        }
    }

    // handler for any failure status code:
    response.failure = { resp ->
        println "Unexpected error: ${resp.statusLine.statusCode} : ${resp.statusLine.reasonPhrase}"
    }
}